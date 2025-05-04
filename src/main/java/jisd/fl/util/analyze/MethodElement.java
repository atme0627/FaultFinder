package jisd.fl.util.analyze;

import com.github.javaparser.ast.body.MethodDeclaration;

//JavaParserのCompilationUnitを保持し、クラス内の情報を得るためのメソッドを加えたクラス
public class MethodElement {
    private final MethodDeclaration md;

    public MethodElement(MethodDeclaration md){
        this.md = md;
    }
}