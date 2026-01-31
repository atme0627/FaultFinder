package jisd.fl.core.domain.port;

import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.*;

public interface SuspiciousExpressionFactory {
    SuspiciousAssignment createAssignment(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, SuspiciousVariable assignTarget);
    SuspiciousReturnValue createReturnValue(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue);
    SuspiciousArgument createArgument(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue, MethodElementName invokeMethodName, int argIndex, int callCountAfterTargetInLine);
}
