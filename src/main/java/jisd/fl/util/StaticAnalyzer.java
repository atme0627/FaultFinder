package jisd.fl.util;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import jisd.info.ClassInfo;
import org.apache.commons.lang3.tuple.Pair;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Function;

public class StaticAnalyzer {
    public static Set<String> getClassNames(String targetSrcPath) {
        Set<String> classNames = new LinkedHashSet<>();
        Path p = Paths.get(targetSrcPath);

        class ClassExplorer implements FileVisitor<Path> {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if(file.toString().endsWith(".java")){
                    classNames.add(p.relativize(file).toString().split("\\.")[0].replace("/", "."));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                System.out.println("failed: " + file.toString());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        }

        try {
            Files.walkFileTree(p, new ClassExplorer());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return classNames;
    }

    public static Set<String> getAllMethods(String targetSrcDir, boolean withPackage, boolean withSignature) throws NoSuchFileException {
        Set<String> allClasses = getClassNames(targetSrcDir);
        Set<String> allMethods = new HashSet<>();
        for(String className : allClasses){
            allMethods.addAll(getMethodNames(className, false, false, withPackage, withSignature));
        }
        return allMethods;
    }

    //targetSrcPathは最後"/"なし
    //targetClassNameはdemo.SortTestのように記述
    //返り値は demo.SortTest#test1(int a)の形式
    //publicメソッド以外は取得しない
    //testMethodはprivateのものを含めないのでpublicOnlyをtrueに
    public static Set<String> getMethodNames(String targetClassName, boolean isTest, boolean publicOnly, boolean withPackage, boolean withSignature) throws NoSuchFileException {
        Set<String> methodNames = new LinkedHashSet<>();
        CompilationUnit unit = JavaParserUtil.parseClass(targetClassName, isTest);
        Function<CallableDeclaration<?>, String> methodNameBuilder = (n) -> (
                ((withPackage) ? targetClassName.replace("/", ".") + "#" : "")
                + ((withSignature) ? n.getSignature() : n.getNameAsString()));

        class MethodVisitor extends VoidVisitorAdapter<String>{
            @Override
            public void visit(MethodDeclaration n, String arg) {
                if(!publicOnly || n.isPublic()) {
                    if(!isTest || n.isAnnotationPresent("Test")){
                        //testの場合@Testがないものは含まない
                        methodNames.add(methodNameBuilder.apply(n));
                    }
                    super.visit(n, arg);
                }
            }

            @Override
            public void visit(ConstructorDeclaration n, String arg) {
                if(!publicOnly || n.isPublic()) {
                    if(!isTest || n.isAnnotationPresent("Test")){
                        //testの場合@Testがないものは含まない
                        methodNames.add(methodNameBuilder.apply(n));
                    }
                    super.visit(n, arg);
                }
            }
        }
        unit.accept(new MethodVisitor(), "");
        return methodNames;
    }

    //返り値はmap: targetMethodName ex.) demo.SortTest#test1(int a) --> Pair(start, end)
    public static Map<String, Pair<Integer, Integer>> getRangeOfAllMethods(String targetClassName) throws NoSuchFileException {
        Map<String, Pair<Integer, Integer>> rangeOfMethods = new HashMap<>();
        CompilationUnit unit = JavaParserUtil.parseClass(targetClassName, false);

        class MethodVisitor extends VoidVisitorAdapter<String>{
            @Override
            public void visit(MethodDeclaration n, String arg) {
                rangeOfMethods.put(targetClassName.replace("/", ".")  + "#" + n.getSignature(), Pair.of(n.getBegin().get().line, n.getEnd().get().line));
                super.visit(n, arg);
            }

            @Override
            public void visit(ConstructorDeclaration n, String arg) {
                rangeOfMethods.put(targetClassName.replace("/", ".")  + "#" + n.getSignature(), Pair.of(n.getBegin().get().line, n.getEnd().get().line));
                super.visit(n, arg);
            }
        }
        unit.accept(new MethodVisitor(), "");
        return rangeOfMethods;
    }

    //返り値はmap ex.) Integer --> Pair(start, end)
    public static Map<Integer, Pair<Integer, Integer>> getRangeOfAllStatements(String targetClassName) {
        Map<Integer, Pair<Integer, Integer>> rangeOfStatement = new TreeMap<>();
        CompilationUnit unit = null;
        try {
            unit = JavaParserUtil.parseClass(targetClassName, false);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        class MethodVisitor extends VoidVisitorAdapter<String>{
            @Override
            public void visit(ExpressionStmt n, String arg) {
                Pair<Integer, Integer> range = Pair.of(n.getBegin().get().line, n.getEnd().get().line);
                for(int i = range.getLeft(); i <= range.getRight(); i++) {
                    rangeOfStatement.put(i, range);
                }
                super.visit(n, arg);
            }
        }

        unit.accept(new MethodVisitor(), "");
        return rangeOfStatement;
    }

    public static String getClassNameWithPackage(String targetSrcDir, String className) {
        Set<String> classNames = getClassNames(targetSrcDir);
        for(String n : classNames){
            String[] ns = n.split("\\.");
            if(ns[ns.length - 1].equals(className)){
                return n;
            }
        }
        throw new RuntimeException("StaticAnalyzer#getClassNameWithPackage\n" +
                "Cannot find class: " + className);
    }

    public static String getMethodNameFormLine(String targetClassName, int line) throws NoSuchFileException {
        Map<String, Pair<Integer, Integer>> ranges = getRangeOfAllMethods(targetClassName);
        String[] method = new String[1];
        ranges.forEach((m, pair) -> {
            if(pair.getLeft() <= line && line <= pair.getRight()) {
                method[0] = m;
            }
        });

        return method[0];
    }

    //(クラス, 対象の変数) --> 変数が代入されている行（初期化も含む）
    public static List<Integer> getAssignLine(String className, String variable) throws NoSuchFileException {
        List<Integer> assignLine = new ArrayList<>();

        class MethodVisitor extends VoidVisitorAdapter<String> {
            @Override
            public void visit(AssignExpr n, String variable) {
                Expression targetExpr = n.getTarget();

                //配列参照の場合
                if(targetExpr.isArrayAccessExpr()){
                    targetExpr = targetExpr.asArrayAccessExpr().getName();
                }

                if (targetExpr.toString().equals(variable)) {
                    assignLine.add(n.getEnd().get().line);
                }
                super.visit(n, variable);
            }

            @Override
            public void visit(VariableDeclarator n, String variable) {
                if (n.getName().toString().equals(variable)) {
                    assignLine.add(n.getEnd().get().line);
                }
                super.visit(n, variable);
            }
        }

        CompilationUnit unit = JavaParserUtil.parseClass(className, false);
        unit.accept(new MethodVisitor(), variable);
        assignLine.sort(Comparator.naturalOrder());
        return assignLine;
    }

    //メソッド --> メソッドが呼ばれている行
    //methodNameはクラス、シグニチャを含む
    public static List<Integer> getMethodCallingLine(String methodName) throws NoSuchFileException {
        List<Integer> methodCallingLine = new ArrayList<>();

        class MethodVisitor extends VoidVisitorAdapter<String> {
            @Override
            public void visit(MethodCallExpr n, String arg) {
                int line = n.getBegin().get().line;
                if(!methodCallingLine.contains(line)) {
                    methodCallingLine.add(line);
                }
                super.visit(n, arg);
            }
        }

        BlockStmt bs = bodyOfMethod(methodName);
        bs.accept(new MethodVisitor(), "");
        methodCallingLine.sort(Comparator.naturalOrder());
        return methodCallingLine;
    }


    //フルパスの引数を含んだ状態で保持されているClassInfoに対応するためのメソッド
    //methodNames()では、引数の型につく型パラメータは省略される。
    public static String fullNameOfMethod(String shortMethodName, ClassInfo ci){
        List<String> fullNameMethods = ci.methodNames();
        for(String fullName : fullNameMethods){
            if(shortMethodName.equals(shortMethodName(fullName))) return fullName;
        }
        throw new RuntimeException(shortMethodName + " is not found.");
    }

    private static String shortMethodName(String fullMethodName){
        String name = fullMethodName.split("\\(")[0];
        String args = fullMethodName.substring(fullMethodName.indexOf("(")+1, fullMethodName.indexOf(")"));
        List<String> argList = new ArrayList<>(List.of(args.split(", ")));
        List<String> shortArgList = new ArrayList<>();
        for(String arg : argList){
            if(arg.contains(".") || arg.contains("/")) {
                String[] splitArgs = arg.split("[./]");
                shortArgList.add(splitArgs[splitArgs.length - 1]);
            }
            else {
                shortArgList.add(arg);
            }
        }

        StringBuilder shortMethod = new StringBuilder(name + "(");
        for(int i = 0; i < shortArgList.size(); i++){
            String shortArg = shortArgList.get(i);
            shortMethod.append(shortArg);
            if (i != shortArgList.size() - 1) shortMethod.append(", ");
        }
        shortMethod.append(")");
        return shortMethod.toString();
    }

    public static BlockStmt bodyOfMethod(String targetMethod){
        BlockStmt bs = null;
        try {
            MethodDeclaration md = JavaParserUtil.parseMethod(targetMethod);
            bs = md.getBody().get();
        } catch (NoSuchElementException e) {
            throw new RuntimeException(e);
        }
        catch (NoSuchFileException e){
            try {
                ConstructorDeclaration cd = JavaParserUtil.parseConstructor(targetMethod);
                bs = cd.getBody();
            }
            catch (NoSuchFileException ex){
                throw new RuntimeException();
            }
        }
        return bs;
    }

    public static Set<Integer> canSetLineOfClass(String targetClass, String variable){
        String targetSrcDir = PropertyLoader.getProperty("targetSrcDir");
        Set<String> methods;
        Set<Integer> canSet = new HashSet<>();
        try {
            methods = getMethodNames(targetClass, false, false, true, true);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        for(String method: methods){
            canSet.addAll(canSetLineOfMethod(method, variable));
        }

        return canSet;
    }


    public static Set<Integer> canSetLineOfMethod(String targetMethod, String variable){
        Set<Integer> canSet = new HashSet<>();
        BlockStmt bs = bodyOfMethod(targetMethod);

        class SimpleNameVisitor extends VoidVisitorAdapter<String> {
            @Override
            public void visit(SimpleName n, String arg) {
                if(n.getIdentifier().equals(variable)){
                    canSet.add(n.getBegin().get().line);
                    canSet.add(n.getBegin().get().line - 1);
                    canSet.add(n.getBegin().get().line + 1);
                }
                super.visit(n, arg);
            }
        }

        bs.accept(new SimpleNameVisitor(), "");
        return canSet;
    }
}

