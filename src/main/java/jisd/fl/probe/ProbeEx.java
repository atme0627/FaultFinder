package jisd.fl.probe;

import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.analyze.StaticAnalyzer;

import java.util.*;

//値のStringを比較して一致かどうかを判定
//理想的には、"==" と同じ方法で判定したいが、型の問題で難しそう

/**
 * 卒論での実装
 */
public class ProbeEx extends ProbeForStatement {
    Set<SuspiciousVariable> probedValue;
    Set<String> targetClasses;
    SuspiciousExpression suspiciousExprTreeRoot = null;

    public ProbeEx(FailedAssertInfo assertInfo) {
        super(assertInfo.getVariableInfo());
        probedValue = new HashSet<>();
        targetClasses = StaticAnalyzer.getClassNames();
    }

    //調査結果の木構造のルートノードに対応するSuspExprを返す
    public SuspiciousExpression run(int sleepTime) {
        List<SuspiciousVariable> probingTargets = new ArrayList<>();
        List<SuspiciousVariable> nextTargets = new ArrayList<>();
        List<SuspiciousVariable> investigatedTargets = new ArrayList<>();

        probingTargets.add(firstTarget);
        investigatedTargets.add(firstTarget);

        int depth = 0;
        while(!probingTargets.isEmpty()) {
            for (SuspiciousVariable target : probingTargets) {
                printProbeExInfoHeader(target);
                SuspiciousExpression suspExpr = probing(sleepTime, target).orElseThrow(() -> new RuntimeException("Cause line is not found."));

                nextTargets = suspExpr.neighborSuspiciousVariables(sleepTime, true);
                nextTargets.removeAll(investigatedTargets);

                addTreeElement(suspExpr, target);
                printProbeExInfoFooter(suspExpr, nextTargets);
            }

            investigatedTargets.addAll(nextTargets);
            probingTargets = nextTargets;
            nextTargets = new ArrayList<>();
        }
        return suspiciousExprTreeRoot;
    }


    //TODO: メソッド単位の場合の卒論時点での実装に必要
//    //probeLine内で呼び出されたメソッド群を返す
//    public List<String> searchMarkingMethods(ProbeResult pr, String testMethod){
//        List<String> markingMethods = new ArrayList<>();
//        //引数が感染していた場合、呼び出しメソッドがマーキング対象
//        if(pr.isCausedByArgument()){
//            Pair<Integer, MethodElement> caller = pr.getCallerMethod();
//            if(caller != null) {
//                markingMethods.add(caller.getRight().fqmn());
//            }
//            return markingMethods;
//        }
//
//        //probe lineが所属するmethodをmarking methodに追加
//        if(targetClasses.contains(pr.getProbeMethodName().split("#")[0])) {
//            markingMethods.add(pr.getProbeMethodName());
//        }
//
//        Set<String> calledMethods = getCalleeMethods(pr.probeMethod(), pr.probeLine());
//        for(String called : calledMethods){
//            if(targetClasses.contains(called.split("#")[0])) markingMethods.add(called);
//        }
//        return markingMethods;
//    }
}
