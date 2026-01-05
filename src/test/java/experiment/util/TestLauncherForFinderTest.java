package experiment.util;

import experiment.defect4j.Defects4jUtil;
import experiment.util.internal.finder.TestLauncherForFinder;
import jisd.fl.core.entity.MethodElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class TestLauncherForFinderTest {

    @BeforeEach
    void initProperty() {
        String project = "Lang";
        int bugId = 58;
        Defects4jUtil.changeTargetVersion(project, bugId);
        Defects4jUtil.compileBuggySrc(project, bugId);
    }

    @Test
    void launchTest() {
        MethodElementName testMethodName = new MethodElementName("org.apache.commons.lang.math.NumberUtilsTest#testLang300()");
        TestLauncherForFinder tl = new TestLauncherForFinder(testMethodName);
        Optional<TestLauncherForFinder.TestFailureInfo> tmp = tl.runTestAndGetFailureLine();
        tmp.ifPresent(System.out::println);
        if(tmp.isEmpty()) System.out.println("Not found");
    }




}