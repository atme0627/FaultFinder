package jisd.fl.probe;

import jisd.fl.probe.info.SuspiciousExprTreeNode;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousReturnValue;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.probe.internal.CauseLineFinder;
import jisd.fl.probe.util.ProbeReporter;

import java.util.*;

public class Probe{
    SuspiciousExprTreeNode suspiciousExprTreeRoot = null;
    SuspiciousVariable firstTarget;
    ProbeReporter reporter;

    public Probe(SuspiciousVariable target) {
        this.firstTarget = target;
        this.reporter = new ProbeReporter();
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

            //search cause line
            CauseLineFinder finder = new CauseLineFinder(target);
            Optional<SuspiciousExpression> suspExprOpt = finder.find();
            if(suspExprOpt.isEmpty()){
                System.err.println("[Probe For STATEMENT] Cause line is not found.");
                System.err.println("[Probe For STATEMENT] Skip probing");
                continue;
            }
            SuspiciousExpression suspExpr = suspExprOpt.get();
;           reporter.reportCauseExpression(suspExpr);
            //include return line of callee method to cause lines
            List<SuspiciousExpression> causeExprs = collectInvokedReturnExpressions(suspExpr);

            //search next target
            System.out.println(" >>> search next target");
            List<SuspiciousVariable> newTargets = new ArrayList<>();
            for (SuspiciousExpression ce : causeExprs) {
                List<SuspiciousVariable> neighbor = ce.neighborSuspiciousVariables(sleepTime, false, ce);
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
        suspExprQueue.add(targetCauseExpr);

        SuspiciousExprTreeNode targetNode = suspiciousExprTreeRoot.find(targetCauseExpr);
        if(targetNode == null) throw new RuntimeException("Target node is not found.");

        while(!suspExprQueue.isEmpty()){
            SuspiciousExpression target = suspExprQueue.removeFirst();

            List<SuspiciousReturnValue> returnsOfTarget = target.searchSuspiciousReturns();
            if(!returnsOfTarget.isEmpty()) {
                targetNode.addChild(returnsOfTarget);
                suspExprQueue.addAll(returnsOfTarget);
            }
            result.add(target);
        }
        reporter.reportInvokedReturnExpression(targetNode);
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
