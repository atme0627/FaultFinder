package experiment.coverage;

import com.fasterxml.jackson.core.JsonProcessingException;
import experiment.defect4j.Defects4jUtil;
import jisd.fl.coverage.CoverageAnalyzer;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.util.FileUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

public class CoverageGenerator {
   String testClassName;
   String project;
   int bugId;
   boolean isEmpty;
   static String rootDir = "src/main/resources/coverages";

   public CoverageGenerator(String testClassName,
                            String project,
                            int bugId){
      this.project = project;
      this.bugId = bugId;
      this.testClassName = testClassName;

      //すぐ消す
       File f = new File(outputDir(), "class_coverage.txt");
       if(f.length() == 0) isEmpty = true;

      FileUtil.initDirectory(outputDir());

      FileUtil.initFile(outputDir(), "class_coverage.txt");
      FileUtil.initFile(outputDir(), "method_coverage.txt");
      FileUtil.initFile(outputDir(), "line_coverage.txt");
      FileUtil.initFile(outputDir(), "CoverageCollection_object_data.json");
   }


   public void generate(){
       Set<String> failedTests = new HashSet<>(Defects4jUtil.getFailedTestMethods(project, bugId));
      CoverageAnalyzer ca = new CoverageAnalyzer(outputDir(), failedTests);
      CoverageCollection cc = ca.analyzeAll(testClassName);
       exportCoverage(cc);
   }

   private String outputDir(){
      return rootDir + "/" + project + "/" + project + bugId + "_buggy/" + testClassName;
   }

   private void exportCoverage(CoverageCollection cc){
       try (
          PrintStream psClass = new PrintStream(outputDir() + "/class_coverage.txt");
          PrintStream psMethod = new PrintStream(outputDir() + "/method_coverage.txt");
          PrintStream psLine = new PrintStream(outputDir() + "/line_coverage.txt");
          PrintStream psJson = new PrintStream(outputDir() + "/CoverageCollection_object_data.json");
          )
       {
          cc.printCoverages(psClass, Granularity.CLASS);
          cc.printCoverages(psMethod, Granularity.METHOD);
          cc.printCoverages(psLine, Granularity.LINE);
          cc.generateJson(psJson);
       } catch (FileNotFoundException | JsonProcessingException e) {
           throw new RuntimeException(e);
       }
   }

    public static CoverageCollection loadAll(String project, int bugId){
       String dir = rootDir + "/" + project + "/" + project + bugId + "_buggy";
        Set<String> testClasses = FileUtil.getFileNames(dir);
        CoverageCollection cc = null;

        for(String testClass : testClasses){
            if(testClass.startsWith(".")) continue;
            CoverageCollection ccTmp
                    = CoverageCollection.loadJson(dir + "/" + testClass + "/CoverageCollection_object_data.json");
            if(ccTmp.getCoverages().isEmpty()) {
                throw new RuntimeException("CoverageCollection is empty. [FILE] " + dir + "/" + testClass + "/CoverageCollection_object_data.json");
            }
            if(cc == null) {
                cc = ccTmp;
            }
            else {
                cc.mergeCoverage(ccTmp);
            }
        }
        return cc;
    }
}
