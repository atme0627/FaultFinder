package jisd.fl.probe;

import experiment.defect4j.Defects4jUtil;
import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.mapper.SuspiciousVariableMapper;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.probe.info.TmpJsonUtils;
import jisd.fl.util.JsonIO;
import jisd.fl.util.PropertyLoader;
import jisd.fl.core.entity.MethodElementName;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

class ProbeTest {
    Path jsonOutPutDir;
    @BeforeEach
    void initProperty() {
        Dotenv dotenv = Dotenv.load();
        Path testProjectDir = Paths.get(dotenv.get("TEST_PROJECT_DIR"));
        PropertyLoader.setTargetSrcDir(testProjectDir.resolve("src/main/java").toString());
        PropertyLoader.setTestSrcDir(testProjectDir.resolve("src/test/java").toString());
        PropertyLoader.setTargetBinDir(testProjectDir.resolve("build/classes/java/main").toString());
        PropertyLoader.setTestBinDir(testProjectDir.resolve("build/classes/java/test").toString());

        Path currentDirectoryPath = FileSystems.getDefault().getPath("");
        jsonOutPutDir = currentDirectoryPath.resolve("src/test/resources/json/SuspiciousExpression");
    }

    @Nested
    class MinimumTest {
        /*
        最小限、Junitのテストの実行と変数の観測ができてることを確認する。
         */
        @Test
        void CheckRunTestAndWatchVariable() {
            SuspiciousVariable target = new SuspiciousVariable(
                    new MethodElementName("org.sample.MinimumTest#CheckRunTestAndWatchVariable()"),
                    "org.sample.MinimumTest#CheckRunTestAndWatchVariable()",
                    "x",
                    "6",
                    true,
                    false
            );

            Probe pfs = new Probe(target);
            SuspiciousExpression treeRoot = pfs.run(2000);
        }
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

            //File output = jsonOutPutDir.resolve("CalcTest.json").toFile();
            //JsonIO.export(treeRoot, output);
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

            File output = jsonOutPutDir.resolve("ConditionalTest.json").toFile();
            JsonIO.export(treeRoot, output);
        }

        @Test
        void loadFromJson() throws IOException {
            File input = jsonOutPutDir.resolve("ConditionalTest.json").toFile();
            SuspiciousExpression loadedFromJson = TmpJsonUtils.loadFromJson(input);
            File output = jsonOutPutDir.resolve("ConditionalTest2.json").toFile();
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

            File output = jsonOutPutDir.resolve("LoopTest1.json").toFile();
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

        String jsonString = Files.readString(inputFile.toPath());
        List<SuspiciousVariable> probeTargets = SuspiciousVariableMapper.fromJsonArray(jsonString);

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