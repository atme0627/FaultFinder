package jisd.fl.util.analyze;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class TestMethodElement extends MethodElement {
    public TestMethodElement(CallableDeclaration cd) {
        super(cd);
    }
    //assert文のactualに代入されたStatementを探す
    //actualはassert文の2番目の引数にあると決めつける。
    public List<Expression> findAssertActualExpr(){
        return cd.findAll(MethodCallExpr.class)
                .stream()
                .filter(call -> call.getNameAsString().startsWith("assert"))
                .map(MethodCallExpr::getArguments)
                .filter(NodeList::isNonEmpty)
                .filter(args -> args.size() >= 2)
                .map(args -> args.get(1))
                .collect(Collectors.toList());
    }

    public List<Integer> findAssertLine(){
        return cd.findAll(MethodCallExpr.class)
                .stream()
                .filter(call -> call.getNameAsString().startsWith("assert"))
                .map(Node::getRange)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(r -> r.begin.line)
                .collect(Collectors.toList());
    }

    public static TestMethodElement getTestMethodElementByName(CodeElementName testCase) throws NoSuchFileException {
        return new TestMethodElement(JavaParserUtil.getCallableDeclarationByName(testCase));
    }
}
