package jisd.fl.probe;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.StaticAnalyzer;
import jisd.info.ClassInfo;
import jisd.info.LocalInfo;
import jisd.info.MethodInfo;
import jisd.info.StaticInfoFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

//値のStringを比較して一致かどうかを判定
//理想的には、"==" と同じ方法で判定したいが、型の問題で難しそう
public class ProbeEx extends AbstractProbe{

    public ProbeEx(FailedAssertInfo assertInfo) {
        super(assertInfo);
        String testSrcDir = PropertyLoader.getProperty("testSrcDir");
        String testBinDir = PropertyLoader.getProperty("testBinDir");
        this.targetSif = new StaticInfoFactory(testSrcDir, testBinDir);
    }

    //呼び出し関係は考えていないバージョン
    //そのメソッド内だけ検索する
    @Override
    public List<Integer> getCanSetLine(VariableInfo variableInfo) {
        ClassInfo ci = testSif.createClass(assertInfo.getTestClassName());
        MethodInfo mi = ci.method(assertInfo.getTestMethodName().split("#")[1] + "()");
        LocalInfo li = mi.local(variableInfo.getVariableName());
        return li.canSet();
    }

    //assert文から遡って、最後に変数が目的の条件を満たしている行で呼び出しているメソッドを返す。
    public ProbeResult run(int sleepTime) {
//        VariableInfo variableInfo = assertInfo.getVariableInfo();
//        List<ProbeInfo> watchedValues = extractInfoFromDebugger(variableInfo, sleepTime);
//        printWatchedValues(watchedValues, variableInfo);
//        ProbeResult result = searchProbeLine(watchedValues, );
//        int probeLine = result.getProbeLine();
//        printProbeLine(probeLine, variableInfo);
//        return result;
        return null;
    }

    @Override
    protected ProbeResult searchProbeLine(List<ProbeInfo> watchedValues, List<Integer> assignLine) {
        return null;
    }

    //probe.runで出力された行のパースを行い
    //probe対象のメソッドを返す
    //メソッドが存在しない場合、"#" + assertStmtの形式の要素を1つ持つListを返す
    public List<String> probeLineParser(int probeLine) {
        List<String> probeTargetMethods = new ArrayList<>();
        ClassInfo ci = targetSif.createClass(assertInfo.getTestClassName());
        String[] src = ci.src().split("\\r?\\n|\\r");
        String assertLine = src[probeLine - 1];

        //parse statement
        //assertStmtは一つの代入文のみがある前提
        Statement assertStmt = StaticJavaParser.parseStatement(assertLine);

        //MethodCallExprからsimpleNameを取り出し、className#methodNameの形に
        List<MethodCallExpr> methodCallExprs = assertStmt.findAll(MethodCallExpr.class);

        //メソッドがあるかチェック
        if(methodCallExprs.isEmpty()){
            probeTargetMethods.add("#" + assertStmt.toString());
            return probeTargetMethods;
        }

        for (MethodCallExpr mce : methodCallExprs) {
            Optional<Expression> exp = mce.getScope();
            String probeTargetClass;
            //ex.) add(a, b);
            if (exp.isEmpty()) {
                probeTargetClass = assertInfo.getTestClassName();
            }

            //ex.) X.add(a, b);
            else {
                probeTargetClass = getClassNameFromMethodCall(exp.get().toString(), assertInfo.getTestClassName(), assertInfo.getTestMethodName());
            }

            probeTargetMethods.add(probeTargetClass + "#" + mce.getName());
        }

        return probeTargetMethods;
    }

    //MethodName: A.add のような形式
    //testMethodName: このメソッド呼び出しを行ったテストメソッド ex.) SampleTest#test1
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
