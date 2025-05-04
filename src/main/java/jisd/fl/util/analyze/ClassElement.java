package jisd.fl.util.analyze;

import com.github.javaparser.ast.CompilationUnit;

//JavaParserのCompilationUnitを保持し、クラス内の情報を得るためのメソッドを加えたクラス
public class ClassElement {
    private final CompilationUnit unit;

    public ClassElement(CompilationUnit unit){
        this.unit = unit;
    }
}
