package jisd.fl.probe.info;

import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.core.entity.MethodElementName;

import java.util.List;
import java.util.NoSuchElementException;

public class SuspiciousReturnValue extends SuspiciousExpression {
    public SuspiciousReturnValue(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue) {
        super(failedTest, locateMethod, locateLine, actualValue);
        Statement stmt = TmpJavaParserUtils.extractStmt(this.locateMethod, this.locateLine);
        this.expr = JavaParserSuspReturn.extractExprReturnValue(stmt);
    }

    public SuspiciousReturnValue(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue, String stmtString) {
        super(failedTest, locateMethod, locateLine, actualValue, stmtString);
        Statement stmt = TmpJavaParserUtils.extractStmt(this.locateMethod, this.locateLine);
        this.expr = JavaParserSuspReturn.extractExprReturnValue(stmt);
    }
    
    @Override
    public List<SuspiciousReturnValue> searchSuspiciousReturns() throws NoSuchElementException {
        return JDISuspReturn.searchSuspiciousReturns(this);
    }


    @Override
    public String toString(){
        return "[ SUSPICIOUS RETURN VALUE ] ( " + locateMethod + " line:" + locateLine + " ) " + stmtString();
    }

}
