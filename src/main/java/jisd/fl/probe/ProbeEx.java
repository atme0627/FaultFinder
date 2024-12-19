package jisd.fl.probe;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.util.JavaParserUtil;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.StaticAnalyzer;
import org.apache.commons.lang3.tuple.Pair;

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
        return null;
    }

    //次のprobe対象のVariableInfoを返す
    public List<VariableInfo> searchNextProbeTargets(ProbeResult pr) {
        List<VariableInfo> vis = new ArrayList<>();
        if(pr.isArgument()){
            String argVariable = getArgumentVariable(pr);
            VariableInfo vi = new VariableInfo(
                    pr.getProbeMethod(),
                    argVariable,
                    pr.getVariableInfo().getVariableType(),
                    pr.getVariableInfo().isField(),
                    pr.getVariableInfo().getArrayNth(),
                    pr.getVariableInfo().getTargetField()
                    );
            vis.add(vi);
        }
        else {

        }
        return vis;
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
    Set<String> getVariableFromProbeLine(String probeLine){
        Statement stmt = StaticJavaParser.parseStatement(probeLine);
        return null;
    }

    Set<String> getLocalVariables(String targetMethod){
        Set<String> lvs = new HashSet<>();
        MethodDeclaration md;
        try {
            md = JavaParserUtil.parseMethod(targetMethod);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        List<VariableDeclarator> vds = md.findAll(VariableDeclarator.class);
        for(VariableDeclarator vd : vds){
            lvs.add(vd.getNameAsString());
        }
        return lvs;
    }
}
