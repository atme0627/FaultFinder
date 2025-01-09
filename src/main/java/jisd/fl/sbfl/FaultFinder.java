package jisd.fl.sbfl;

import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.probe.Probe;
import jisd.fl.probe.ProbeEx;
import jisd.fl.probe.ProbeExResult;
import jisd.fl.probe.ProbeResult;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.util.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.*;
import java.util.function.ToDoubleBiFunction;


public class FaultFinder {
    SbflResult sbflResult;

    //remove時に同じクラスの他のメソッドの疑惑値にかける定数
    private double removeConst = 0.8;
    //susp時に同じクラスの他のメソッドの疑惑値に足す定数
    private double suspConst = 0.2;
    //probe時に使用する定数
    private double probeC1 = 0.2;
    private double probeC2 = 0.1;
    private double probeC3 = 0.1;

    //probeExの疑惑値計算に使用する変数
    private double probeExC = 2.0;
    private double probeExLambda = 0.8;

    private ToDoubleBiFunction<Integer, Integer> probeExfunction
            = (depth, countInLine) -> probeExC * (Math.pow(probeExLambda, depth - 1));

    final Granularity granularity;

    public FaultFinder(CoverageCollection covForTestSuite, Granularity granularity, Formula f) {
        this.granularity = granularity;
        sbflResult = new SbflResult();
        calcSuspiciousness(covForTestSuite, granularity, f);
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
        IblResult iblResult = new IblResult();

        String targetMethod = sbflResult.getMethodOfRank(rank);
        String contextClass = targetMethod.split("#")[0];
        System.out.println("[  REMOVE  ] " + targetMethod);
        iblResult.addElement(targetMethod, sbflResult.getSuspicious(targetMethod), 0.0);
        sbflResult.setSuspicious(targetMethod, 0);

        Set<String> contexts = StaticAnalyzer.getMethodNames(contextClass, false, false, true, true);
        for(String contextMethod : contexts) {
            if(!sbflResult.isElementExist(contextMethod)) continue;
            if(contextMethod.equals(targetMethod)) continue;
            double preScore = sbflResult.getSuspicious(contextMethod);
            sbflResult.setSuspicious(contextMethod, preScore * removeConst);
            iblResult.addElement(contextMethod, preScore, sbflResult.getSuspicious(contextMethod));
        }

        iblResult.print();

        sbflResult.sort();
        sbflResult.printFLResults();
    }

    public void susp(int rank) throws IOException {
        if(!validCheck(rank)) return;
        IblResult iblResult = new IblResult();
        String targetMethod = sbflResult.getMethodOfRank(rank);
        System.out.println("[  SUSP  ] " + targetMethod);
        String contextClass = targetMethod.split("#")[0];
        iblResult.addElement(targetMethod, sbflResult.getSuspicious(targetMethod), 0.0);
        sbflResult.setSuspicious(targetMethod, 0);

        Set<String> contexts = StaticAnalyzer.getMethodNames(contextClass, false, false, true, true);
        for(String contextMethod : contexts) {
            if(!sbflResult.isElementExist(contextMethod)) continue;
            if(contextMethod.equals(targetMethod)) continue;
            double preScore = sbflResult.getSuspicious(contextMethod);
            sbflResult.setSuspicious(contextMethod, preScore + suspConst);
            iblResult.addElement(contextMethod, preScore, sbflResult.getSuspicious(contextMethod));
        }

        iblResult.print();
        sbflResult.sort();
        sbflResult.printFLResults();
    }

