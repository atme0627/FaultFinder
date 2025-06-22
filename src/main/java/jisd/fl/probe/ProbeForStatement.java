package jisd.fl.probe;

import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.probe.info.ProbeExResult;
import jisd.fl.probe.info.ProbeResult;
import jisd.fl.util.analyze.*;

import java.util.*;

public class ProbeForStatement extends AbstractProbe{
    Set<SuspiciousVariable> probedValue;
    Set<String> targetClasses;
    SuspiciousExpression suspiciousExprTreeRoot = null;


    public ProbeForStatement(FailedAssertInfo assertInfo) {
        super(assertInfo);
        probedValue = new HashSet<>();
        targetClasses = StaticAnalyzer.getClassNames();
    }

    //調査結果の木構造のルートノードに対応するSuspExprを返す
    public SuspiciousExpression run(int sleepTime) {
        SuspiciousVariable firstTarget = assertInfo.getVariableInfo();
        List<SuspiciousVariable> probingTargets = new ArrayList<>();
        List<SuspiciousVariable> nextTargets = new ArrayList<>();
        List<SuspiciousVariable> investigatedTargets = new ArrayList<>();

        probingTargets.add(firstTarget);
        investigatedTargets.add(firstTarget);

        int depth = 0;
        while(!probingTargets.isEmpty()) {
            for (SuspiciousVariable target : probingTargets) {
                printProbeExInfoHeader(target, depth);
                SuspiciousExpression suspExpr = probing(sleepTime, target).orElseThrow(() -> new RuntimeException("Cause line is not found."));

                nextTargets = suspExpr.neighborSuspiciousVariables(sleepTime, true);
                nextTargets.removeAll(investigatedTargets);

                addTreeElement(suspExpr, target);
                printProbeExInfoFooter(suspExpr, nextTargets);
            }

            probingTargets = nextTargets;
            nextTargets = new ArrayList<>();
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

    private void printProbeExInfoHeader(SuspiciousVariable target, int depth){
        System.out.println("============================================================================================================");
        System.out.println(" Probe For STATEMENT      DEPTH: " + depth);
        System.out.println(target.toString());
        System.out.println("============================================================================================================");
    }

    private void printProbeExInfoFooter(SuspiciousExpression suspExpr, List<SuspiciousVariable> nextTarget){
        System.out.println("------------------------------------------------------------------------------------------------------------");
        System.out.println(suspExpr);
        System.out.println(" [NEXT TARGET]");
        nextTarget.forEach(v -> System.out.println(v.toString()));
    }

    private void addTreeElement(SuspiciousExpression suspExpr, SuspiciousVariable targetSuspVar){
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
