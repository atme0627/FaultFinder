package jisd.fl.util.analyze;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.core.entity.MethodElementName;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class JavaParserUtil {
    static {
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21); // または JAVA_16/21 など
        StaticJavaParser.setConfiguration(config);
    }
    //引数に与えられるclassNameがpackageを含まない可能性あり
    @Deprecated
    public static CompilationUnit parseClass(String className) throws NoSuchFileException {
        MethodElementName targetClass = new MethodElementName(className);
        return parseClass(targetClass);
    }

    //TODO: parseTestClassのところはいらないはず
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

    //methodNameはクラス、シグニチャを含む
    public static CallableDeclaration<?> getCallableDeclarationByName(MethodElementName targetMethod) throws NoSuchFileException {
        Optional<CallableDeclaration> omd = extractCallableDeclaration(targetMethod)
                .stream()
                .filter(cd -> cd.getSignature().toString().equals(targetMethod.methodSignature))
                .findFirst();
        return omd.orElseThrow(RuntimeException::new);
    }

    public static List<CallableDeclaration> extractCallableDeclaration(MethodElementName targetClass) throws NoSuchFileException {
        return extractNode(targetClass, CallableDeclaration.class);
    }

    public static List<AssignExpr> extractAssignExpr(MethodElementName targetClass) throws NoSuchFileException {
        return parseClass(targetClass)
                .findAll(AssignExpr.class);
    }

    public static List<VariableDeclarator> extractVariableDeclarator(MethodElementName targetClass) throws NoSuchFileException {
        return extractNode(targetClass, VariableDeclarator.class);
    }

    public static BlockStmt searchBodyOfMethod(MethodElementName targetMethod) throws NoSuchFileException {
        CallableDeclaration<?> cd = getCallableDeclarationByName(targetMethod);
        return cd.isMethodDeclaration() ?
                cd.asMethodDeclaration().getBody().orElseThrow() :
                cd.asConstructorDeclaration().getBody();
    }

    private static <T extends Node> List<T> extractNode(MethodElementName targetClass, Class<T> nodeClass) throws NoSuchFileException {
        return parseClass(targetClass)
                .findAll(nodeClass);
    }

    public static Optional<CallableDeclaration> getCallableDeclarationByLine(MethodElementName targetClass, int line) throws NoSuchFileException {
        return getNodeByLine(targetClass, line, CallableDeclaration.class);
    }

    //その行を含む最小範囲のStatementを返す
    public static Optional<Statement> getStatementByLine(MethodElementName targetClass, int line) throws NoSuchFileException {
        return getNodeByLine(targetClass, line, Statement.class);
    }

    private static <T extends Node> Optional<T> getNodeByLine(MethodElementName targetClass, int line, Class<T> nodeClass) throws NoSuchFileException {
        return extractNode(targetClass, nodeClass)
                .stream()
                .filter(stmt -> stmt.getRange().isPresent())
                .filter(stmt -> (stmt.getBegin().get().line <= line))
                .filter(stmt -> (stmt.getEnd().get().line >= line))
                .min(Comparator.comparingInt(stmt -> stmt.getRange().get().getLineCount()));
    }

}