    public void probe(FailedAssertInfo fai, int sleepTime){
        VariableInfo variableInfo = fai.getVariableInfo();
        IblResult iblResult = new IblResult();
        System.out.println("[  PROBE  ] " + fai.getTestMethodName() + ": " + variableInfo);
        Probe prb = new Probe(fai);
        ProbeResult probeResult = null;
        try {
             probeResult = prb.run(sleepTime);
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
        String callerMethod = probeResult.getCallerMethod().getRight();
        callerFactor = probeC2 * sbflResult.getSuspicious(callerMethod);
        for(String siblingMethod : probeResult.getSiblingMethods()){
            if (probeMethod.equals(siblingMethod)) continue;
            siblingFactor += probeC2 * sbflResult.getSuspicious(siblingMethod);
        }

        //set suspicious score
        preScore = sbflResult.getSuspicious(probeMethod);
        sbflResult.setSuspicious(probeMethod, preScore * (1 + probeC1) + callerFactor + siblingFactor);
        iblResult.addElement(probeMethod, preScore, sbflResult.getSuspicious(probeMethod));

        preScore = sbflResult.getSuspicious(callerMethod);
        sbflResult.setSuspicious(callerMethod, preScore + callerFactor + siblingFactor);
        iblResult.addElement(callerMethod, preScore, sbflResult.getSuspicious(callerMethod));

        for(String siblingMethod : probeResult.getSiblingMethods()){
            if (probeMethod.equals(siblingMethod)) continue;
            preScore = sbflResult.getSuspicious(siblingMethod);
            sbflResult.setSuspicious(siblingMethod, preScore + callerFactor + siblingFactor);
            iblResult.addElement(siblingMethod, preScore, sbflResult.getSuspicious(siblingMethod));
        }

        iblResult.print();
        sbflResult.sort();
        sbflResult.printFLResults();
    }

    public void probeEx(FailedAssertInfo fai, int sleepTime){
        VariableInfo variableInfo = fai.getVariableInfo();
        IblResult iblResult = new IblResult();
        System.out.println("[  PROBE EX  ] " + fai.getTestMethodName() + ": " + variableInfo);
        ProbeEx prbEx = new ProbeEx(fai);
        ProbeExResult probeExResult = null;

        probeExResult = prbEx.run(sleepTime);

        //set suspicious score
        double preScore;
        for(String markingMethod : probeExResult.markingMethods()){
            preScore = sbflResult.getSuspicious(markingMethod);
            sbflResult.setSuspicious(markingMethod, preScore * probeExResult.probeExSuspWeight(markingMethod, probeExfunction));
            iblResult.addElement(markingMethod, preScore, sbflResult.getSuspicious(markingMethod));
        }

        iblResult.print();
        sbflResult.sort();
        sbflResult.printFLResults();
    }

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

    static class IblResult {
        List<Element> results = new ArrayList<>();

        public void addElement(String method, Double preScore, Double newScore){
            results.add(new Element(method, preScore,newScore));
        }

        public void print(){
            Pair<Integer, Integer> l = maxLengthOfName();
            int classLength = l.getLeft();
            int methodLength = l.getRight();

            String header =
                    "| " + StringUtils.repeat(' ', classLength - "CLASS NAME".length()) + "CLASS NAME"
                    + " | " + StringUtils.repeat(' ', methodLength - "METHOD NAME".length()) + "METHOD NAME"
                    + " |   OLD  ->   NEW  |";
            String partition = StringUtils.repeat("=", header.length());

            System.out.println(partition);
            System.out.println(header);
            System.out.println(partition);
            for(Element e : results){
                System.out.println("| " + StringUtils.leftPad(e.method.split("#")[0], classLength)
                + " | " + StringUtils.leftPad(e.method.split("#")[1], methodLength)
                + " | " + String.format("%.4f", e.oldScore) + " -> " + String.format("%.4f", e.newScore) + " |");
            }
            System.out.println(partition);
            System.out.println();
        }

        private Pair<Integer, Integer> maxLengthOfName() {
            int classLength = 0;
            int methodLength = 0;

            for (Element e : results) {
                classLength = Math.max(classLength, e.method.split("#")[0].length());
                methodLength = Math.max(methodLength, e.method.split("#")[1].length());
            }

            return Pair.of(classLength, methodLength);
        }

        static class Element {
            String method;
            Double oldScore;
            Double newScore;

             Element(String method, Double oldScore, Double newScore){
                this.method = method;
                this.oldScore = oldScore;
                this.newScore = newScore;
            }
        }
    }
}
