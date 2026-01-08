package jisd.fl.probe.info;

import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.core.entity.MethodElementName;

import java.util.*;

public class SuspiciousAssignment extends SuspiciousExpression {

    //左辺で値が代入されている変数の情報
    public final SuspiciousVariable assignTarget;

    public SuspiciousAssignment(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, SuspiciousVariable assignTarget, String stmtString) {
        super(failedTest, locateMethod, locateLine, assignTarget.getActualValue(), stmtString);
        Statement stmt = TmpJavaParserUtils.extractStmt(this.locateMethod, this.locateLine);
        this.expr = JavaParserSuspAssign.extractExprAssign(true, stmt);
        this.assignTarget = assignTarget;
    }

    @Override
    //TODO: 今はオブジェクトの違いを考慮していない
    public List<SuspiciousReturnValue> searchSuspiciousReturns() throws NoSuchElementException {
        return JDISuspAssign.searchSuspiciousReturns(this);
    }

    @Override
    public String toString() {
        return "[ SUSPICIOUS ASSIGNMENT ] ( " + locateMethod + " line:" + locateLine + " ) " + stmtString();
    }
}