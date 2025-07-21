package jisd.fl.sbfl;

import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.CoverageOfTarget;
import jisd.fl.coverage.Granularity;
import jisd.fl.probe.ProbeEx;
import jisd.fl.probe.info.ProbeExResult;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.report.ScoreUpdateReport; // new
import jisd.fl.util.analyze.CodeElementName;
import jisd.fl.util.analyze.MethodElementName;
import jisd.fl.util.analyze.StaticAnalyzer;

import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.function.ToDoubleBiFunction;

import static java.lang.Math.min;


public class FaultFinder {
    FLRanking flRanking;
    private Set<String> highlightMethods = new HashSet<>();

    //remove時に同じクラスの他のメソッドの疑惑値にかける定数
    protected double removeConst = 0.8;
    //susp時に同じクラスの他のメソッドの疑惑値にかける定数
    protected double suspConst = 1.2;
    //probe時に使用する定数
    protected double probeC1 = 0.2;
    protected double probeC2 = 0.1;
    protected double probeC3 = 0.1;

    //probeExの疑惑値計算に使用する変数
    protected double probeExLambda = 0.8;

    //probeExの疑惑値計算に使用する変数
    private int rankingSize = 20;
    final Granularity granularity;

    public FaultFinder(CoverageCollection covForTestSuite, Granularity granularity, Formula f) {
        this.granularity = granularity;
        flRanking = new FLRanking(granularity);
        calcSuspiciousness(covForTestSuite, granularity, f);
    }

    public void printRanking(){
        flRanking.printFLResults();
    }

    public void printRanking(int top){
        flRanking.printFLResults(top);
    }

    private void calcSuspiciousness(CoverageCollection covForTestSuite, Granularity granularity, Formula f){
        for(CoverageOfTarget coverageOfTarget : covForTestSuite.getCoverages()) {
            coverageOfTarget.getCoverage(granularity).forEach((element, status) -> {
                flRanking.setElement(element, status, f);
            });
        }
        flRanking.sort();
    }

    public FLRanking getFLResults(){
        return flRanking;
    }

    public void remove(int rank) {
        if(!validCheck(rank)) return;
        ScoreUpdateReport report = new ScoreUpdateReport("REMOVE");

        String targetMethod = flRanking.getElementNameAtPlace(rank);
        FLRankingElement target = flRanking.getElementAtPlace(rank).orElseThrow(
                () -> new RuntimeException("rank:" + rank + " is out of bounds. (max rank: " + flRanking.getSize() + ")"));
        String contextClass = targetMethod.split("#")[0];
        System.out.println("[  REMOVE  ] " + targetMethod);
        report.recordChange(target);
        flRanking.updateSuspiciousScore(targetMethod, 0);

        Set<String> contexts = null;
        try {
            MethodElementName context = new MethodElementName(contextClass);
            contexts = StaticAnalyzer.getMethodNames(context);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
        for(String contextMethod : contexts) {
            if(!flRanking.isElementExist(contextMethod)) continue;
            if(contextMethod.equals(targetMethod)) continue;
            double preScore = flRanking.getSuspicious(contextMethod);
            double newScore = preScore * removeConst;
            flRanking.updateSuspiciousScore(contextMethod, newScore);
        }

        report.print();

        flRanking.sort();
        flRanking.printFLResults(rankingSize);
    }

    public void susp(int rank) {
        if(!validCheck(rank)) return;
        ScoreUpdateReport report = new ScoreUpdateReport("SUSP");
        String targetMethod = flRanking.getElementNameAtPlace(rank);
        FLRankingElement target = flRanking.getElementAtPlace(rank).orElseThrow(
                () -> new RuntimeException("rank:" + rank + " is out of bounds. (max rank: " + flRanking.getSize() + ")"));
        System.out.println("[  SUSP  ] " + targetMethod);
        String contextClass = targetMethod.split("#")[0];
        report.recordChange(target);
        flRanking.updateSuspiciousScore(targetMethod, 0);

        Set<String> contexts = null;
        try {
            MethodElementName context = new MethodElementName(contextClass);
            contexts = StaticAnalyzer.getMethodNames(context);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
        for(String contextMethod : contexts) {
            if(!flRanking.isElementExist(contextMethod)) continue;
            if(contextMethod.equals(targetMethod)) continue;
            double preScore = flRanking.getSuspicious(contextMethod);
            double newScore = preScore + suspConst;
            flRanking.updateSuspiciousScore(contextMethod, newScore);
        }

        report.print();
        flRanking.sort();
        flRanking.printFLResults(rankingSize);
    }

    public void probeEx(FailedAssertInfo fai){
        probeEx(fai, 3000);
    }

    public void probeEx(FailedAssertInfo fai, int sleepTime){
        SuspiciousVariable suspiciousVariable = fai.getVariableInfo();
        System.out.println("[  PROBE EX  ] " + fai.getTestMethodName() + ": " + suspiciousVariable);
        ProbeEx prbEx = new ProbeEx(fai);
        ProbeExResult probeExResult = null;

        //TODO: SuspiciousStatementに変換
        //probeExResult = prbEx.run(sleepTime);
        //probeEx(probeExResult);
    }

    public void probeEx(ProbeExResult probeExResult){
        System.out.println("[  PROBE EX  ]");
        //set suspicious score
        ScoreUpdateReport report = new ScoreUpdateReport("PROBE EX");
        double preScore;
        for(String markingMethod : probeExResult.markingMethods()){
            if(!flRanking.isElementExist(markingMethod)) continue;
            preScore = flRanking.getSuspicious(markingMethod);

            double finalPreScore = preScore;
            ToDoubleBiFunction<Integer, Integer> probeExFunction
                    = (depth, countInLine) -> finalPreScore * (Math.pow(getProbeExLambda(), depth));

            double newScore = preScore + probeExResult.probeExSuspScore(markingMethod, probeExFunction);
            flRanking.updateSuspiciousScore(markingMethod, newScore);
        }

        report.print();
        flRanking.sort();
        flRanking.printFLResults(rankingSize);
    }

    private boolean validCheck(int rank){
        if(granularity != Granularity.METHOD){
            System.err.println("Only method granularity is supported.");
            return false;
        }
        if(!flRanking.rankValidCheck(rank)) return false;
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

    public double getProbeExLambda() {
        return probeExLambda;
    }

    public void setProbeExLambda(double probeExLambda) {
        this.probeExLambda = probeExLambda;
    }

    public int getRankingSize() {
        return rankingSize;
    }

    public void setRankingSize(int rankingSize) {
        this.rankingSize = rankingSize;
    }

    public Set<String> getHighlightMethods() {
        return highlightMethods;
    }

    public void setHighlightMethods(Set<String> highlightMethods) {
        this.highlightMethods = highlightMethods;
        this.flRanking.setHighlightMethods(highlightMethods);
    }

    
}
