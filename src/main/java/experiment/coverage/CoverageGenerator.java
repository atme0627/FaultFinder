package experiment.coverage;

import experiment.defect4j.Defects4jUtil;
import jisd.fl.coverage.CoverageAnalyzer;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.util.FileUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class CoverageGenerator {
   String testClassName;
   String project;
   int bugId;
   boolean isEmpty;
   String rootDir = "src/main/resources/coverages";

   public CoverageGenerator(String testClassName,
                            String project,
                            int bugId){
      this.project = project;
      this.bugId = bugId;
      this.testClassName = testClassName;

      //すぐ消す
       File f = new File(outputDir(), "class_coverage.txt");
       if(f.length() == 0) isEmpty = true;

      FileUtil.createDirectory(outputDir());

      FileUtil.initFile(outputDir(), "class_coverage.txt");
      FileUtil.initFile(outputDir(), "method_coverage.txt");
      FileUtil.initFile(outputDir(), "line_coverage.txt");
   }


   public void generate(){
       Set<String> failedTests = new HashSet<>(Defects4jUtil.getFailedTestMethods(project, bugId));
      CoverageAnalyzer ca = new CoverageAnalyzer(outputDir(), failedTests);
      CoverageCollection cc;
       try {
           cc = ca.analyzeAll(testClassName);
       } catch (IOException | InterruptedException e) {
           throw new RuntimeException(e);
       }
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
          )
       {
          cc.printCoverages(psClass, Granularity.CLASS);
          cc.printCoverages(psMethod, Granularity.METHOD);
          cc.printCoverages(psLine, Granularity.LINE);
       } catch (FileNotFoundException e) {
           throw new RuntimeException(e);
       }
   }
}
