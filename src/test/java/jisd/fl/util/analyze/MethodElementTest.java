package jisd.fl.util.analyze;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.Expression;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.NoSuchFileException;

import static org.junit.jupiter.api.Assertions.*;

class MethodElementTest {
    @BeforeEach
    void initProperty() {
        PropertyLoader.setProperty("targetSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/src/main/java");
        PropertyLoader.setProperty("testSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/src/test/java");
        PropertyLoader.setProperty("testBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/build/classes/java/main");
        PropertyLoader.setProperty("targetBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/build/classes/java/test");

        TestUtil.compileForDebug(new MethodElementName("sample.MethodCallTest"));
    }

    @Test
    void extractArgumentOfMethodExpr() throws NoSuchFileException {
        CallableDeclaration cd
                = JavaParserUtil.getCallableDeclarationByName(new MethodElementName("sample.MethodCallTest#methodCall1()"));
        MethodElement md = new MethodElement(cd);
        Expression expr = md.extractArgumentOfMethodExpr(new MethodElementName("sample.MethodCall#methodCalling()"), 12, 0);
        assertEquals("2", expr.toString());
    }
}