package experiment.coverage;

import jisd.fl.coverage.CoverageAnalyzer;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.util.FileUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;

public class CoverageGenerator {
   String testClassName;
   String project;
   int bugId;
   boolean isGenerated = false;
   String rootDir = "src/main/resources/coverages";

   public CoverageGenerator(String testClassName,
                            String project,
                            int bugId){
      this.project = project;
      this.bugId = bugId;
      this.testClassName = testClassName;


      FileUtil.createDirectory(outputDir());

      if(FileUtil.isExist(outputDir(), "class_coverage.txt")){
          isGenerated = true;
      }
      else {
          FileUtil.createFile(outputDir(), "class_coverage.txt");
          FileUtil.createFile(outputDir(), "method_coverage.txt");
          FileUtil.createFile(outputDir(), "line_coverage.txt");
      }
   }


   public void generate(){
      CoverageAnalyzer ca = new CoverageAnalyzer(outputDir());
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
       if(isGenerated) return;
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
