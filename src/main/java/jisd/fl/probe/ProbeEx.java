package jisd.fl.probe;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.util.JavaParserUtil;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.StaticAnalyzer;
import org.apache.commons.lang3.tuple.Pair;

import java.io.StringBufferInputStream;
import java.nio.file.NoSuchFileException;
import java.util.*;

//値のStringを比較して一致かどうかを判定
//理想的には、"==" と同じ方法で判定したいが、型の問題で難しそう
public class ProbeEx extends AbstractProbe {
    static String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");
    static Set<String> allMethods;

    public ProbeEx(FailedAssertInfo assertInfo) {
        super(assertInfo);
        try {
            allMethods = StaticAnalyzer.getAllMethods(targetSrcDir, false, false);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
    }

    public ProbeResult run(int sleepTime) {
        return probing(sleepTime, assertInfo.getVariableInfo());
    }

    //次のprobe対象のVariableInfoを返す
    public List<VariableInfo> searchNextProbeTargets(ProbeResult pr) {
        List<VariableInfo> vis = new ArrayList<>();
        //感染した変数が引数のものだった場合
        if(pr.isArgument()){
            String argVariable = getArgumentVariable(pr);
            VariableInfo vi = new VariableInfo(
                    pr.getProbeMethod(),
                    argVariable,
                    pr.getVariableInfo().isPrimitive(),
                    isFieldVariable(argVariable, pr.getProbeMethod()),
                    pr.getVariableInfo().isArray(),
                    pr.getVariableInfo().getArrayNth(),
                    pr.getVariableInfo().getActualValue(),
                    pr.getVariableInfo().getTargetField()
                    );
            vis.add(vi);
        }
        else {
            Set<String> neighborVariables = getNeighborVariables(pr);
            for(String n : neighborVariables){
                String variableName = n;
                boolean isArray;
                boolean isField = false;
                int arrayNth;
                //フィールドかどうか判定
                if(n.contains("this.")){
                    isField = true;
                    variableName = n.substring("this.".length());
                }
                //配列かどうか判定
                if(n.contains("[")){
                    variableName = variableName.split("\\[")[0];
                    arrayNth = Integer.parseInt(n.substring(n.indexOf("[") + 1, n.indexOf("]")));
                    isArray = true;
                }
                else {
                    arrayNth = -1;
                    isArray = false;
                }

                VariableInfo vi = new VariableInfo(
                        pr.getProbeMethod(),
                        variableName,
                        true,
                        isField,
                        isArray,
                        arrayNth,
                        pr.getValuesInLine().get(n),
                        null
                );
                vis.add(vi);
            }
        }
        return vis;
    }

    private boolean isFieldVariable(String variable, String targetMethod){
        String targetClass = targetMethod.split("#")[0];
        MethodDeclaration md;
        CompilationUnit unit;
        try {
            md = JavaParserUtil.parseMethod(targetMethod);
            unit = JavaParserUtil.parseClass(targetClass);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        //method内で定義されたローカル変数の場合
        List<VariableDeclarator> vds = md.findAll(VariableDeclarator.class);
        for(VariableDeclarator vd : vds){
            if(vd.getName().toString().equals(variable)){
                return false;
            }
        }

        //fieldの場合
        List<FieldDeclaration> fds = unit.findAll(FieldDeclaration.class);
        vds = new ArrayList<>();
        for(FieldDeclaration fd : fds){
            vds.addAll(fd.getVariables());
        }

        for(VariableDeclarator vd : vds){
            if(vd.getName().toString().equals(variable)){
                return true;
            }
        }

        throw new RuntimeException("Variable \"" + variable + "\" is not found in " + targetClass);
    }

    //probeLine中で使われている変数群を返す
    private Set<String> getNeighborVariables(ProbeResult pr){
        Set<String> neighbor = new HashSet<>();
        Set<String> watched = pr.getValuesInLine().keySet();
        Statement stmt = StaticJavaParser.parseStatement(pr.getSrc());
        List<SimpleName> variableNames = stmt.findAll(SimpleName.class);
        variableNames.forEach((v) -> {
            for(String w : watched) {
                //プリミティブ型の場合
                if (w.equals(v.toString()) || w.equals("this." + v)) {
                    neighbor.add(w);
                }

                if(!w.contains("[")) continue;
                String withoutArray = w.substring(0, w.indexOf("["));
                //配列の場合
                if (withoutArray.contains(v.toString()) || withoutArray.contains("this." + v)) {
                    neighbor.add(w);
                }
            }
        });
        return neighbor;
    }

    //メソッド呼び出しで使われた変数名を返す
    private String getArgumentVariable(ProbeResult pr){
        String locateClass = pr.getProbeMethod().split("#")[0];
        Pair<Integer, String> callerNameAndCallLocation = getCallerMethod(pr.getProbeLines(), locateClass);
        int index = getIndexOfArgument(pr);
        int line = callerNameAndCallLocation.getLeft();
        String locateMethod = callerNameAndCallLocation.getRight();

        class BlockStmtVisitor extends GenericVisitorAdapter<String, Integer> {
            @Override
            public String visit(final MethodCallExpr n, final Integer line) {
                if(n.getBegin().get().line == line){
                    return n.getArgument(index).toString();
                }
                return super.visit(n, line);
            }
        }

        BlockStmt bs;
        try {
            MethodDeclaration md = JavaParserUtil.parseMethod(locateMethod);
            bs = md.getBody().get();
        } catch (NoSuchFileException e) {
            //targetMethodがコンストラクタの場合
            try {
                ConstructorDeclaration cd = JavaParserUtil.parseConstructor(locateMethod);
                bs = cd.getBody();
            } catch (NoSuchFileException ex) {
                throw new RuntimeException(ex);
            }
        }

        return bs.accept(new BlockStmtVisitor(), line);
    }


    private int getIndexOfArgument(ProbeResult pr){
        String targetMethod = pr.getProbeMethod();
        String variable = pr.getVariableInfo().getVariableName();
        int index = -1;
        NodeList<Parameter> prms;
        try {
            MethodDeclaration md = JavaParserUtil.parseMethod(targetMethod);
            prms = md.getParameters();
        } catch (NoSuchFileException e) {
            //targetMethodがコンストラクタの場合
            try {
                ConstructorDeclaration cd = JavaParserUtil.parseConstructor(targetMethod);
                prms = cd.getParameters();
            } catch (NoSuchFileException ex) {
                throw new RuntimeException(ex);
            }
        }

        for(int i = 0; i < prms.size() - 1; i++){
            Parameter prm = prms.get(i);
            if(prm.getName().toString().equals(variable)){
                index = i;
            }
        }

        if(index == -1) throw new RuntimeException("parameter " + variable + " is not found.");
        return index;
    }
}
