package jisd.fl.probe;

import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.FileUtil;
import jisd.fl.util.JsonExporter;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.analyze.MethodElementName;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;

class ProbeForStatementTest {
    @Nested
    class CalcTest {
        @BeforeEach
        void initProperty() {
            PropertyLoader.setTargetSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/main/java");
            PropertyLoader.setTestSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/test/java");
        }

        @Test
        void runTest() {
            SuspiciousVariable target = new SuspiciousVariable(
                    new MethodElementName("org.sample.CalcTest#methodCall1()"),
                    "org.sample.CalcTest#methodCall1()",
                    "actual",
                    "11",
                    true,
                    false
            );

            ProbeForStatement pfs = new ProbeForStatement(target);
            SuspiciousExpression treeRoot = pfs.run(2000);

            File output = new File("/Users/ezaki/IdeaProjects/MyFaultFinder/src/test/resources/json/SuspiciousExpression/CalcTest.json");
            JsonExporter.exportSuspExpr(treeRoot, output);
        }
    }

    @Nested
    class ConditionalTest {
        @BeforeEach
        void initProperty() {
            PropertyLoader.setTargetSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/main/java");
            PropertyLoader.setTestSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/test/java");
        }

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

            ProbeForStatement pfs = new ProbeForStatement(target);
            SuspiciousExpression treeRoot = pfs.run(2000);

            File output = new File("/Users/ezaki/IdeaProjects/MyFaultFinder/src/test/resources/json/SuspiciousExpression/ConditionalTest.json");
            JsonExporter.exportSuspExpr(treeRoot, output);
        }

        @Test
        void loadFromJson() throws IOException {
            File input = new File("/Users/ezaki/IdeaProjects/MyFaultFinder/src/test/resources/json/SuspiciousExpression/ConditionalTest.json");
            SuspiciousExpression loadedFromJson = SuspiciousExpression.loadFromJson(input);
            File output = new File("/Users/ezaki/IdeaProjects/MyFaultFinder/src/test/resources/json/SuspiciousExpression/ConditionalTest2.json");
            JsonExporter.exportSuspExpr(loadedFromJson, output);
            assertTrue(FileUtils.contentEquals(input, output), "File contents should match");

        }
    }
}