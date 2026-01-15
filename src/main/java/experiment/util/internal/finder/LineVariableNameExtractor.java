package experiment.util.internal.finder;

import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.infra.javaparser.JavaParserUtils;
import jisd.fl.core.entity.MethodElementName;

import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

public class LineVariableNameExtractor {
    public Statement extractStmtInFailureLine(int failureLine, MethodElementName locateMethod){
        try {
            return JavaParserUtils.getStatementByLine(locateMethod, failureLine).orElseThrow();
        } catch (NoSuchFileException e) {
            throw new RuntimeException("Class [" + locateMethod + "] is not found.");
        } catch (NoSuchElementException e){
            throw new RuntimeException("Cannot extract Statement from [" + locateMethod + ":" + failureLine + "].");
        }
    }

    public List<String> extractVariableNamesInLine(int failureLine, MethodElementName locateMethod){
        Statement stmt = extractStmtInFailureLine(failureLine, locateMethod);
        return stmt.findAll(NameExpr.class).stream()
                //引数やメソッド呼び出しに用いられる変数を除外
                .map(NameExpr::toString)
                .collect(Collectors.toList());
    }
}
