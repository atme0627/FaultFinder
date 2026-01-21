package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.MethodElementName;

import java.util.List;

public class SuspiciousReturnValue extends SuspiciousExpression {

    public SuspiciousReturnValue(
            MethodElementName failedTest,
            MethodElementName locateMethod,
            int locateLine,
            String actualValue,
            String stmtString,
            boolean hasMethodCalling,
            List<String> directNeighborVariableNames,
            List<String> indirectNeighborVariableNames
    ) {
        super(failedTest, locateMethod, locateLine, actualValue, stmtString, hasMethodCalling, directNeighborVariableNames, indirectNeighborVariableNames);
    }


    @Override
    public String toString(){
        return "[ SUSPICIOUS RETURN VALUE ] ( " + locateMethod + " line:" + locateLine + " ) " + stmtString();
    }

}
