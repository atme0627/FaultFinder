package jisd.fl.infra.javaparser;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.util.ToolPaths;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;

public class JavaParserUtils {
    static {
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21); // または JAVA_16/21 など
        StaticJavaParser.setConfiguration(config);
    }

    //TODO: 内部クラスでもトップレベル全体が帰るようになってる!!!
    public static CompilationUnit parseClass(ClassElementName targetClass) throws NoSuchFileException {
        Path p = ToolPaths.findSourceFilePath(targetClass).get();
        try {
            CompilationUnit parsed = StaticJavaParser.parse(p);
                return parsed;
        } catch (NoSuchFileException e){
            throw e;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static public Statement extractStmt(ClassElementName locateClass, int locateLine) {
        try {
            return getStatementByLine(locateClass, locateLine).orElseThrow();
        } catch (NoSuchFileException e) {
            throw new RuntimeException("Class [" + locateClass + "] is not found.");
        } catch (NoSuchElementException e){
            throw new RuntimeException("Cannot extract Statement from [" + locateClass + ":" + locateLine + "].");
        }
    }

    public static BlockStmt extractBodyOfMethod(MethodElementName targetMethod) throws NoSuchFileException {
        CallableDeclaration<?> cd = extractNode(targetMethod.classElementName, CallableDeclaration.class)
                .stream()
                .filter(cd1 -> cd1.getSignature().toString().equals(targetMethod.methodSignature))
                .findFirst().orElseThrow(RuntimeException::new);
        return cd.isMethodDeclaration() ?
                cd.asMethodDeclaration().getBody().orElseThrow() :
                cd.asConstructorDeclaration().getBody();
    }

    //その行を含む最小範囲のStatementを返す
    public static Optional<Statement> getStatementByLine(ClassElementName targetClass, int line) throws NoSuchFileException {
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

    public static List<Integer> findFieldVariableDeclarationLine(ClassElementName targetClass, String fieldName){
        try {
            CompilationUnit cu = parseClass(targetClass);
            return cu.findAll(FieldDeclaration.class).stream()
                    .filter(fd -> fd.getVariables().stream().anyMatch(vd -> vd.getNameAsString().equals(fieldName)))
                    .map(fd -> fd.getRange().map(r -> r.begin.line).orElse(null))
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList();
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T extends Node> List<T> extractNode(ClassElementName targetClass, Class<T> nodeClass) throws NoSuchFileException {
        return JavaParserUtils.parseClass(targetClass)
                .findAll(nodeClass);
    }
}
