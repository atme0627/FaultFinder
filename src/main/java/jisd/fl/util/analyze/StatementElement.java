package jisd.fl.util.analyze;

import com.github.javaparser.ast.stmt.Statement;

//JavaParserのStatementを保持し、ステートメント内の情報を得るためのメソッドを加えたクラス
public class StatementElement {
    private final Statement stmt;

    public StatementElement(Statement stmt){
        this.stmt = stmt;
    }
}
