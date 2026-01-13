package jisd.fl.probe.info;

import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.util.analyze.JavaParserUtil;

import java.nio.file.NoSuchFileException;
import java.util.NoSuchElementException;

public class TmpJavaParserUtils {
    static public Statement extractStmt(MethodElementName locateMethod, int locateLine) {
        try {
            return JavaParserUtil.getStatementByLine(locateMethod, locateLine).orElseThrow();
        } catch (NoSuchFileException e) {
            throw new RuntimeException("Class [" + locateMethod + "] is not found.");
        } catch (NoSuchElementException e){
            throw new RuntimeException("Cannot extract Statement from [" + locateMethod + ":" + locateLine + "].");
        }
    }
}
