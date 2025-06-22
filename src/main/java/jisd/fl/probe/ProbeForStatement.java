package jisd.fl.probe;

import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousReturnValue;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.analyze.*;

import java.util.*;

public class ProbeForStatement extends AbstractProbe{
    Set<SuspiciousVariable> probedValue;
    Set<String> targetClasses;
    SuspiciousExpression suspiciousExprTreeRoot = null;


    public ProbeForStatement(SuspiciousVariable target) {
        super(target);
        probedValue = new HashSet<>();
        targetClasses = StaticAnalyzer.getClassNames();
    }

    //調査結果の木構造のルートノードに対応するSuspExprを返す
    public SuspiciousExpression run(int sleepTime) {
        Deque<SuspiciousVariable> probingTargets = new ArrayDeque<>();
        List<SuspiciousVariable> investigatedTargets = new ArrayList<>();

        probingTargets.add(firstTarget);
        investigatedTargets.add(firstTarget);

        int depth = 0;
        while(!probingTargets.isEmpty()) {
            SuspiciousVariable target = probingTargets.removeFirst();
            printProbeExInfoHeader(target, depth);

            //search cause line
            SuspiciousExpression suspExpr = probing(sleepTime, target).orElseThrow(() -> new RuntimeException("Cause line is not found."));
;

            //include return line of callee method to cause lines
            List<SuspiciousExpression> causeExprs = searchSuspiciousReturns(suspExpr);

            //search next target
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

    @Override
    protected Optional<SuspiciousExpression> probing(int sleepTime, SuspiciousVariable suspVar) {
        Optional<SuspiciousExpression> result = super.probing(sleepTime, suspVar);
        int loop = 0;
        int LOOP_LIMIT = 5;
        while(result.isEmpty()) {
            loop++;
            System.err.println("[Probe For STATEMENT] Cannot get enough information.");
            System.err.println("[Probe For STATEMENT] Retry to collect information.");
            sleepTime += 2000;
            result = super.probing(sleepTime, suspVar);
            if (loop == LOOP_LIMIT) {
                System.err.println("[Probe For STATEMENT] Failed to collect information.");
                return Optional.empty();
            }
        }
        return result;
    }

    private List<SuspiciousExpression> searchSuspiciousReturns(SuspiciousExpression targetCauseExpr){
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
                suspExprQueue.addAll(returnsOfTarget);
            }
            result.add(target);
        }
        return result;
    }

    protected void printProbeExInfoHeader(SuspiciousVariable target, int depth){
        System.out.println("============================================================================================================");
        System.out.println(" Probe For STATEMENT      DEPTH: " + depth);
        System.out.println(target.toString());
        if(target.getParent() != null) {
            System.out.println(target.getParent().toString());
        }
        System.out.println("============================================================================================================");
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
