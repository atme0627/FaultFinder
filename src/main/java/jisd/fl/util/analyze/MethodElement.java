package jisd.fl.util.analyze;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.nio.file.NoSuchFileException;
import java.util.List;

//JavaParserのCallableDeclarationを保持し、メソッド内の情報を得るためのメソッドを加えたクラス
//constructorも含む
public class MethodElement {
    protected final CallableDeclaration cd;
    private final MethodElementName ce;

    public MethodElement(CallableDeclaration cd){
        this.cd = cd;
        this.ce = new MethodElementName(cd);
    }

    public MethodElementName name(){
        return ce;
    }
    //指定されたローカル変数のvariableDeclaratorを返す
    //指定された名前の変数が存在しない場合はnull
    //同一メソッド内にスコープが異なる同名の変数がある状況は未想定
    public List<VariableDeclarator> findLocalVarDeclaration(String localVarName){
        BlockStmt bs = extractBodyOfMethod();
        List<VariableDeclarator> vds = bs.findAll(VariableDeclarator.class);
        return vds.stream()
                .filter(vd -> vd.getNameAsString().equals(localVarName))
                .toList();
    }

    private BlockStmt extractBodyOfMethod() {
        return cd.isMethodDeclaration() ?
                cd.asMethodDeclaration().getBody().orElseThrow() :
                cd.asConstructorDeclaration().getBody();
    }

    public static MethodElement getMethodElementByName(MethodElementName methodName) throws NoSuchFileException {
        return new MethodElement(JavaParserUtil.getCallableDeclarationByName(methodName));
    }


}