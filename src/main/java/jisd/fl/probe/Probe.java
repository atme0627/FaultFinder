package jisd.fl.probe;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import com.sun.jdi.VMDisconnectedException;
import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import jisd.debug.Point;
import jisd.debug.value.ValueInfo;
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
public class Probe {
    Debugger dbg;
    StaticInfoFactory sif;
    FailedAssertInfo assertInfo;

    public Probe(Debugger dbg, FailedAssertInfo assertInfo) {
        this.dbg = dbg;
        this.assertInfo = assertInfo;
        this.sif = new StaticInfoFactory(assertInfo.getSrcDir(), assertInfo.getBinDir());
    }

    //呼び出し関係は考えていないバージョン
    //そのメソッド内だけ検索する
    public ArrayList<Integer> getLineWithVar() {
        ClassInfo ci = sif.createClass(assertInfo.getTestClassName());
        MethodInfo mi = ci.method(assertInfo.getTestMethodName());
        LocalInfo li = mi.local(assertInfo.getVariableName());
        return li.canSet();
    }

    //assert文から遡って、最後に変数が目的の条件を満たしている行で呼び出しているメソッドを返す。
    public List<String> run(int sleepTime) {
        ArrayList<Integer> lineWithVar = getLineWithVar();
        ArrayList<Optional<Point>> watchPoints = new ArrayList<>();
        ArrayList<Optional<DebugResult>> results = new ArrayList<>();
        String[] varName = {assertInfo.getVariableName()};

        dbg.setMain(assertInfo.getTestClassName());
        //run program
        for (int line : lineWithVar) {
            watchPoints.add(dbg.watch(line, varName));
        }

        try {
            dbg.run(sleepTime);
        } catch (VMDisconnectedException ignored) {
        }

        dbg.exit();

        //get debugResult
        for (Optional<Point> op : watchPoints) {
            Point p;
            if (op.isPresent()) {
                p = op.get();
            } else {
                throw new NoSuchElementException("There is no information in a watchPoint.");
            }
            results.add(p.getResults(assertInfo.getVariableName()));
        }

        //probe
        Collections.reverse(results);
        int probeLine = -1;
        for (Optional<DebugResult> odr : results) {
            DebugResult dr = null;
            if (odr.isPresent()) {
                dr = odr.get();
            } else {
                throw new NoSuchElementException("There is no information in a DebugResult.");
            }

            //値がセットされていない場合はスキップ
            ValueInfo vi = null;
            try {
                vi = dr.lv();
            } catch (RuntimeException e) {
                continue;
            }

            if (assertInfo.eval(vi.getValue())) {
                probeLine = dr.getLocation().getLineNumber();
                //System.out.println("probeLine: " + probeLine);
            }
        }

        if (probeLine == -1) {
            throw new RuntimeException("No matching rows found.");
        } else {
            //probeLineにいるときにはまだその行は実行されていない
            return probeLineParser(probeLine - 1);
        }
    }

    //probe.runで出力された行のパースを行い
    //probe対象のメソッドを返す
    //
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
}
