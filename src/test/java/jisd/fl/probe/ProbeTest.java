package jisd.fl.probe;

import com.fasterxml.jackson.core.type.TypeReference;
import experiment.defect4j.Defects4jUtil;
import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.JsonIO;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.analyze.MethodElementName;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

class ProbeTest {
    @BeforeEach
    void initProperty() {
        PropertyLoader.setTargetSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/main/java");
        PropertyLoader.setTestSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/test/java");
    }

    @Nested
    class CalcTest {
        @Test
        void runTest() {
            SuspiciousVariable target = new SuspiciousVariable(
                    new MethodElementName("org.sample.CalcTest#methodCall1()"),
                    "org.sample.CalcTest#methodCall1()",
                    "actual",
                    "4",
                    true,
                    false
            );

            Probe pfs = new Probe(target);
            SuspiciousExpression treeRoot = pfs.run(2000);

            File output = new File("/Users/ezaki/IdeaProjects/MyFaultFinder/src/test/resources/json/SuspiciousExpression/CalcTest.json");
            JsonIO.export(treeRoot, output);
        }
    }

    @Nested
    class ConditionalTest {
        @Test
        void runTest() {
            SuspiciousVariable target = new SuspiciousVariable(
                    new MethodElementName("org.sample.coverage.ConditionalTest#testXEqualY()"),
                    "org.sample.coverage.ConditionalTest#testXEqualY()",
                    "result",
                    "2",
                    true,
                    false
            );

            Probe pfs = new Probe(target);
            SuspiciousExpression treeRoot = pfs.run(2000);

            File output = new File("/Users/ezaki/IdeaProjects/MyFaultFinder/src/test/resources/json/SuspiciousExpression/ConditionalTest.json");
            JsonIO.export(treeRoot, output);
        }

        @Test
        void loadFromJson() throws IOException {
            File input = new File("/Users/ezaki/IdeaProjects/MyFaultFinder/src/test/resources/json/SuspiciousExpression/ConditionalTest.json");
            SuspiciousExpression loadedFromJson = SuspiciousExpression.loadFromJson(input);
            File output = new File("/Users/ezaki/IdeaProjects/MyFaultFinder/src/test/resources/json/SuspiciousExpression/ConditionalTest2.json");
            JsonIO.export(loadedFromJson, output);
            assertTrue(FileUtils.contentEquals(input, output), "File contents should match");

        }
    }

    @Nested
    class LoopTest {
        @Test
        void runTest() {
            SuspiciousVariable target = new SuspiciousVariable(
                    new MethodElementName("org.sample.coverage.LoopTest#testCase1_forAndWhile_prodGreaterThanSum()"),
                    "org.sample.coverage.LoopTest#testCase1_forAndWhile_prodGreaterThanSum()",
                    "result",
                    "8",
                    true,
                    false
            );

            Probe pfs = new Probe(target);
            SuspiciousExpression treeRoot = pfs.run(2000);

            File output = new File("/Users/ezaki/IdeaProjects/MyFaultFinder/src/test/resources/json/SuspiciousExpression/LoopTest1.json");
            JsonIO.export(treeRoot, output);
        }
    }

    @Test
    void runFromJsonTest() throws IOException {
        Dotenv dotenv = Dotenv.load();
        Path expDir = Paths.get(dotenv.get("EXP_20250726_DIR"));

        String project = "Lang";
        int bugId = 8;

        File inputFile = expDir.resolve(project + "/" + project.toLowerCase() + "_" + bugId + "b/probeTargets.json").toFile();
        Defects4jUtil.changeTargetVersion(project, bugId);
        Defects4jUtil.compileBuggySrc(project, bugId);

        List<?> probeTargets = JsonIO.importFromJson(inputFile, new TypeReference<List<SuspiciousVariable>>() {});

        System.out.println("Finding target: [PROJECT] " + project + "  [BUG ID] " + bugId);

        for(int i = 0; i < probeTargets.size(); i++) {
            SuspiciousVariable target = (SuspiciousVariable) probeTargets.get(i);
            System.out.println("failedTest " + target.getFailedTest());

            File outputFile = expDir.resolve(project + "/" + project.toLowerCase() + "_" + bugId + "b/probe/" +
                    target.getFailedTest() + "_" + target.getSimpleVariableName()).toFile();
            Path path = outputFile.toPath();
            Files.deleteIfExists(path);
            Files.createFile(path);

            Probe prb = new Probe(target);
            SuspiciousExpression result = prb.run(2000);
            JsonIO.export(result, outputFile);
        }
    }
}