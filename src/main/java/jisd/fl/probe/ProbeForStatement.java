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

    public ProbeForStatement(FailedAssertInfo assertInfo) {
        super(assertInfo);
        probedValue = new HashSet<>();
        targetClasses = StaticAnalyzer.getClassNames();
    }

    public ProbeExResult run(int sleepTime) {
        ProbeExResult result = new ProbeExResult();
        SuspiciousExpression root = null;

        SuspiciousVariable firstTarget = assertInfo.getVariableInfo();
        List<SuspiciousVariable> probingTargets = new ArrayList<>();
        List<SuspiciousVariable> nextTargets = new ArrayList<>();
        probingTargets.add(firstTarget);
        boolean isArgument = false;

        int depth = 0;
        while(!probingTargets.isEmpty()) {
            if(!isArgument) depth += 1;
            for (SuspiciousVariable target : probingTargets) {
                printProbeExInfoHeader(target, depth);

                SuspiciousExpression suspExpr = probing(sleepTime, target).orElseThrow(() -> new RuntimeException("Cause line is not found."));
                List<SuspiciousVariable> newTargets = suspExpr.neighborSuspiciousVariables(sleepTime, true);

                if(root == null) {
                    root = suspExpr;
                }
                else {
                }

                ProbeResult pr = ProbeResult.convertSuspExpr(suspExpr);
                result.addElement(pr.getProbeMethodName().split("#")[0], pr.probeLine(), 0, 1);
                printProbeExInfoFooter(pr, newTargets);
                nextTargets.addAll(newTargets);
                isArgument = pr.isCausedByArgument();
            }

            probingTargets = nextTargets;
            nextTargets = new ArrayList<>();
        }
        return result;
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

    private void printProbeExInfoFooter(ProbeResult pr, List<SuspiciousVariable> nextTarget){
        printProbeStatement(pr);
        System.out.println(" [NEXT TARGET]");
        nextTarget.forEach(v -> System.out.println(v.toString()));
    }
}
