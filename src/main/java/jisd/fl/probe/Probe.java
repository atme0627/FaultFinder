package jisd.fl.probe;

import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousReturnValue;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.probe.internal.CauseLineFinder;
import jisd.fl.probe.util.ProbeReporter;

import java.util.*;

public class Probe{
    SuspiciousExpression suspiciousExprTreeRoot = null;
    SuspiciousVariable firstTarget;
    ProbeReporter reporter;

    public Probe(SuspiciousVariable target) {
        this.firstTarget = target;
        this.reporter = new ProbeReporter();
    }

    //調査結果の木構造のルートノードに対応するSuspExprを返す
    public SuspiciousExpression run(int sleepTime) {
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
                List<SuspiciousVariable> neighbor = ce.neighborSuspiciousVariables(sleepTime, false);
                neighbor.removeAll(investigatedTargets);
                newTargets.addAll(neighbor);
            }
            newTargets.removeAll(investigatedTargets);

            investigatedTargets.addAll(newTargets);
            probingTargets.addAll(newTargets);

            addTreeElement(suspExpr, target);
            printProbeExInfoFooter(suspExpr, newTargets);
        }
        return suspiciousExprTreeRoot;
    }

    private List<SuspiciousExpression> collectInvokedReturnExpressions(SuspiciousExpression targetCauseExpr){
        List<SuspiciousExpression> result = new ArrayList<>();
        Deque<SuspiciousExpression> suspExprQueue = new ArrayDeque<>();
        suspExprQueue.add(targetCauseExpr);

        System.out.println("------------------------------------------------------------------------------------------------------------");
        while(!suspExprQueue.isEmpty()){
            SuspiciousExpression target = suspExprQueue.removeFirst();

            List<SuspiciousReturnValue> returnsOfTarget = target.searchSuspiciousReturns();
            if(!returnsOfTarget.isEmpty()) {
                System.out.println(" >>> search return line");
                System.out.println(" >>> target: " + target);
                System.out.println(" >>> ");
                System.out.println(" >>> return lines");
                for (SuspiciousReturnValue r : returnsOfTarget) {
                    System.out.println(" >>> " + r);
                }
                target.addChild(returnsOfTarget);
                suspExprQueue.addAll(returnsOfTarget);
            }
            result.add(target);
        }
        return result;
    }

    protected void printProbeExInfoFooter(SuspiciousExpression suspExpr, List<SuspiciousVariable> nextTarget){
        System.out.println("------------------------------------------------------------------------------------------------------------");
        System.out.println(suspExpr);
        System.out.println(" [NEXT TARGET]");
        nextTarget.forEach(v -> System.out.println(v.toString()));
    }

    protected void addTreeElement(SuspiciousExpression suspExpr, SuspiciousVariable targetSuspVar){
        if(suspiciousExprTreeRoot == null){
            suspiciousExprTreeRoot = suspExpr;
            return;
        }
        if(targetSuspVar.getParent() == null){
            System.out.println("Something is wrong");
        }
        targetSuspVar.getParent().addChild(suspExpr);
    }
}
