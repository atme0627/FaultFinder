package jisd.fl.sbfl;

import jisd.debug.Debugger;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.probe.AssertExtractor;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.NewProbe;
import jisd.fl.util.*;

import java.io.IOException;
import java.util.*;


public class FaultFinder {
    String testSrcDir = PropertyLoader.getProperty("d4jTestSrcDir");
    String testBinDir = PropertyLoader.getProperty("d4jTestBinDir");
    String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");
    MethodCallGraph callGraph;
    SbflResult sbflResult;

    //remove時に同じクラスの他のメソッドの疑惑値にかける定数
    private double removeConst = 0.8;
    //susp時に同じクラスの他のメソッドの疑惑値に足す定数
    private double suspConst = 0.2;
    //probe時に使用する定数
    private double probeC1 = 0.2;
    private double probeC2 = 0.1;
    private double probeC3 = 0.1;

    final Granularity granularity;

    public FaultFinder(CoverageCollection covForTestSuite, Granularity granularity, Formula f) throws IOException {
        this.granularity = granularity;
        sbflResult = new SbflResult();
        calcSuspiciousness(covForTestSuite, granularity, f);
        callGraph = StaticAnalyzer.getMethodCallGraph(targetSrcDir);
    }

    private void calcSuspiciousness(CoverageCollection covForTestSuite, Granularity granularity, Formula f){
        Set<String> targetClassNames = covForTestSuite.getTargetClassNames();
        for(String targetClassName : targetClassNames){
            Map<String, SbflStatus> covData = covForTestSuite.getCoverageOfTarget(targetClassName, granularity);
            calcSuspiciousnessOfTarget(targetClassName, covData, f, granularity);
        }
        sbflResult.sort();
    }

    private void calcSuspiciousnessOfTarget(String targetClassName, Map<String, SbflStatus> covData, Formula f, Granularity granularity){
        covData.forEach((element, status) ->{
            if(granularity == Granularity.LINE) element = targetClassName + " --- " + element;
            sbflResult.setElement(element, status, f);
        });
    }

    public SbflResult getFLResults(){
        return sbflResult;
    }

    public void remove(int rank) throws IOException {
        if(!validCheck(rank)) return;
        String targetMethod = sbflResult.getMethodOfRank(rank);
        String contextClass = targetMethod.split("#")[0];
        sbflResult.setSuspicious(targetMethod, 0);

        Set<String> contexts = StaticAnalyzer.getMethodNames(targetSrcDir, contextClass);
        for(String contextMethod : contexts) {
            double preScore = sbflResult.getSuspicious(contextMethod);
            sbflResult.setSuspicious(contextMethod, preScore * removeConst);
        }

        System.out.println("remove: " + targetMethod);
        sbflResult.printFLResults(10);
    }

    public void susp(int rank) throws IOException {
        if(!validCheck(rank)) return;
        String targetMethod = sbflResult.getMethodOfRank(rank);
        String contextClass = targetMethod.split("#")[0];
        sbflResult.setSuspicious(targetMethod, 0);

        Set<String> contexts = StaticAnalyzer.getMethodNames(targetSrcDir, contextClass);
        for(String contextMethod : contexts) {
            double preScore = sbflResult.getSuspicious(contextMethod);
            sbflResult.setSuspicious(contextMethod, preScore + suspConst);
        }

        System.out.println("susp: " + targetMethod);
        sbflResult.printFLResults(10);
    }

    public void probe(String targetTestClass,
                      String targetTestMethod,
                      int failedAssertLine,
                      int nthArg,
                      String actualValue){

        AssertExtractor ae = new AssertExtractor(testSrcDir, testBinDir);
        FailedAssertInfo fai = ae.getAssertByLineNum(targetTestClass, targetTestMethod, failedAssertLine, nthArg, actualValue);
        Debugger dbg = TestUtil.testDebuggerFactory(targetTestClass, targetTestMethod);
        NewProbe prb = new NewProbe(fai);
//        List<String> probeMethods = prb.run(2000);
//
//        //probeMethodsがメソッドを持っているかチェック
//        if(probeMethods.get(0).startsWith("#")){
//            throw new RuntimeException("FaultFinder#probe\n" +
//                    "probeLine does not have methods. probeLine: " + probeMethods.get(0).substring(1));
//        }
//
//        //calc suspicious score
//        for(String probeMethod : probeMethods){
//
//        }
    }

    //TODO: あるメソッドを呼び出したメソッドとそのメソッドが呼び出したメソッドをコールスタックから取得する


    private boolean validCheck(int rank){
        if(granularity != Granularity.METHOD){
            System.err.println("Only method granularity is supported.");
            return false;
        }
        if(!sbflResult.rankValidCheck(rank)) return false;
        return true;
    }

    public double getRemoveConst() {
        return removeConst;
    }

    public void setRemoveConst(double removeConst) {
        this.removeConst = removeConst;
    }

    public double getSuspConst() {
        return suspConst;
    }

    public void setSuspConst(double suspConst) {
        this.suspConst = suspConst;
    }

    public double getProbeC1() {
        return probeC1;
    }

    public void setProbeC1(double probeC1) {
        this.probeC1 = probeC1;
    }

    public double getProbeC2() {
        return probeC2;
    }

    public void setProbeC2(double probeC2) {
        this.probeC2 = probeC2;
    }

    public double getProbeC3() {
        return probeC3;
    }

    public void setProbeC3(double probeC3) {
        this.probeC3 = probeC3;
    }
}
