package jisd.fl.probe;

import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.probe.info.ProbeExResult;
import jisd.fl.probe.info.ProbeResult;
import jisd.fl.util.analyze.MethodElement;
import jisd.fl.util.analyze.StaticAnalyzer;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

//値のStringを比較して一致かどうかを判定
//理想的には、"==" と同じ方法で判定したいが、型の問題で難しそう
public class ProbeEx extends AbstractProbe {
    Set<SuspiciousVariable> probedValue;
    Set<String> targetClasses;

    public ProbeEx(FailedAssertInfo assertInfo) {
        super(assertInfo);
        probedValue = new HashSet<>();
        targetClasses = StaticAnalyzer.getClassNames();
    }

    public ProbeExResult run(int sleepTime) {
        ProbeExResult result = new ProbeExResult();
        SuspiciousVariable firstTarget = assertInfo.getVariableInfo();
        List<SuspiciousVariable> probingTargets = new ArrayList<>();
        List<SuspiciousVariable> nextTargets = new ArrayList<>();
        probingTargets.add(firstTarget);
        boolean isArgument = false;

        //初めのprobe対象変数がローカル変数の場合、変数が所属するmethodをdepth1でマーキングメソッドに含める。
        if(!firstTarget.isField()){
            printProbeInfoIfLocal(firstTarget);
            result.addElement(firstTarget.getLocateMethod(true), 1, 1);
        }

        int depth = 0;
        while(!probingTargets.isEmpty()) {
            if(!isArgument) depth += 1;
            if(depth > 10) break;
            for (SuspiciousVariable target : probingTargets) {
                printProbeExInfoHeader(target, depth);

                SuspiciousExpression suspExpr = probing(sleepTime, target).orElseThrow(() -> new RuntimeException("Cause line is not found."));
                List<SuspiciousVariable> newTargets = suspExpr.neighborSuspiciousVariables(sleepTime, true);

                ProbeResult pr = ProbeResult.convertSuspExpr(suspExpr);

                List<String> markingMethods = searchMarkingMethods(pr, assertInfo.getTestMethodName());
                printProbeExInfoFooter(pr, newTargets, markingMethods);

                result.addAll(markingMethods, depth);
                nextTargets.addAll(newTargets);
                isArgument = pr.isCausedByArgument();
            }

            probingTargets = nextTargets;
            nextTargets = new ArrayList<>();
        }
        return result;
    }

    protected Optional<SuspiciousExpression> probing(int sleepTime, SuspiciousVariable suspVar) {
        if(isProbed(suspVar)) return Optional.empty();
        addProbedValue(suspVar);
        Optional<SuspiciousExpression> result = super.probing(sleepTime, suspVar);
        int loop = 0;
        int LOOP_LIMIT = 5;
        while(result.isEmpty()) {
            loop++;
            System.err.println("[Probe] Cannot get enough information.");
            System.err.println("[Probe] Retry to collect information.");
            sleepTime += 2000;
            result = super.probing(sleepTime, suspVar);
            if (loop == LOOP_LIMIT) {
                System.err.println("[Probe] Failed to collect information.");
                return Optional.empty();
            }
        }
        return result;
    }

    //probeLine内で呼び出されたメソッド群を返す
    public List<String> searchMarkingMethods(ProbeResult pr, String testMethod){
        List<String> markingMethods = new ArrayList<>();
        //引数が感染していた場合、呼び出しメソッドがマーキング対象
        if(pr.isCausedByArgument()){
            Pair<Integer, MethodElement> caller = pr.getCallerMethod();
            if(caller != null) {
                markingMethods.add(caller.getRight().fqmn());
            }
            return markingMethods;
        }

        //probe lineが所属するmethodをmarking methodに追加
        if(targetClasses.contains(pr.getProbeMethodName().split("#")[0])) {
            markingMethods.add(pr.getProbeMethodName());
        }

        Set<String> calledMethods = getCalleeMethods(pr.probeMethod(), pr.probeLine());
        for(String called : calledMethods){
            if(targetClasses.contains(called.split("#")[0])) markingMethods.add(called);
        }
        return markingMethods;
    }

    private boolean isProbed(SuspiciousVariable vi){
        for(SuspiciousVariable e : probedValue){
            if(vi.equals(e)) return true;
        }
        return false;
    }

    private void addProbedValue(SuspiciousVariable vi){
        probedValue.add(vi);
    }

    private void printProbeInfoIfLocal(SuspiciousVariable firstTarget) {
        System.out.println("============================================================================================================");
        System.out.println(" Probe Ex     DEPTH: 1");
        System.out.println(" [MARKING METHODS] " + firstTarget.getLocateMethod(true));
    }

    private void printProbeExInfoHeader(SuspiciousVariable target, int depth){
        System.out.println("============================================================================================================");
        System.out.println(" Probe Ex     DEPTH: " + depth);
        System.out.println(target.toString());
        System.out.println("============================================================================================================");
    }

    private void printProbeExInfoFooter(ProbeResult pr, List<SuspiciousVariable> nextTarget, List<String> markingMethods){
        printProbeStatement(pr);
        System.out.println(" [MARKING METHODS]");
        for(String m : markingMethods){
            System.out.println(" " + m);
        }
        System.out.println(" [NEXT TARGET]");
        for(SuspiciousVariable vi : nextTarget){
            System.out.println(" [VARIABLE] " + vi.getVariableName(true, true) + "    [ACTUAL] " + vi.getActualValue());
        }
    }

}
