package jisd.fl.util.analyze;

import com.github.javaparser.Range;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.nodeTypes.NodeWithRange;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.probe.assertinfo.VariableInfo;
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
        return getClassNames(Paths.get(PropertyLoader.getProperty("targetSrcDir")));
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

    public static Optional<Range> getRangeOfStatement(CodeElement targetClass, int line) throws NoSuchFileException {
        Optional<Statement> expStmt = JavaParserUtil.getStatementByLine(targetClass, line);
        return (expStmt.isPresent()) ? expStmt.get().getRange() : Optional.empty();
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
                null;
        try {
            assignLines = JavaParserUtil.extractAssignExpr(targetClass)
                    .stream()
                    .map(exp -> exp.isArrayAccessExpr() ? exp.asArrayAccessExpr().getName() : exp.getTarget())
                    .filter(exp -> exp.toString().equals(variable))
                    .filter(exp -> exp.getEnd().isPresent())
                    .map(exp -> exp.getEnd().get().line)
                    .collect(Collectors.toList());
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        List<Integer> declarationLines =
                null;
        try {
            declarationLines = JavaParserUtil.extractVariableDeclarator(targetClass)
                    .stream()
                    .filter(exp -> exp.getName().toString().equals(variable))
                    .filter(exp -> exp.getEnd().isPresent())
                    .map(exp -> exp.getEnd().get().line)
                    .collect(Collectors.toList());
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

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

    public static List<Integer> getCanSetLine(VariableInfo variableInfo) {
        if(variableInfo.isField()) {
            return StaticAnalyzer.canSetLineOfClass(variableInfo.getLocateMethodElement(), variableInfo.getVariableName());
        }
        else {
            return StaticAnalyzer.canSetLineOfMethod(variableInfo.getLocateMethodElement(), variableInfo.getVariableName());
        }
    }

    public static List<Integer> canSetLineOfClass(CodeElement targetClass, String variable){
        Set<String> methods;
        List<Integer> canSet = new ArrayList<>();

        try {
            methods = getMethodNames(targetClass);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        methods.stream()
                .map(CodeElement::new)
                .forEach(e -> canSet.addAll(canSetLineOfMethod(e, variable)));

        return canSet.stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }


    public static List<Integer> canSetLineOfMethod(CodeElement targetMethod, String variable){
        List<Integer> canSet = new ArrayList<>();
        BlockStmt bs = JavaParserUtil.extractBodyOfMethod(targetMethod);
        //bodyが空の場合がある。
        if(bs == null) return canSet;

        bs.findAll(SimpleName.class)
                .stream()
                .filter(sn -> sn.getIdentifier().endsWith(variable))
                .forEach(sn -> {
                    for(int i = -2; i <= 2; i++) {
                        if (bs.getBegin().get().line < sn.getBegin().get().line + i
                                && sn.getBegin().get().line + i <= bs.getEnd().get().line) {
                            canSet.add(sn.getBegin().get().line + i);
                        }
                    }
                });

        return canSet.stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());
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

