package jisd.fl.usecase;

import jisd.fl.core.domain.NeighborSuspiciousVariablesSearcher;
import jisd.fl.core.domain.SuspiciousReturnsSearcher;
import jisd.fl.core.entity.susp.SuspiciousExprTreeNode;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.core.domain.CauseLineFinder;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.presenter.ProbeReporter;

import java.util.*;

public class Probe{
    private SuspiciousExprTreeNode suspiciousExprTreeRoot = new SuspiciousExprTreeNode(null);
    private final SuspiciousLocalVariable firstTarget;
    private final ProbeReporter reporter;
    private final CauseLineFinder causeLineFinder;
    protected final NeighborSuspiciousVariablesSearcher neighborSearcher;
    private final SuspiciousReturnsSearcher searcher;

    public Probe(SuspiciousLocalVariable target) {
        this.firstTarget = target;
        this.reporter = new ProbeReporter();
        this.causeLineFinder = new CauseLineFinder();
        this.neighborSearcher = new NeighborSuspiciousVariablesSearcher();
        this.searcher = new SuspiciousReturnsSearcher();
    }

    // SuspExpr単位で探索する。
    // 0. ユーザ由来のsuspVarから最初のSuspExprを特定する。
    // 1. suspExpr -- [suspVar] --> suspExpr(, suspArg) 探索済みのsuspVarは除外
    // 2. suspExpr -- [return]  --> suspExpr
    public SuspiciousExprTreeNode run(int sleepTime){
        Deque<SuspiciousExpression> exploringTargets = new ArrayDeque<>();
        Set<SuspiciousVariable> investigatedVariables = new HashSet<>();

        // 0. ユーザ由来のsuspVarから最初のSuspExprを特定する。
        investigatedVariables.add(firstTarget);
        reporter.reportSuspiciousVariable(firstTarget);
        SuspiciousExpression suspExpr = causeLineFinder.find(firstTarget).orElseThrow(() -> new RuntimeException("[Probe For STATEMENT] Cause line not found."));
        exploringTargets.add(suspExpr);
        this.suspiciousExprTreeRoot = new SuspiciousExprTreeNode(suspExpr);
        reporter.reportTargetSuspiciousExpression(suspExpr);

        //expr --> list<expr> の特定ループ
        while(!exploringTargets.isEmpty()){
            reporter.printHeader("", 150);
            SuspiciousExpression targetExpr = exploringTargets.removeFirst();
            reporter.reportTargetSuspiciousExpression(targetExpr);
            List<SuspiciousExpression> children = new ArrayList<>();

            // 1. suspExpr -- [suspVar] --> suspExpr(, suspArg) 探索済みのsuspVarは除外
            //SuspExprで直接使用されている(≒メソッドの引数でない)全ての変数
            List<SuspiciousVariable> neighborVariable = neighborSearcher.neighborSuspiciousVariables(false, targetExpr);
            for(SuspiciousVariable suspVar : neighborVariable){
                if(investigatedVariables.contains(suspVar)) continue;
                investigatedVariables.add(suspVar);
                Optional<SuspiciousExpression> suspExprOpt = causeLineFinder.find(suspVar);
                if(suspExprOpt.isEmpty()){
                    System.err.println("[Probe For STATEMENT] Cause line is not found.");
                    System.err.println("[Probe For STATEMENT] Skip probing");
                    continue;
                }
                children.add(suspExprOpt.get());
            }

            // 2. suspExpr -- [return]  --> suspExpr
            //Exprが直接呼び出しているmethodのreturnのみ返す。
            // int result = calc(a, b);
            //  -> return add(a, b); <= ここだけ返す。
            //    -> return a + b;       <= ここは[return add(a, b)]の探索で探す。
            List<SuspiciousExpression> result = searcher.search(targetExpr);
            children.addAll(result);

            //木構造に追加
            addTreeElement(targetExpr, children);
            //次の探索対象に追加
            exploringTargets.addAll(children);
        }

        return suspiciousExprTreeRoot;
    }


    protected void printProbeExInfoFooter(SuspiciousExpression suspExpr, List<SuspiciousVariable> nextTarget){
        System.out.println("------------------------------------------------------------------------------------------------------------");
        System.out.println(suspExpr);
        System.out.println(" [NEXT TARGET]");
        nextTarget.forEach(v -> System.out.println(v.toString()));
    }


    protected void addTreeElement(SuspiciousExpression parent, List<SuspiciousExpression> children){
        SuspiciousExprTreeNode parentNode = suspiciousExprTreeRoot.find(parent);
        if(parentNode == null) {
            suspiciousExprTreeRoot.print();
            throw new RuntimeException("Parent node is not found.");
        }
        parentNode.addChild(children);
        if(!children.isEmpty()) parentNode.printChildren();
    }
}
