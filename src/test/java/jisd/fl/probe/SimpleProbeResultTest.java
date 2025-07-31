package jisd.fl.probe;

import jisd.fl.probe.info.SimpleProbeResult;
import org.junit.jupiter.api.Test;

class SimpleProbeResultTest {

    @Test
    void loadJsonTest(){
        String project = "Math";
        int bugId = 1;

        String dir = "src/main/resources/probeExResult/Math/Math1_buggy/org.apache.commons.math3.fraction.BigFractionTest#testDigitLimitConstructor()_p2_probeEx.json" ;
        SimpleProbeResult result = SimpleProbeResult.loadJson(dir);
        result.print();
    }
}