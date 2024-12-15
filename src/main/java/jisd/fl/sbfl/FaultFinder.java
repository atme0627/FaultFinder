package jisd.fl.sbfl;

import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.probe.Probe;
import jisd.fl.probe.ProbeResult;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
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
        System.out.println("[remove] " + targetMethod);
        System.out.println("    " + targetMethod + ": " + sbflResult.getSuspicious(targetMethod) + " --> 0.0");
        sbflResult.setSuspicious(targetMethod, 0);

        Set<String> contexts = StaticAnalyzer.getMethodNames(contextClass, false);
        for(String contextMethod : contexts) {
            if(!sbflResult.isElementExist(contextMethod)) continue;
            if(contextMethod.equals(targetMethod)) continue;
            double preScore = sbflResult.getSuspicious(contextMethod);
            sbflResult.setSuspicious(contextMethod, preScore * removeConst);
            System.out.println("    " + contextMethod + ": " + preScore + " --> " + sbflResult.getSuspicious(contextMethod));
        }

        System.out.println();
        sbflResult.sort();
        sbflResult.printFLResults(10);
    }

    public void susp(int rank) throws IOException {
        if(!validCheck(rank)) return;
        String targetMethod = sbflResult.getMethodOfRank(rank);
        System.out.println("[susp] " + targetMethod);
        String contextClass = targetMethod.split("#")[0];
        System.out.println("    " + targetMethod + ": " + sbflResult.getSuspicious(targetMethod) + " --> 0.0");
        sbflResult.setSuspicious(targetMethod, 0);

        Set<String> contexts = StaticAnalyzer.getMethodNames(contextClass, false);
        for(String contextMethod : contexts) {
            if(!sbflResult.isElementExist(contextMethod)) continue;
            if(contextMethod.equals(targetMethod)) continue;
            double preScore = sbflResult.getSuspicious(contextMethod);
            sbflResult.setSuspicious(contextMethod, preScore + suspConst);
            System.out.println("    " + contextMethod + ": " + preScore + " --> " + sbflResult.getSuspicious(contextMethod));
        }

        System.out.println();
        sbflResult.sort();
        sbflResult.printFLResults(10);
    }

    public void probe(FailedAssertInfo fai){
        VariableInfo variableInfo = fai.getVariableInfo();
        System.out.println("[probe] " + fai.getTestMethodName() + ": " + variableInfo);
        Probe prb = new Probe(fai);
        ProbeResult probeResult = null;
        try {
             probeResult = prb.run(2000);
        } catch (RuntimeException e){
        //probeMethodsがメソッドを持っているかチェック
            throw new RuntimeException("FaultFinder#probe\n" +
                    "probeLine does not have methods.");
        }

        System.out.println("probe method: " + probeResult.getProbeMethod());

        //calc suspicious score
        double callerFactor = 0.0;
        double siblingFactor = 0.0;
        double preScore;
        String probeMethod = probeResult.getProbeMethod();
        String callerMethod = probeResult.getCallerMethod();
        callerFactor = probeC2 * sbflResult.getSuspicious(callerMethod);
        for(String siblingMethod : probeResult.getSiblingMethods()){
            if (probeMethod.equals(siblingMethod)) continue;
            siblingFactor += probeC2 * sbflResult.getSuspicious(siblingMethod);
        }

        //set suspicious score
        preScore = sbflResult.getSuspicious(probeMethod);
        sbflResult.setSuspicious(probeMethod, preScore * (1 + probeC1) + callerFactor + siblingFactor);
        System.out.println("    " + probeMethod + ": " + preScore + " --> " + sbflResult.getSuspicious(probeMethod));
        preScore = sbflResult.getSuspicious(callerMethod);
        sbflResult.setSuspicious(callerMethod, preScore + callerFactor + siblingFactor);
        System.out.println("    " + callerMethod + ": " + preScore + " --> " + sbflResult.getSuspicious(callerMethod));
        for(String siblingMethod : probeResult.getSiblingMethods()){
            if (probeMethod.equals(siblingMethod)) continue;
            preScore = sbflResult.getSuspicious(siblingMethod);
            sbflResult.setSuspicious(siblingMethod, preScore + callerFactor + siblingFactor);
            System.out.println("    " + siblingMethod + ": " + preScore + " --> " + sbflResult.getSuspicious(siblingMethod));
        }

        System.out.println();
        sbflResult.sort();
        sbflResult.printFLResults(10);
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
