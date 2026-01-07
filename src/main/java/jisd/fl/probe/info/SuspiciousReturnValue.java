package jisd.fl.probe.info;

import jisd.fl.core.entity.MethodElementName;

import java.util.List;
import java.util.NoSuchElementException;

public class SuspiciousReturnValue extends SuspiciousExpression {
    public SuspiciousReturnValue(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue) {
        super(failedTest, locateMethod, locateLine, actualValue);
        this.expr = JavaParserSuspReturn.extractExprReturnValue(stmt);
    }
    
    @Override
    public List<SuspiciousReturnValue> searchSuspiciousReturns() throws NoSuchElementException {
        return JDISuspReturn.searchSuspiciousReturns(this);
    }


    @Override
    public String toString(){
        return "[ SUSPICIOUS RETURN VALUE ]\n" + "    " + locateMethod.methodSignature + "{\n       ...\n" + super.toString() + "\n       ...\n    }";
    }

}
