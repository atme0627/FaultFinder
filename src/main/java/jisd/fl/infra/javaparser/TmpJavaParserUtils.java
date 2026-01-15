package jisd.fl.infra.javaparser;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.core.entity.MethodElementName;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static jisd.fl.util.analyze.JavaParserUtil.extractNode;

public class TmpJavaParserUtils {
    static {
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21); // または JAVA_16/21 など
        StaticJavaParser.setConfiguration(config);
    }

    public static CompilationUnit parseClass(MethodElementName targetClass) throws NoSuchFileException {
        Path p = targetClass.getFilePath();
        try {
            return StaticJavaParser.parse(p);
        } catch (NoSuchFileException e){
            throw e;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static public Statement extractStmt(MethodElementName locateMethod, int locateLine) {
        try {
            return getStatementByLine(locateMethod, locateLine).orElseThrow();
        } catch (NoSuchFileException e) {
            throw new RuntimeException("Class [" + locateMethod + "] is not found.");
        } catch (NoSuchElementException e){
            throw new RuntimeException("Cannot extract Statement from [" + locateMethod + ":" + locateLine + "].");
        }
    }

    public static BlockStmt extractBodyOfMethod(MethodElementName targetMethod) throws NoSuchFileException {
        CallableDeclaration<?> cd = extractNode(targetMethod, CallableDeclaration.class)
                .stream()
                .filter(cd1 -> cd1.getSignature().toString().equals(targetMethod.methodSignature))
                .findFirst().orElseThrow(RuntimeException::new);
        return cd.isMethodDeclaration() ?
                cd.asMethodDeclaration().getBody().orElseThrow() :
                cd.asConstructorDeclaration().getBody();
    }

    //その行を含む最小範囲のStatementを返す
    public static Optional<Statement> getStatementByLine(MethodElementName targetClass, int line) throws NoSuchFileException {
        return extractNode(targetClass, Statement.class)
                .stream()
                .filter(stmt -> stmt.getRange().isPresent())
                .filter(stmt -> (stmt.getBegin().get().line <= line))
                .filter(stmt -> (stmt.getEnd().get().line >= line))
                .min(Comparator.comparingInt(stmt -> stmt.getRange().get().getLineCount()));
    }

    //あるメソッド内の特定の変数の定義行の番号を取得する。
    public static List<Integer> findLocalVariableDeclarationLine(MethodElementName targetMethod, String localVarName){
        List<Integer> result;
        try {
            BlockStmt bs = extractBodyOfMethod(targetMethod);
            List<VariableDeclarator> vds = bs.findAll(VariableDeclarator.class);
            result = vds.stream()
                    .filter(vd1 -> vd1.getNameAsString().equals(localVarName))
                    .map(vd -> vd.getRange().get().begin.line)
                    .toList();
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}
