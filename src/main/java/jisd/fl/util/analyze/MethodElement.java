package jisd.fl.util.analyze;

import com.github.javaparser.Range;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.probe.ProbeResult;

import java.nio.file.NoSuchFileException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

//JavaParserのCallableDeclarationを保持し、メソッド内の情報を得るためのメソッドを加えたクラス
//constructorも含む
public class MethodElement {
    private final CallableDeclaration cd;
    private final CodeElementName ce;

    public MethodElement(CallableDeclaration cd){
        this.cd = cd;
        this.ce = new CodeElementName(cd);
    }

    public String fqmn(){
        return ce.getFullyQualifiedMethodName();
    }

    public CodeElementName name(){
        return ce;
    }
    //指定されたローカル変数のvariableDeclaratorを返す
    //指定された名前の変数が存在しない場合はnull
    //同一メソッド内にスコープが異なる同盟の変数がある状況は未想定
    public Optional<VariableDeclarator> findLocalVarDeclaration(String localVarName){
        BlockStmt bs = extractBodyOfMethod();
        List<VariableDeclarator> vds = bs.findAll(VariableDeclarator.class);
        return vds.stream()
                .filter(vd -> vd.getNameAsString().equals(localVarName))
                .findFirst();
    }

    //指定された行を含むStatementElementを返す
    //指定された行を含むStatementが存在しない場合はnull
    public Optional<StatementElement> findStatementByLine(int line){
        BlockStmt body = extractBodyOfMethod();
        return body.findAll(Statement.class).stream()
                .filter(stmt -> stmt.getRange().isPresent())
                .filter(stmt -> {
                    Range r = stmt.getRange().get();
                    return r.begin.line <= line && line <= r.end.line;
                })
                // 最も狭い範囲（最も深い）のstatementを返す
                .min(Comparator.comparingInt(stmt -> {
                    Range r = stmt.getRange().get();
                    // 範囲サイズを計算（行数差×1000 + 列数差）
                    return (r.end.line - r.begin.line) * 1000 + (r.end.column - r.begin.column);
                }))
                .map(StatementElement::new);
    }

    private BlockStmt extractBodyOfMethod() {
        return cd.isMethodDeclaration() ?
                cd.asMethodDeclaration().getBody().orElseThrow() :
                cd.asConstructorDeclaration().getBody();
    }

    public static MethodElement getMethodElementByName(CodeElementName methodName) throws NoSuchFileException {
        return new MethodElement(JavaParserUtil.getCallableDeclarationByName(methodName));
    }

    //argNameが引数の何番目か返す
    public int getIndexOfArgument(String argName){
        NodeList<Parameter> prms = cd.getParameters();
        for(int i = 0; i < prms.size(); i++){
            Parameter prm =  prms.get(i);
            if(prm.getName().toString().equals(argName)){
                return i;
            }
        }
        throw new RuntimeException("parameter [" + argName + "] is not found.");
    }


    //指定された行での指定されたメソッドの呼び出しに対して、そのindex番目(0-indexed)の引数を返す
    //メソッド呼び出し、コンストラクタ呼び出し、this()呼び出しの順に探索
    //同名のメソッドが複数含まれている場合は考慮しない
    public Expression extractArgumentOfMethodExpr(CodeElementName targetCalleeMethod, int line, int index){
        Statement targetStmt = findStatementByLine(line).orElseThrow().statement();

        NodeWithArguments targetExpr;
        MethodCallExpr methodCall = targetStmt.findAll(MethodCallExpr.class)
                .stream()
                .filter(mce -> mce.getNameAsString().equals(targetCalleeMethod.getShortMethodName()))
                .findFirst()
                .orElse(null);
        if(methodCall != null) targetExpr = methodCall;
        else {
            ObjectCreationExpr objectCreation = targetStmt.findAll(ObjectCreationExpr.class)
                    .stream()
                    .filter(oc -> oc.getType().getNameAsString().equals(targetCalleeMethod.getShortMethodName()))
                    .findFirst()
                    .orElse(null);
            targetExpr = objectCreation;
        }

        if(targetExpr != null && targetExpr.getArguments().size() > index){
            Expression expr = targetExpr.getArgument(index);
            if (expr.isCastExpr()) {
                expr = expr.asCastExpr().getExpression();
            }
            return expr;
        }

        ExplicitConstructorInvocationStmt explicitInvocation
                = targetStmt.findAll(ExplicitConstructorInvocationStmt.class)
                .stream()
                .findFirst()
                .orElse(null);

        if(explicitInvocation != null && explicitInvocation.getArguments().size() > index){
            Expression expr = explicitInvocation.getArgument(index);
            if (expr.isCastExpr()) {
                expr = expr.asCastExpr().getExpression();
            }
            return expr;
        }

        throw new RuntimeException(index + " -th argument in " + targetCalleeMethod + "(at line:" + line + ") is not found.");
    }
}