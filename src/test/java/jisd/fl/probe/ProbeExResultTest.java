package jisd.fl.probe;

import jisd.fl.util.FileUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProbeExResultTest {

    @Test
    void loadTest(){
        String project = "Math";
        int bugId = 1;

        String dir = "src/main/resources/probeExResult/Math/Math1_buggy";
        String fileName = "org.apache.commons.math3.fraction.BigFractionTest#testDigitLimitConstructor()_p2.probeEx";
        ProbeExResult result = ProbeExResult.load(dir, fileName);
        result.print();
    }

    @Test
    void loadAllTest(){
        String project = "Math";
        int bugId = 1;

        String dir = "src/main/resources/probeExResult/Math/Math1_buggy";
        List<ProbeExResult> results = loadAll(dir);
        for(ProbeExResult result : results) {
            result.print();
        }
    }

    public static List<ProbeExResult> loadAll(String dir){
        List<ProbeExResult> pers = new ArrayList<>();
        Set<String> files = FileUtil.getFileNames(dir, "probeEx");
        for(String f : files){
            pers.add(ProbeExResult.load(dir, f));
        }
        return pers;
    }
}