package jisd.fl.probe;

import org.junit.jupiter.api.Test;

class ProbeExResultTest {

    @Test
    void loadJsonTest(){
        String project = "Math";
        int bugId = 1;

        String dir = "src/main/resources/probeExResult/Math/Math1_buggy/org.apache.commons.math3.fraction.BigFractionTest#testDigitLimitConstructor()_p2_probeEx.json" ;
        ProbeExResult result = ProbeExResult.loadJson(dir);
        result.print();
    }
}