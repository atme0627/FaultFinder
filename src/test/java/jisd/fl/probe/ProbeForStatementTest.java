package jisd.fl.probe;

import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.FileUtil;
import jisd.fl.util.JsonExporter;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.analyze.MethodElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;

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
    }
}