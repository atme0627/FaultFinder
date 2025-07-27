package jisd.fl.util.analyze;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

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

    public static CompilationUnit parseTestClass(MethodElementName targetClass) throws NoSuchFileException {
        Path p = targetClass.getFilePath(true);
        if (!Files.exists(p)) throw new NoSuchFileException(p.toString());
        try {
            return StaticJavaParser.parse(p);
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

    public static List<Statement> extractStatement(MethodElementName targetClass) throws NoSuchFileException {
        return extractNode(targetClass, Statement.class);
    }

    public static List<AssignExpr> extractAssignExpr(MethodElementName targetClass) throws NoSuchFileException {
        return extractNode(targetClass, AssignExpr.class);
    }

    public static List<VariableDeclarator> extractVariableDeclarator(MethodElementName targetClass) throws NoSuchFileException {
        return extractNode(targetClass, VariableDeclarator.class);
    }

    @Deprecated
    public static BlockStmt extractBodyOfMethod(String targetMethod) throws NoSuchFileException {
        MethodElementName cd = new MethodElementName(targetMethod);
        return extractBodyOfMethod(cd);
    }

    public static BlockStmt extractBodyOfMethod(MethodElementName targetMethod) throws NoSuchFileException {
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

    public static ClassOrInterfaceDeclaration getParentOfMethod(CallableDeclaration cd){
        Node parent = cd.getParentNode().orElse(null);
        return (ClassOrInterfaceDeclaration) parent;
    }

    //packageがない場合""を返す
    public static String getPackageName(CompilationUnit unit){
        PackageDeclaration parentPackage = unit.getPackageDeclaration().orElse(null);
        return parentPackage != null ? parentPackage.getNameAsString() : "";
    }

    public static boolean isFieldVariable(CompilationUnit cu, NameExpr variable){
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        JavaParserFacade javaParserFacade = JavaParserFacade.get(typeSolver);
        ResolvedValueDeclaration decl = javaParserFacade.solve(variable).getCorrespondingDeclaration();
        return false;
    }
}
