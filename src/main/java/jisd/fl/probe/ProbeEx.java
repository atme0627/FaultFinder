package jisd.fl.probe;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.StaticAnalyzer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

//値のStringを比較して一致かどうかを判定
//理想的には、"==" と同じ方法で判定したいが、型の問題で難しそう
public class ProbeEx extends AbstractProbe{
    static String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");
    static Set<String> allMethods = StaticAnalyzer.getAllMethods(targetSrcDir, false, false);

    public ProbeEx(FailedAssertInfo assertInfo) {
        super(assertInfo);
    }

    //assert文から遡って、最後に変数が目的の条件を満たしている行で呼び出しているメソッドを返す。
    public ProbeResult run(int sleepTime) {
        return null;
    }

    //次のprobe対象のメソッドを返す
    public List<String> probeStatementParser(ProbeResult pr) {

        List<String> markingMethods = new ArrayList<>();
        //parse statement
        //assertStmtは一つの代入文のみがある前提
        Statement probeStmt = StaticJavaParser.parseStatement(pr.getSrc());

        //MethodCallExprからsimpleNameを取り出し、className#methodNameの形に
        List<MethodCallExpr> methodCallExprs = probeStmt.findAll(MethodCallExpr.class);

        //メソッドがない場合は終了
        if(methodCallExprs.isEmpty()) return null;
        //メソッド外部APIでないものを抽出する。
        methodCallExprs = removeExternalApiMethod(methodCallExprs);

        for (MethodCallExpr mce : methodCallExprs) {
            Optional<Expression> exp = mce.getScope();
            String probeTargetClass;
            if (!exp.isEmpty()) {
            //ex.) X.add(a, b);
                probeTargetClass = getClassNameFromMethodCall(exp.get().toString(), assertInfo.getTestClassName(), assertInfo.getTestMethodName());
                markingMethods.add(probeTargetClass + "#" + mce.getName());
            }
        }

        markingMethods.add(pr.getProbeMethod());
        return markingMethods;
    }

    public List<MethodCallExpr> removeExternalApiMethod(List<MethodCallExpr> mces){
        List<MethodCallExpr> filteredMethods = new ArrayList<>();
        for(MethodCallExpr mce : mces){
            String methodName = mce.getNameAsString();
            if(allMethods.contains(methodName)) filteredMethods.add(mce);
        }
        return filteredMethods;
    }

    //sibling method の中から探す
    private String getClassNameFromMethodCall(String methodCall, String testClassName, String testMethodName) {
        String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");
        String testSrcDir = PropertyLoader.getProperty("d4jTestSrcDir");
        //argと()を消す
        int argPlace = methodCall.lastIndexOf('(');
        if (argPlace == -1) argPlace = methodCall.length();
        String[] methodCallElements = methodCall.substring(0, argPlace).split("\\.");

        String p = testSrcDir + "/" + testClassName.replace(".", "/") + ".java";
        Path source = Paths.get(p);

        CompilationUnit unit = null;
        try {
            unit = StaticJavaParser.parse(source);
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }

        //MethodNameのAのクラスを調べる
        String typeName = unit.accept(new GenericVisitorAdapter<>() {
            @Override
            public String visit(VariableDeclarator vd, String variableName) {
                if (vd.getNameAsString().equals(variableName)) {
                    return vd.getTypeAsString();
                }
                return null;
            }
        }, methodCallElements[0]);

        if (typeName == null) {
            throw new RuntimeException("Probe#getClassNameFromMethodCall\n" +
                    "Cannot find type of variable: " + methodCallElements[0]);
        }

        String typeNameWithPackage;
        typeNameWithPackage = StaticAnalyzer.getClassNameWithPackage(targetSrcDir, typeName);

//        //B以降
//        //X.Y or X.Y()に対し、Xのフィールド、メソッドにYが含まれていることを確認し、Yの型を返す。
//        String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");
//        unit = StaticJavaParser.parse(targetSrcDir + "/" + typeNameWithPackage.replace(".", "/") + ".java");
//
//        String element = methodCallElements[1];
//        String newTypeName;
//        //method
//        if (element.endsWith("()")) {
//            newTypeName = unit.accept(new GenericVisitorAdapter<>() {
//                @Override
//                public String visit(MethodDeclaration md, String methodName) {
//                    if ((md.getNameAsString() + "()").equals(methodName)) {
//                        return md.getTypeAsString();
//                    }
//                    return null;
//                }
//            }, element);
//        }
//        //field
//        else {
//            newTypeName = unit.accept(new GenericVisitorAdapter<>() {
//                @Override
//                public String visit(VariableDeclarator vd, String variableName) {
//                    if (vd.getNameAsString().equals(variableName)) {
//                        return vd.getTypeAsString();
//                    }
//                    return null;
//                }
//            }, element);
//        }
//
//        if (newTypeName == null) {
//            throw new RuntimeException("Probe#getClassNameFromMethodCall\n" +
//                    "Cannot find type of variable: " + element);
//        }
//
//        String newTypeNameWithPackage;
//        try {
//            newTypeNameWithPackage = StaticAnalyzer.getClassNameWithPackage(, newTypeName);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

        return typeNameWithPackage;
    }


}
