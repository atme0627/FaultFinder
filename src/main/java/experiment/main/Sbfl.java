//package experiment.main;
//
//import com.fasterxml.jackson.core.util.DefaultIndenter;
//import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import experiment.coverage.CoverageGenerator;
//import experiment.defect4j.Defects4jUtil;
//import experiment.sbfl.RankingEvaluator;
//import jisd.fl.coverage.CoverageCollection;
//import jisd.fl.coverage.Granularity;
//import jisd.fl.sbfl.Formula;
//
//import java.io.FileWriter;
//import java.io.IOException;
//import java.io.PrintStream;
//import java.io.PrintWriter;
//import java.nio.file.NoSuchFileException;
//import java.util.NoSuchElementException;
//import java.util.Set;
//
//public class Sbfl{
//    static String outputCsv = "/Users/ezaki/Desktop/research/experiment/MWE_lambda=0.5.txt";
//
//    public static void main(String[] args){
//
//        try (FileWriter fw = new FileWriter(outputCsv, true);
//             PrintWriter pw = new PrintWriter(fw)) {
//            pw.println("bugId, mweBefore, mweAfter, sbfl");
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        String project = "Math";
//        int numOfBugs = 106;
//        for (int bugId = 1; bugId <= numOfBugs; bugId++) {
//            Defects4jUtil.changeTargetVersion(project, bugId);
//
//            CoverageCollection cov = CoverageGenerator.loadAll(project, bugId);
//            RankingEvaluator re = new RankingEvaluator(cov, Granularity.METHOD, Formula.OCHIAI);
//            double sbfl = 100000000;
//            for(String bugMethod : re.loadBugMethods(project, bugId)) {
//                sbfl = Math.min(sbfl, re.ff.getFLResults().getRankOfElement(bugMethod));
//            }
//            double mweBefore;
//            try {
//                mweBefore = re.calcMWE(project, bugId);
//            }
//            catch (NoSuchElementException e){
//                continue;
//            }
//
//            re = new RankingEvaluator(cov, Granularity.METHOD, Formula.OCHIAI);
//            try {
//                re.loadAndApplyProbeEx(project, bugId);
//            } catch (NoSuchFileException e) {
//                continue;
//            }
//            double mweAfter = re.calcMWE(project, bugId);
//
//            try (FileWriter fw = new FileWriter(outputCsv, true);
//                 PrintWriter pw = new PrintWriter(fw)) {
//                pw.println(bugId + ", " + mweBefore + ", " + mweAfter + ", " +  sbfl);
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//            System.out.println("[  MWE  ] " + mweBefore + "--> " + mweAfter);
//        }
//    }
//}
