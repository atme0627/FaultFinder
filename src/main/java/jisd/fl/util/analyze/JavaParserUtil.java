package jisd.fl.util.analyze;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.util.PropertyLoader;

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
    public static CallableDeclaration<?> getCallableDeclarationByName(CodeElement targetMethod) throws NoSuchFileException {
        Optional<CallableDeclaration> omd = extractCallableDeclaration(targetMethod)
                .stream()
                .filter(cd -> cd.getSignature().toString().equals(targetMethod.methodSignature))
                .findFirst();
        return omd.orElseThrow(() -> new NoSuchFileException(targetMethod.methodSignature + "is not found."));
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
        String strTargetMethod = targetMethod.getFullyQualifiedMethodName();
        BlockStmt bs = null;
        try {
            MethodDeclaration md = getCallableDeclarationByName(targetMethod).asMethodDeclaration();
            bs = md.getBody().get();
        } catch (NoSuchElementException e) {
            return null;
        }
        catch (NoSuchFileException e){
            try {
                ConstructorDeclaration cd = getCallableDeclarationByName(targetMethod).asConstructorDeclaration();
                bs = cd.getBody();
            }
            catch (NoSuchFileException ex){
                return null;
            }
        }
        return bs;
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
