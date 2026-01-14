package jisd.fl.core.domain.port;

import jisd.fl.core.entity.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousArgument;
import jisd.fl.core.entity.susp.SuspiciousAssignment;
import jisd.fl.core.entity.susp.SuspiciousReturnValue;
import jisd.fl.core.entity.susp.SuspiciousVariable;

public interface SuspiciousExpressionFactory {
    SuspiciousAssignment createAssignment(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, SuspiciousVariable assignTarget);
    SuspiciousReturnValue createReturnValue(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue);
    SuspiciousArgument createArgument(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue, MethodElementName calleeMethodName, int argIndex, int callCountAfterTargetInLine);
}
