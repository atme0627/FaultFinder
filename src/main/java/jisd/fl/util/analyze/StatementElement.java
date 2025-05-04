package jisd.fl.util.analyze;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.stmt.Statement;

import java.util.Optional;

//JavaParserのStatementを保持し、ステートメント内の情報を得るためのメソッドを加えたクラス
public class StatementElement {
    private final Statement stmt;

    public StatementElement(Statement stmt){
        this.stmt = stmt;
    }

    public Statement statement(){
        return stmt;
    }

    //Statementが属するメソッドを返す
    public Optional<CallableDeclaration<?>> findEnclosingMethod() {
        Optional<Node> parent = stmt.getParentNode();
        while (parent.isPresent()) {
            Node node = parent.get();
            if (node instanceof CallableDeclaration<?>) {
                return Optional.of((CallableDeclaration<?>) node);
            }
            parent = node.getParentNode();
        }
        return Optional.empty();
    }



}
