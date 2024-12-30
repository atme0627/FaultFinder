package experiment.defect4j;

import jisd.fl.util.FileUtil;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.StaticAnalyzer;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.NoSuchFileException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class Defects4jUtilTest {

    @Test
    void changeTargetVersionTest() {
        Defects4jUtil.changeTargetVersion("Math", 1);
        System.out.println(PropertyLoader.getProperty("targetSrcDir"));
        System.out.println(PropertyLoader.getProperty("junitConsoleLauncherPath"));
    }

    @Test
    void getAllModifiedMethodTest() throws InterruptedException {
        String project = "Math";
        int maxid = 106;
        for(int bugId = 1; bugId <=  maxid; bugId++){
            String d = "src/main/resources/dataset/Math/" + project + bugId + "_buggy/";
            FileUtil.createDirectory(d);
            FileUtil.createFile(d, "modified_methods.txt");
            System.out.println("[PROJECT] " + project + "   [ID] " + bugId);
            List<String> modified = Defects4jUtil.getModifiedMethod(project, bugId);



            try (FileWriter fw = new FileWriter(d + "/modified_methods.txt")){
                //メソッドが執せされていない場合(fieldの変更など)
                if(modified.isEmpty()){
                    String s = "Not modified.";
                    System.out.println(s);
                    fw.write(s);
                    fw.write("\n");
                }
                else {
                    for (String m : modified) {
                        System.out.println(m);
                        fw.write(m);
                        fw.write("\n");
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.out.println();
            Thread.sleep(1000);
        }
    }

    @Test
    void getModifiedMethodTest(){
        String project = "Math";
        int bugId = 87;
        System.out.println("[PROJECT] " + project + "   [ID] " + bugId);
        List<String> modified = Defects4jUtil.getModifiedMethod(project, bugId);
        for(String m : modified){
            System.out.println(m);
        }
        System.out.println();
    }
}
