package experiment.util.internal.finder;

import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.core.entity.ClassElementName;
import jisd.fl.infra.javaparser.JavaParserUtils;

import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

public class LineVariableNameExtractor {
    public Statement extractStmtInFailureLine(int failureLine, ClassElementName locateClass){
        try {
            return JavaParserUtils.getStatementByLine(locateClass, failureLine).orElseThrow();
        } catch (NoSuchFileException e) {
            throw new RuntimeException("Class [" + locateClass + "] is not found.");
        } catch (NoSuchElementException e){
            throw new RuntimeException("Cannot extract Statement from [" + locateClass + ":" + failureLine + "].");
        }
    }

    public List<String> extractVariableNamesInLine(int failureLine, ClassElementName locateClass){
        Statement stmt = extractStmtInFailureLine(failureLine, locateClass);
        return stmt.findAll(NameExpr.class).stream()
                //引数やメソッド呼び出しに用いられる変数を除外
                .map(NameExpr::toString)
                .collect(Collectors.toList());
    }
}
