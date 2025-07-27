package experiment.util;

import experiment.defect4j.Defects4jUtil;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestUtil;
import jisd.fl.util.analyze.MethodElementName;
import org.junit.jupiter.api.Test;

import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;

class SuspiciousVariableFinderTest {

    @Test
    void find1() throws NoSuchFileException {
        PropertyLoader.setTargetSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/main/java");
        PropertyLoader.setTestSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/test/java");

        MethodElementName testMethodName = new MethodElementName("org.sample.coverage.ConditionalTest#testXEqualY()");

        TestUtil.compileForDebug(testMethodName);
        SuspiciousVariableFinder finder
                = new SuspiciousVariableFinder(testMethodName);
        List<SuspiciousVariable> result = finder.find();
        result.forEach(vi -> System.out.println(vi.toString()));
    }

    @Test
    void findD4j1() throws NoSuchFileException {
        Defects4jUtil.changeTargetVersion("Lang", 1);
        Defects4jUtil.compileBuggySrc("Lang", 1);
        MethodElementName testMethodName = new MethodElementName("org.apache.commons.lang3.math.NumberUtilsTest#TestLang747()");

        SuspiciousVariableFinder finder
                = new SuspiciousVariableFinder(testMethodName);
        List<SuspiciousVariable> result = finder.find();
        result.forEach(vi -> System.out.println(vi.toString()));
    }

    @Test
    void findD4j3() throws NoSuchFileException {
        Defects4jUtil.changeTargetVersion("Lang", 3);
        Defects4jUtil.compileBuggySrc("Lang", 3);
        MethodElementName testMethodName = new MethodElementName("org.apache.commons.lang3.math.NumberUtilsTest#testStringCreateNumberEnsureNoPrecisionLoss()");

        SuspiciousVariableFinder finder
                = new SuspiciousVariableFinder(testMethodName);
        List<SuspiciousVariable> result = finder.find();
        result.forEach(vi -> System.out.println(vi.toString()));
    }

    @Test
    void findD4j4() throws NoSuchFileException {
        Defects4jUtil.changeTargetVersion("Lang", 4);
        Defects4jUtil.compileBuggySrc("Lang", 4);
        MethodElementName testMethodName = new MethodElementName("org.apache.commons.lang3.text.translate.LookupTranslatorTest#testLang882()");

        SuspiciousVariableFinder finder
                = new SuspiciousVariableFinder(testMethodName);
        List<SuspiciousVariable> result = finder.find();
        result.forEach(vi -> System.out.println(vi.toString()));
    }

    @Test
    void findD4j() throws NoSuchFileException {
        int bugId = 14;
        Defects4jUtil.changeTargetVersion("Lang", bugId);
        Defects4jUtil.compileBuggySrc("Lang", bugId);
        List<SuspiciousVariable> result = new ArrayList<>();
        List<MethodElementName> failedMethods = Defects4jUtil.getFailedTestMethods("Lang", bugId);
        for (MethodElementName me : failedMethods) {
            SuspiciousVariableFinder finder = new SuspiciousVariableFinder(me);
            result.addAll(finder.find());
        }

        result.forEach(vi -> System.out.println(vi.toString()));
    }
}