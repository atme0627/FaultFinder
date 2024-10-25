package jisd.fl.util;

import com.github.javaparser.ParseResult;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class StaticAnalyzer {
    //targetSrcPathは最後"/"なし
    //TestClassNameはdemo.SortTestのように記述
    public static ArrayList<String> getMethodNames(String targetSrcPath, String TestClassName) throws IOException {
        String targetJavaPath = targetSrcPath + "/" + TestClassName.replace(".", "/") + ".java";
        Path p = Paths.get(targetJavaPath);
        ArrayList<String> methodNames = new ArrayList<>();
        CompilationUnit unit = StaticJavaParser.parse(p);
        class MethodVisitor extends VoidVisitorAdapter<String>{
            @Override
            public void visit(MethodDeclaration n, String arg) {
                methodNames.add(n.getNameAsString());
                super.visit(n, arg);
            }
        }
        unit.accept(new MethodVisitor(), "");
        return methodNames;
    }
}

