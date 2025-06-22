package jisd.fl.probe;

import jisd.fl.probe.info.SuspiciousExpression;
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
            SuspiciousExpression suspExpr = probing(sleepTime, target).orElseThrow(() -> new RuntimeException("Cause line is not found."));

            List<SuspiciousVariable> newTargets = suspExpr.neighborSuspiciousVariables(sleepTime, true);
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

    protected void printProbeExInfoHeader(SuspiciousVariable target, int depth){
        System.out.println("============================================================================================================");
        System.out.println(" Probe For STATEMENT      DEPTH: " + depth);
        System.out.println(target.toString());
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
