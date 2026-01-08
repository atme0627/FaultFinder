package jisd.fl.infra.javaparser;

import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.core.domain.port.SuspiciousExpressionFactory;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.probe.info.*;

import java.util.List;
import java.util.NoSuchElementException;

public class JavaParserSuspiciousExpressionFactory implements SuspiciousExpressionFactory {

    @Override
    public SuspiciousAssignment createAssignment(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, SuspiciousVariable assignTarget) {
        Statement stmt = TmpJavaParserUtils.extractStmt(locateMethod, locateLine);
        return new SuspiciousAssignment(failedTest, locateMethod, locateLine, assignTarget, stmt.toString());
    }

    @Override
    public SuspiciousReturnValue createReturnValue(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue) {
        Statement stmt = TmpJavaParserUtils.extractStmt(locateMethod, locateLine);
        return new SuspiciousReturnValue(failedTest, locateMethod, locateLine, actualValue, stmt.toString());
    }

    @Override
    public SuspiciousArgument createArgument(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue, MethodElementName calleeMethodName, int argIndex, int callCountAfterTargetInLine) {
        Statement stmt = TmpJavaParserUtils.extractStmt(locateMethod, locateLine);
        return new SuspiciousArgument(failedTest, locateMethod, locateLine, actualValue, calleeMethodName, argIndex, callCountAfterTargetInLine, stmt.toString());
    }
}
