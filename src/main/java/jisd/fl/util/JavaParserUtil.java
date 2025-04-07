package jisd.fl.util;

import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.util.analyze.CodeElement;

import javax.swing.plaf.nimbus.State;
import javax.swing.text.html.Option;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public class JavaParserUtil {
    public static CompilationUnit parseClass(CodeElement targetClass, boolean isTest) throws NoSuchFileException {
       return parseClass(targetClass.getFullyQualifiedClassName(), isTest);
    }

    public static CompilationUnit parseClass(String className, boolean isTest) throws NoSuchFileException {
        Path p = Paths.get(getFullPath(className, isTest));
        CompilationUnit unit = null;
        try {
            unit = StaticJavaParser.parse(p);
        } catch (NoSuchFileException e) {
            //mainでダメならtestを試す
            if(isTest)  throw new NoSuchFileException(className);

            p = Paths.get(getFullPath(className, true));
            try {
                unit = StaticJavaParser.parse(p);
            } catch (NoSuchFileException ex) {
                throw new NoSuchFileException(className);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } catch (IOException e){
            throw new RuntimeException(e);
        }
        return unit;
    }

    //methodNameはクラス、シグニチャを含む
    public static MethodDeclaration parseMethod(String methodName) throws NoSuchFileException {
        String className = methodName.split("#")[0];
        CompilationUnit unit = parseClass(className, false);
        Optional<MethodDeclaration> omd = unit.findFirst(MethodDeclaration.class,
                (n)->n.getSignature().toString().equals(methodName.split("#")[1]));

        if(omd.isEmpty()) throw new NoSuchFileException(methodName + "is not found.");
        return omd.get();
    }

    public static ConstructorDeclaration parseConstructor(String constructorName) throws NoSuchFileException {
        String className = constructorName.split("#")[0];
        CompilationUnit unit = parseClass(className, false);
        Optional<ConstructorDeclaration> ocd = unit.findFirst(ConstructorDeclaration.class,
                (n)->n.getSignature().toString().equals(constructorName.split("#")[1]));

        if(ocd.isEmpty()) throw new NoSuchFileException(constructorName + "is not found.");
        return ocd.get();
    }


    public static List<CallableDeclaration> extractCallableDeclaration(CodeElement targetClass) throws NoSuchFileException {
        return extractNode(targetClass, CallableDeclaration.class);
    }

    public static List<Statement> extractStatement(CodeElement targetClass) throws NoSuchFileException {
        return extractNode(targetClass, Statement.class);
    }

    private static <T extends Node> List<T> extractNode(CodeElement targetClass, Class<T> nodeClass) throws NoSuchFileException {
        return parseClass(targetClass, false)
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


    private static String getFullPath(String className, boolean isTest){
        String targetSrcDir = PropertyLoader.getProperty("targetSrcDir");
        String testSrcDir = PropertyLoader.getProperty("testSrcDir");
        return ((isTest) ? testSrcDir : targetSrcDir)
                + "/" + className.replace(".", "/") + ".java";
    }

    public static boolean isConstructor(String methodName){
        String className = methodName.split("#")[0];
        String classNameWithoutPackage = className.substring(className.lastIndexOf('.') + 1);
        String methodNameWithoutPackage = methodName.substring(0, methodName.indexOf("(")).split("#")[1];
        return classNameWithoutPackage.equals(methodNameWithoutPackage);
    }

}
