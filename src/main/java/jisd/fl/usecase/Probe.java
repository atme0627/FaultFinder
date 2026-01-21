package jisd.fl.usecase;

import jisd.fl.core.domain.NeighborSuspiciousVariablesSearcher;
import jisd.fl.core.domain.SuspiciousReturnsSearcher;
import jisd.fl.core.entity.susp.SuspiciousExprTreeNode;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.susp.SuspiciousReturnValue;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.core.domain.CauseLineFinder;
import jisd.fl.presenter.ProbeReporter;

import java.util.*;

public class Probe{
    SuspiciousExprTreeNode suspiciousExprTreeRoot = new SuspiciousExprTreeNode(null);
    SuspiciousVariable firstTarget;
    ProbeReporter reporter;
    protected final NeighborSuspiciousVariablesSearcher neighborSearcher;

    public Probe(SuspiciousVariable target) {
        this.firstTarget = target;
        this.reporter = new ProbeReporter();
        this.neighborSearcher = new NeighborSuspiciousVariablesSearcher();
    }

    //調査結果の木構造のルートノードに対応するSuspExprを返す
    public SuspiciousExprTreeNode run(int sleepTime) {
        Deque<SuspiciousVariable> probingTargets = new ArrayDeque<>();
        List<SuspiciousVariable> investigatedTargets = new ArrayList<>();

        probingTargets.add(firstTarget);
        investigatedTargets.add(firstTarget);

        while(!probingTargets.isEmpty()) {
            SuspiciousVariable target = probingTargets.removeLast();
            reporter.reportProbeTarget(target);
            List<SuspiciousExpression> causeExprs = new ArrayList<>();

            //search cause line
            CauseLineFinder finder = new CauseLineFinder(target);
            Optional<SuspiciousExpression> suspExprOpt = finder.find();
            if(suspExprOpt.isEmpty()){
                System.err.println("[Probe For STATEMENT] Cause line is not found.");
                System.err.println("[Probe For STATEMENT] Skip probing");
                continue;
            }
            SuspiciousExpression suspExpr = suspExprOpt.get();
            causeExprs.add(suspExpr);
            addTreeElement(suspExpr, target);
;           reporter.reportCauseExpression(suspExpr);
            //include return line of callee method to cause lines

            causeExprs.addAll(collectInvokedReturnExpressions(suspExpr));

            //search next target
            System.out.println(" >>> search next target");
            List<SuspiciousVariable> newTargets = new ArrayList<>();
            for (SuspiciousExpression ce : causeExprs) {
                //SuspExprで観測できる全ての変数
                List<SuspiciousVariable> neighbor = neighborSearcher.neighborSuspiciousVariables(false, ce);
                neighbor.forEach(sv -> sv.setParent(ce));
                neighbor.removeAll(investigatedTargets);
                newTargets.addAll(neighbor);
            }
            newTargets.removeAll(investigatedTargets);

            investigatedTargets.addAll(newTargets);
            probingTargets.addAll(newTargets);
            printProbeExInfoFooter(suspExpr, newTargets);
        }
        return suspiciousExprTreeRoot;
    }

    private List<SuspiciousExpression> collectInvokedReturnExpressions(SuspiciousExpression targetCauseExpr){
        List<SuspiciousExpression> result = new ArrayList<>();
        Deque<SuspiciousExpression> suspExprQueue = new ArrayDeque<>();
        SuspiciousReturnsSearcher searcher = new SuspiciousReturnsSearcher();

        suspExprQueue.add(targetCauseExpr);
        while(!suspExprQueue.isEmpty()){
            SuspiciousExpression target = suspExprQueue.removeFirst();
            SuspiciousExprTreeNode targetNode = suspiciousExprTreeRoot.find(target);
            if(targetNode == null) throw new RuntimeException("Target node is not found.");

            List<SuspiciousReturnValue> returnsOfTarget = searcher.search(target);
            if(!returnsOfTarget.isEmpty()) {
                targetNode.addChild(returnsOfTarget);
                suspExprQueue.addAll(returnsOfTarget);
            }
            result.addAll(returnsOfTarget);
        }
        reporter.reportInvokedReturnExpression(suspiciousExprTreeRoot.find(targetCauseExpr));
        return result;
    }

    protected void printProbeExInfoFooter(SuspiciousExpression suspExpr, List<SuspiciousVariable> nextTarget){
        System.out.println("------------------------------------------------------------------------------------------------------------");
        System.out.println(suspExpr);
        System.out.println(" [NEXT TARGET]");
        nextTarget.forEach(v -> System.out.println(v.toString()));
    }

    protected void addTreeElement(SuspiciousExpression suspExpr, SuspiciousVariable targetSuspVar){
        if(suspiciousExprTreeRoot.suspExpr == null){
            suspiciousExprTreeRoot = new SuspiciousExprTreeNode(suspExpr);
            return;
        }
        if(targetSuspVar.getParent() == null){
            System.out.println("Something is wrong");
        }
        SuspiciousExprTreeNode parent = suspiciousExprTreeRoot.find(targetSuspVar.getParent());
        if(parent == null) {
            suspiciousExprTreeRoot.print();
            System.out.println("===================================================================================");
            System.out.println(targetSuspVar.getParent());
            System.out.println("===================================================================================");
            throw new RuntimeException("Parent node is not found.");
        }
        parent.addChild(suspExpr);
    }
}
