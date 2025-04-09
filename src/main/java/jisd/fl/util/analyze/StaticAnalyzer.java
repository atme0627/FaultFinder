package jisd.fl.util.analyze;

import com.github.javaparser.Range;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.nodeTypes.NodeWithRange;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.util.PropertyLoader;
import org.apache.commons.lang3.tuple.Pair;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

public class StaticAnalyzer {
    //プロジェクト全体
    public static Set<String> getClassNames() {
        return getClassNames(Paths.get(PropertyLoader.getProperty("targetSrcPath")));
    }

    //ディレクトリ指定
    public static Set<String> getClassNames(Path p) {
        ClassExplorer ce = new ClassExplorer(p);
        try {
            Files.walkFileTree(p, ce);
            return ce.result();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //targetClassNameはdemo.SortTestのように記述
    //返り値は demo.SortTest#test1(int a)の形式
    public static Set<String> getMethodNames(CodeElement targetClass) throws NoSuchFileException {
        return JavaParserUtil
                .extractCallableDeclaration(targetClass)
                .stream()
                .map(cd -> (targetClass.getFullyQualifiedClassName() + "#" + cd.getSignature()))
                .collect(Collectors.toSet());
    }

    //返り値はmap: targetMethodName ex.) demo.SortTest#test1(int a) --> Pair(start, end)
    public static Map<String, Pair<Integer, Integer>> getRangeOfAllMethods(CodeElement targetClass) throws NoSuchFileException {;
        return JavaParserUtil
                .extractCallableDeclaration(targetClass)
                .stream()
                .collect(toMap(
                    cd -> targetClass.getFullyQualifiedClassName() + "#" + cd.getSignature(),
                    StaticAnalyzer::getRangeOfNode
                ));
    }

    private static Pair<Integer, Integer> getRangeOfNode(NodeWithRange<?> node){
        return Pair.of(node.getBegin().get().line, node.getEnd().get().line);
    }

    public static Optional<Range> getRangeOfStatement(CodeElement targetClass, int line) {
        try {
            Optional<Statement> expStmt = JavaParserUtil.getStatementByLine(targetClass, line);
            return (expStmt.isPresent()) ? expStmt.get().getRange() : Optional.empty();
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
    }

    public static CodeElement getExtendedClassNameWithPackage(String parentSimpleClassName, String childClass){
        String targetPackage = childClass.replace(".", "/");
        while(true) {
            Set<String> classNames = getClassNames();
            for (String n : classNames) {
                String[] ns = n.split("\\.");
                if (ns[ns.length - 1].equals(parentSimpleClassName)) {
                    return targetPackage + "." + n;
                }
            }

            if(!targetPackage.contains("/")) break;
            targetPackage = targetPackage.substring(0, targetPackage.lastIndexOf("/"));
        }

        throw new RuntimeException("StaticAnalyzer#getClassNameWithPackage\n" +
                "Cannot find class: " + parentSimpleClassName);
    }

    @Deprecated
    public static String getMethodNameFormLine(String targetClassName, int line) throws NoSuchFileException {
        CodeElement targetClass = new CodeElement(targetClassName);
        return getMethodNameFormLine(targetClass, line);
    }

    public static String getMethodNameFormLine(CodeElement targetClass, int line) throws NoSuchFileException {
        CallableDeclaration cd = JavaParserUtil.getCallableDeclarationByLine(targetClass, line).orElseThrow();
        return targetClass.getFullyQualifiedClassName() + "#" + cd.getSignature();
    }

    //(クラス, 対象の変数) --> 変数が代入されている行（初期化も含む）
    public static List<Integer> getAssignLine(CodeElement targetClass, String variable) {
        //CodeElement targetClass = new CodeElement(className);
        List<Integer> assignLines =
                JavaParserUtil.extractAssignExpr(targetClass)
                        .stream()
                        .map(exp -> exp.isArrayAccessExpr() ? exp.asArrayAccessExpr().getName() : exp.getTarget())
                        .filter(exp -> exp.toString().equals(variable))
                        .filter(exp -> exp.getEnd().isPresent())
                        .map(exp -> exp.getEnd().get().line)
                        .collect(Collectors.toList());

        List<Integer> declarationLines =
                JavaParserUtil.extractVariableDeclarator(targetClass)
                        .stream()
                        .filter(exp -> exp.getName().toString().equals(variable))
                        .filter(exp -> exp.getEnd().isPresent())
                        .map(exp -> exp.getEnd().get().line)
                        .collect(Collectors.toList());

        return Stream.of(assignLines, declarationLines)
                        .flatMap(Collection::stream)
                        .sorted(Comparator.naturalOrder())
                        .collect(Collectors.toList());
    }

    //メソッド --> メソッドが呼ばれている行
    //methodNameはクラス、シグニチャを含む
    public static List<Integer> getMethodCallingLine(CodeElement targetMethod) throws NoSuchFileException {
        return JavaParserUtil.extractBodyOfMethod(targetMethod)
                        .findAll(MethodCallExpr.class)
                        .stream()
                        .filter(exp -> exp.getBegin().isPresent())
                        .map(exp -> exp.getBegin().get().line)
                        .distinct()
                        .sorted(Comparator.naturalOrder())
                        .collect(Collectors.toList());
    }

    public static Set<Integer> canSetLineOfClass(String targetClassName, String variable){
        Set<String> methods;
        Set<Integer> canSet = new HashSet<>();

        CodeElement targetClass = new CodeElement(targetClassName);
        try {
            methods = getMethodNames(targetClass);
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
        BlockStmt bs;
        bs = JavaParserUtil.extractBodyOfMethod(targetMethod);
        //bodyが空の場合がある。
        if(bs == null) return canSet;

        class SimpleNameVisitor extends VoidVisitorAdapter<String> {
            @Override
            public void visit(SimpleName n, String arg) {
                if(n.getIdentifier().equals(variable)){
                    for(int i = -2; i <= 2; i++) {
                        if (bs.getBegin().get().line < n.getBegin().get().line + i
                                && n.getBegin().get().line + i <= bs.getEnd().get().line) {
                            canSet.add(n.getBegin().get().line + i);
                        }
                    }
                }
                super.visit(n, arg);
            }
        }

        bs.accept(new SimpleNameVisitor(), "");
        return canSet;
    }

    static class ClassExplorer implements FileVisitor<Path> {
        Path p;
        Set<String> classNames;

        public ClassExplorer(Path targetSrcPath){
            this.p = targetSrcPath;
            this.classNames = new HashSet<>();
        }

        public Set<String> result(){
            return classNames;
        }

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
}

