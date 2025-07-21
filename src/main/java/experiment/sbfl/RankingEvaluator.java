package experiment.sbfl;

import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.probe.info.ProbeExResult;
import jisd.fl.sbfl.FaultFinder;
import jisd.fl.sbfl.Formula;
import jisd.fl.util.FileUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class RankingEvaluator {
    public FaultFinder ff;

    //remove時に同じクラスの他のメソッドの疑惑値にかける定数
    private double removeConst = 0.8;
    //susp時に同じクラスの他のメソッドの疑惑値に足す定数
    private double suspConst = 0.2;
    //probe時に使用する定数
    private double probeC1 = 0.2;
    private double probeC2 = 0.1;
    private double probeC3 = 0.1;

    //probeExの疑惑値計算に使用する変数
    private double probeExLambda = 0.5;

    //表示するランキングの長さ
    private int rankingSize = 20;

    public RankingEvaluator(CoverageCollection cov, Granularity g, Formula f){
        this(new FaultFinder(cov, g, f));
    }

    public RankingEvaluator(FaultFinder ff){
        this.ff = ff;
        initConst();
        setRankingSize(20);
    }

    public double calcMWE(String project, int bugId){
        Set<String> bugMethods = loadBugMethods(project, bugId);
        ff.setHighlightMethods(bugMethods);
        return calcMWE(bugMethods);
    }

    public double calcMWE(Set<String> bugMethods){
        System.out.println("[ BUG METHODS ]");
        for(String bugMethod : bugMethods){
            System.out.println(bugMethod);
        }
        System.out.println();

        //バグメソッドがrankingに含まれることを確認
        boolean isExist = false;
        for(String bugMethod : bugMethods){
            if(ff.getFLResults().isElementExist(bugMethod)) {
                isExist = true;
                break;
            }
        }
        if(!isExist) throw new NoSuchElementException("bug methods are not in Ranking.");

        Set<String> bugClasses = new HashSet<>();
        for(String bugMethod : bugMethods){
            bugClasses.add(bugMethod.split("#")[0]);
        }

        ff.getFLResults().printFLResults(20);

        //操作を行った回数
        int efforts = 0;
        while(true){
            //バグメソッドがtopにいる場合
            for(String bugMethod : bugMethods){
                if(ff.getFLResults().isTop(bugMethod)) {
                    double mwe = efforts + ff.getFLResults().getRankOfElement(bugMethod);
                    System.out.println("[ MWE ] " + mwe);
                    return mwe;
                }
            }

            efforts += 1;
            log("[EFFORT] " + efforts);
            String examinedMethod = ff.getFLResults().getElementNameAtPlace(1);
            boolean isNeighborOfBug = bugClasses.contains(examinedMethod.split("#")[0]);

            if(isNeighborOfBug){
                ff.susp(1);
            }
            else {
                ff.remove(1);
            }
        }
    }

    public void loadAndApplyProbeEx(String project, int bugId) throws NoSuchFileException {
        List<ProbeExResult> pers = loadProbeEx(project, bugId);

        for(ProbeExResult per : pers){
            ff.probeEx(per);
        }
    }

    public static List<ProbeExResult> loadProbeEx(String project, int bugId) throws NoSuchFileException {
        String dir = "src/main/resources/probeExResult/" + project + "/" + project + bugId + "_buggy";
        if(!FileUtil.isExist(dir)) throw new NoSuchFileException(dir);
        return loadProbeEx(dir);
    }

    public static List<ProbeExResult> loadProbeEx(String dir){
        List<ProbeExResult> pers = new ArrayList<>();
        Set<String> files = FileUtil.getFileNames(dir, "json");
        for(String f : files){
            System.out.println("[LOAD] " + f);
            pers.add(ProbeExResult.loadJson(dir + "/" + f));
        }
        return pers;
    }

    public static Set<String> loadBugMethods(String project, int bugId){
        String dir = "src/main/resources/dataset/" + project + "/Math" + bugId + "_buggy";
        return loadBugMethods(dir);
    }

    public static Set<String> loadBugMethods(String dir){
        Set<String> methods = new HashSet<>();
        Path p = Paths.get(dir + "/" + "modified_methods.txt");
        System.out.println("[LOAD] " + p);
        try (BufferedReader reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)){
            String line;
            while ((line = reader.readLine()) != null) {
                methods.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return methods;
    }

    private void initConst(){
        ff.setProbeC1(probeC1);
        ff.setProbeC2(probeC2);
        ff.setProbeC3(probeC3);
        ff.setSuspConst(suspConst);
        ff.setRemoveConst(removeConst);
        ff.setProbeExLambda(probeExLambda);
    }

    private void log(String message){
        System.out.println(" Evaluator Info >>> " + message);
    }

    public int getRankingSize() {
        return rankingSize;
    }

    public void setRankingSize(int rankingSize) {
        this.rankingSize = rankingSize;
        ff.setRankingSize(rankingSize);
    }
}
