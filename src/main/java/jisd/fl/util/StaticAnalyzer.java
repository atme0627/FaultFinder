package jisd.fl.util;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
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
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if(file.toString().endsWith(".java")){
                    classNames.add(p.relativize(file).toString().split("\\.")[0].replace("/", "."));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                System.out.println("failed: " + file.toString());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
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


    //targetSrcPathは最後"/"なし
    //targetClassNameはdemo.SortTestのように記述
    //返り値は demo.SortTest#test1(int a)の形式
    //publicメソッド以外は取得しない
    //testMethodはprivateのものを含めないのでpublicOnlyをtrueに
    public static Set<String> getMethodNames(String targetClassName, boolean publicOnly, boolean withPackage, boolean withSignature) {
        Set<String> methodNames = new LinkedHashSet<>();
        CompilationUnit unit = JavaParserUtil.parseClass(targetClassName);
        Function<CallableDeclaration<?>, String> methodNameBuilder = (n) -> (
                ((withPackage) ? targetClassName.replace("/", ".") + "#" : "")
                + ((withSignature) ? n.getSignature() : n.getNameAsString()));

        class MethodVisitor extends VoidVisitorAdapter<String>{
            @Override
            public void visit(MethodDeclaration n, String arg) {
                if(!publicOnly || n.isPublic()) {
                    methodNames.add(methodNameBuilder.apply(n));
                    super.visit(n, arg);
                }
            }
            @Override
            public void visit(ConstructorDeclaration n, String arg) {
                if(!publicOnly || n.isPublic()) {
                    methodNames.add(methodNameBuilder.apply(n));
                    super.visit(n, arg);
                }
            }
        }
        unit.accept(new MethodVisitor(), "");
        return methodNames;
    }

    public static Set<String> getAllMethods(String targetSrcDir, boolean withPackage, boolean withSignature){
        Set<String> allClasses = getClassNames(targetSrcDir);
        Set<String> allMethods = new HashSet<>();
        for(String className : allClasses){
            allMethods.addAll(getMethodNames(className, false, withPackage, withSignature));
        }
        return allMethods;
    }

    //返り値はmap: targetMethodName ex.) demo.SortTest#test1(int a) --> Pair(start, end)
    public static Map<String, Pair<Integer, Integer>> getRangeOfMethods(String targetClassName) {
        Map<String, Pair<Integer, Integer>> rangeOfMethod = new HashMap<>();
        CompilationUnit unit = JavaParserUtil.parseClass(targetClassName);

        class MethodVisitor extends VoidVisitorAdapter<String>{
            @Override
            public void visit(MethodDeclaration n, String arg) {
                rangeOfMethod.put(targetClassName.replace("/", ".")  + "#" + n.getSignature(), Pair.of(n.getBegin().get().line, n.getEnd().get().line));
                super.visit(n, arg);
            }

            @Override
            public void visit(ConstructorDeclaration n, String arg) {
                rangeOfMethod.put(targetClassName.replace("/", ".")  + "#" + n.getSignature(), Pair.of(n.getBegin().get().line, n.getEnd().get().line));
                super.visit(n, arg);
            }
        }
        unit.accept(new MethodVisitor(), "");
        return rangeOfMethod;
    }


    //返り値はmap ex.) Integer --> Pair(start, end)
    public static Map<Integer, Pair<Integer, Integer>> getRangeOfStatement(String targetClassName) {
        Map<Integer, Pair<Integer, Integer>> rangeOfStatement = new HashMap<>();
        CompilationUnit unit = JavaParserUtil.parseClass(targetClassName);

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



    public static MethodCallGraph getMethodCallGraph(String targetSrcPath) {
        Set<String> targetClassNames = getClassNames(targetSrcPath);
        Set<String> targetMethodNames = new HashSet<>();
        MethodCallGraph mcg = new MethodCallGraph();

        for(String targetClassName : targetClassNames) {
            targetMethodNames.addAll(getMethodNames(targetClassName, false, false, true));
        }

        for(String targetClassName : targetClassNames){
            getCalledMethodsForClass(targetClassName, targetMethodNames, mcg);
        }

        return mcg;
    }


    //直接的な呼び出し関係しか取れてない
    //ex.) NormalDistributionImpl#getInitialDomainはオーバライドメソッドであり
    //その抽象クラス内で呼び出されているが、この呼び出し関係は取れていない。
    private static void getCalledMethodsForClass(String targetClassName, Set<String> targetMethodNames, MethodCallGraph mcg) {
        CompilationUnit unit = JavaParserUtil.parseClass(targetClassName);

        class MethodVisitor extends VoidVisitorAdapter<String>{
            @Override
            public void visit(MethodCallExpr n, String arg) {
                Optional<MethodDeclaration> callerMethodOptional = n.findAncestor(MethodDeclaration.class);

                //メソッド呼び出しがメソッド内で行われていない場合(ex. フィールドの定義)
                if(callerMethodOptional.isEmpty()){
                    return;
                }

                MethodDeclaration callerMethod = callerMethodOptional.get();
                String callerMethodName = targetClassName + "#" + callerMethod.getNameAsString();
                String calleeMethodName = "fail";
                for(String targetMethodName : targetMethodNames){
                    if(getMethodNameWithoutPackageAndSig(targetMethodName).equals(n.getNameAsString())){
                        calleeMethodName = targetMethodName;
                        break;
                    }
                }

                //呼び出されているメソッドが、外部のものでないことを確認
                if(!targetMethodNames.contains(calleeMethodName)){
                    return;
                }

                //System.out.println("caller: " + callerMethodName +  " callee: " + calleeMethodName);

                mcg.setElement(callerMethodName, calleeMethodName);
                super.visit(n, arg);
            }
        }
        unit.accept(new MethodVisitor(), "");
    }

    public static Set<String> getCalledMethodsForMethod(String callerMethodName,  Set<String> targetMethodNames) {
        Set<String> calleeMethods = new HashSet<>();
        MethodDeclaration callerMethod = JavaParserUtil.parseMethod(callerMethodName);

        class MethodVisitor extends VoidVisitorAdapter<String>{
            @Override
            public void visit(MethodCallExpr n, String arg) {
                for(String targetMethodName : targetMethodNames) {
                    if (getMethodNameWithoutPackageAndSig(targetMethodName).equals(n.getNameAsString())) {
                        calleeMethods.add(targetMethodName);
                        break;
                    }
                }
                super.visit(n, arg);
            }
        }

        callerMethod.accept(new MethodVisitor(), "");
        return calleeMethods;
    }

    private static String getMethodNameWithoutPackageAndSig(String methodName){
        return methodName.split("#")[1].split("\\(")[0];
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

    public static String getMethodNameFormLine(String targetClassName, int line) {
        Map<String, Pair<Integer, Integer>> ranges = getRangeOfMethods(targetClassName);
        String[] method = new String[1];
        ranges.forEach((m, pair) -> {
            if(pair.getLeft() <= line && line <= pair.getRight()) {
                method[0] = m;
            }
        });

        return method[0];
    }

    //(クラス, 対象の変数) --> 変数が代入されている行（初期化も含む）
    public static List<Integer> getAssignLine(String className, String variable) {
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

        CompilationUnit unit = JavaParserUtil.parseClass(className);
        unit.accept(new MethodVisitor(), variable);
        assignLine.sort(Comparator.naturalOrder());
        return assignLine;
    }

    //(メソッド, 対象の変数) --> メソッドが呼ばれている行
    //methodNameはクラス、シグニチャを含む
    public static List<Integer> getMethodCallingLine(String methodName) {
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

        MethodDeclaration md = JavaParserUtil.parseMethod(methodName);
        md.accept(new MethodVisitor(), "");
        methodCallingLine.sort(Comparator.naturalOrder());
        return methodCallingLine;
    }
}

