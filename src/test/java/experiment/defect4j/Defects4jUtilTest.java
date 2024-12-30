package experiment.defect4j;

import jisd.fl.util.PropertyLoader;
import jisd.fl.util.StaticAnalyzer;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
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
        for(int bugId = 1; bugId <= 106; bugId++){
            System.out.println("[PROJECT] " + project + "   [ID] " + bugId);
            List<String> modified = Defects4jUtil.getModifiedMethod(project, bugId);
            for(String m : modified){
                System.out.println(m);
            }
            System.out.println();
            Thread.sleep(3000);
        }
    }

    @Test
    void getModifiedMethodTest(){
        String project = "Math";
        int bugId = 9;
        System.out.println("[PROJECT] " + project + "   [ID] " + bugId);
        List<String> modified = Defects4jUtil.getModifiedMethod(project, bugId);
        for(String m : modified){
            System.out.println(m);
        }
        System.out.println();
    }
}
