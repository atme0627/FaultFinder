package jisd.fl.util.analyze;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class JavaParserUtil {
    //引数に与えられるclassNameがpackageを含まない可能性あり
    @Deprecated
    public static CompilationUnit parseClass(String className) throws NoSuchFileException {
        CodeElement targetClass = new CodeElement(className);
        return parseClass(targetClass);
    }

    public static CompilationUnit parseClass(CodeElement targetClass) throws NoSuchFileException {
        Path p = targetClass.getFilePath();
        if (!Files.exists(p)) throw new NoSuchFileException(p.toString());
        try {
            return StaticJavaParser.parse(p);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //methodNameはクラス、シグニチャを含む
    public static CallableDeclaration<?> getCallableDeclarationByName(CodeElement targetMethod) {
        try {
            Optional<CallableDeclaration> omd = extractCallableDeclaration(targetMethod)
                    .stream()
                    .filter(cd -> cd.getSignature().toString().equals(targetMethod.methodSignature))
                    .findFirst();
        return omd.orElseThrow(RuntimeException::new);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<CallableDeclaration> extractCallableDeclaration(CodeElement targetClass) throws NoSuchFileException {
        return extractNode(targetClass, CallableDeclaration.class);
    }

    public static List<Statement> extractStatement(CodeElement targetClass) throws NoSuchFileException {
        return extractNode(targetClass, Statement.class);
    }

    public static List<AssignExpr> extractAssignExpr(CodeElement targetClass) {
        try {
            return extractNode(targetClass, AssignExpr.class);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<VariableDeclarator> extractVariableDeclarator(CodeElement targetClass) {
        try {
            return extractNode(targetClass, VariableDeclarator.class);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
    }

    @Deprecated
    public static BlockStmt extractBodyOfMethod(String targetMethod){
        CodeElement cd = new CodeElement(targetMethod);
        return extractBodyOfMethod(cd);
    }

    public static BlockStmt extractBodyOfMethod(CodeElement targetMethod){
        CallableDeclaration<?> cd = getCallableDeclarationByName(targetMethod);
        return cd.isMethodDeclaration() ?
                cd.asMethodDeclaration().getBody().orElseThrow() :
                cd.asConstructorDeclaration().getBody();
    }

    private static <T extends Node> List<T> extractNode(CodeElement targetClass, Class<T> nodeClass) throws NoSuchFileException {
        return parseClass(targetClass)
                .findAll(nodeClass);
    }

    public static Optional<CallableDeclaration> getCallableDeclarationByLine(CodeElement targetClass, int line) throws NoSuchFileException {
        return getNodeByLine(targetClass, line, CallableDeclaration.class);
    }

    //その行を含む最小範囲のStatementを返す
    public static Optional<Statement> getStatementByLine(CodeElement targetClass, int line) throws NoSuchFileException {
        return getNodeByLine(targetClass, line, Statement.class);
    }

    private static <T extends Node> Optional<T> getNodeByLine(CodeElement targetClass, int line, Class<T> nodeClass) throws NoSuchFileException {
        return extractNode(targetClass, nodeClass)
                .stream()
                .filter(stmt -> stmt.getRange().isPresent())
                .filter(stmt -> (stmt.getBegin().get().line <= line))
                .filter(stmt -> (stmt.getEnd().get().line >= line))
                .min(Comparator.comparingInt(stmt -> stmt.getRange().get().getLineCount()));
    }

    public static ClassOrInterfaceDeclaration getParentOfMethod(MethodDeclaration md){
        Node parent = md.getParentNode().orElse(null);
        return (ClassOrInterfaceDeclaration) parent;
    }

    //packageがない場合""を返す
    public static String getPackageName(CompilationUnit unit){
        PackageDeclaration parentPackage = unit.getPackageDeclaration().orElse(null);
        return parentPackage != null ? parentPackage.getNameAsString() : "";
    }
}
