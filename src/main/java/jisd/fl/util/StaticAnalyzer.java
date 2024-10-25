package jisd.fl.util;

import org.apache.commons.lang3.tuple.Pair;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class StaticAnalyzer {
    //targetSrcPathは最後"/"なし
    //targetClassNameはdemo.SortTestのように記述
    //返り値は demo.SortTest#test1の形式
    public static ArrayList<String> getMethodNames(String targetSrcPath, String targetClassName) throws IOException {
        String targetJavaPath = targetSrcPath + "/" + targetClassName.replace(".", "/") + ".java";
        Path p = Paths.get(targetJavaPath);
        ArrayList<String> methodNames = new ArrayList<>();
        CompilationUnit unit = StaticJavaParser.parse(p);
        class MethodVisitor extends VoidVisitorAdapter<String>{
            @Override
            public void visit(MethodDeclaration n, String arg) {
                methodNames.add(targetClassName.replace("/", ".") + "#" + n.getNameAsString());
                super.visit(n, arg);
            }
        }
        unit.accept(new MethodVisitor(), "");
        return methodNames;
    }

    //targetSrcPathは最後"/"なし
    //targetMethodNameはdemo.SortTestのように記述
    //返り値はmap: targetMethodName ex.) demo.SortTest#test1 --> Pair(start, end)
    public static Map<String, Pair<Integer, Integer>> getRangeOfMethods(String targetSrcPath, String targetClassName) throws IOException {
        Map<String, Pair<Integer, Integer>> rangeOfMethod = new HashMap<>();
        String targetJavaPath = targetSrcPath + "/" + targetClassName.replace(".", "/") + ".java";
        Path p = Paths.get(targetJavaPath);
        CompilationUnit unit = StaticJavaParser.parse(p);
        class MethodVisitor extends VoidVisitorAdapter<String>{
            @Override
            public void visit(MethodDeclaration n, String arg) {
                rangeOfMethod.put(targetClassName.replace("/", ".")  + "#" + n.getNameAsString(), Pair.of(n.getBegin().get().line, n.getEnd().get().line));
                super.visit(n, arg);
            }
        }
        unit.accept(new MethodVisitor(), "");
        return rangeOfMethod;
    }

}

