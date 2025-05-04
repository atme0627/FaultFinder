package jisd.fl.util.analyze;

import com.github.javaparser.Range;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.nio.file.NoSuchFileException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

//JavaParserのCallableDeclarationを保持し、メソッド内の情報を得るためのメソッドを加えたクラス
//constructorも含む
public class MethodElement {
    private final CallableDeclaration cd;

    public MethodElement(CallableDeclaration cd){
        this.cd = cd;
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
    public Optional<StatementElement> FindStatementByLine(int line){
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
}