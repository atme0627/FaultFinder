package jisd.fl.probe;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import jisd.debug.Debugger;
import jisd.debug.Location;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.StaticAnalyzer;
import jisd.fl.util.TestUtil;
import jisd.info.ClassInfo;
import jisd.info.StaticInfoFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class AbstractProbe {

    Debugger dbg;
    StaticInfoFactory sif;
    FailedAssertInfo assertInfo;

    public AbstractProbe(FailedAssertInfo assertInfo) {
        this.dbg = TestUtil.testDebuggerFactory(assertInfo.getTestClassName(), assertInfo.getTestMethodName());
        this.assertInfo = assertInfo;
    }

    public abstract ProbeResult run(int sleepTime) throws IOException;
    //probe.runで出力された行のパースを行い
    //probe対象のメソッドを返す
    //メソッドが存在しない場合、"#" + assertStmtの形式の要素を1つ持つListを返す
    public List<String> probeLineParser(int probeLine) {
        List<String> probeTargetMethods = new ArrayList<>();
        ClassInfo ci = sif.createClass(assertInfo.getTestClassName());
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
        try {
            typeNameWithPackage = StaticAnalyzer.getClassNameWithPackage(targetSrcDir, typeName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

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

    protected void printWatchedValues(List<ProbeInfo> watchedValues){
        for(ProbeInfo values : watchedValues){
            LocalDateTime createAt = values.createAt;
            Location loc = values.loc;
            String value = values.value;
            System.out.println("CreateAt: " + createAt + " Line: " + loc.getLineNumber() + " value: " + value);
        }
    }

    protected static class ProbeInfo implements Comparable<Probe.ProbeInfo>{
        LocalDateTime createAt;
        Location loc;
        String value;

        ProbeInfo(LocalDateTime createAt,
                    Location loc,
                  String value){
            this.createAt = createAt;
            this.loc = loc;
            this.value = value;
        }

        @Override
        public int compareTo(ProbeInfo o) {
            return createAt.compareTo(o.createAt);
        }
    }
}
