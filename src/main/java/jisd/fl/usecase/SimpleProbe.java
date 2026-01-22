package jisd.fl.usecase;

import jisd.fl.core.entity.susp.SuspiciousExprTreeNode;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.core.domain.CauseLineFinder;
import jisd.fl.infra.javaparser.JavaParserClassNameExtractor;

import java.util.*;

//値のStringを比較して一致かどうかを判定
//理想的には、"==" と同じ方法で判定したいが、型の問題で難しそう

/**
 * 卒論での実装
 */
public class SimpleProbe extends Probe {
    Set<SuspiciousLocalVariable> probedValue = new HashSet<>();
    Set<String> targetClasses = JavaParserClassNameExtractor.getClassNames();
    SuspiciousExprTreeNode suspiciousExprTreeRoot = null;
    SuspiciousLocalVariable firstTarget;

    public SimpleProbe(SuspiciousLocalVariable target) {
        super(target);
    }

    //調査結果の木構造のルートノードに対応するSuspExprを返す
    public SuspiciousExprTreeNode run(int sleepTime) {
        List<SuspiciousLocalVariable> probingTargets = new ArrayList<>();
        List<SuspiciousLocalVariable> nextTargets = new ArrayList<>();
        List<SuspiciousLocalVariable> investigatedTargets = new ArrayList<>();

        probingTargets.add(firstTarget);
        investigatedTargets.add(firstTarget);

        int depth = 0;
        while(!probingTargets.isEmpty()) {
            for (SuspiciousLocalVariable target : probingTargets) {
                CauseLineFinder finder = new CauseLineFinder();
                SuspiciousExpression suspExpr = finder.find(target).orElseThrow(() -> new RuntimeException("Cause line is not found."));

                //SuspExprで観測できる全ての変数
                nextTargets = neighborSearcher.neighborSuspiciousVariables(true, suspExpr);
                nextTargets.removeAll(investigatedTargets);

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
