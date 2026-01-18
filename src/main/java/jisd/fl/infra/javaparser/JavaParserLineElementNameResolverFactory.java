package jisd.fl.infra.javaparser;

import com.github.javaparser.Range;
import com.github.javaparser.ast.body.CallableDeclaration;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.LineElementNameResolver;
import jisd.fl.core.entity.element.MethodElementName;

import java.nio.file.NoSuchFileException;

public class JavaParserLineElementNameResolverFactory {
    /**
     * 対象クラスの情報と行数を基にして、LineElementNameResolverインスタンスを生成する。
     *
     * @param targetClass 対象となるクラスを表すClassElementNameオブジェクト
     * @return 生成されたLineElementNameResolverインスタンス
     */
    public static LineElementNameResolver create(ClassElementName targetClass) throws NoSuchFileException {
        // 対象クラスの最大行数
        int lines = JavaParserUtils.parseClass(targetClass).getEnd().get().line;
        MethodElementName clinit = new MethodElementName(targetClass.fullyQualifiedClassName() + "#<clinit>()");
        LineElementNameResolver resolver = new LineElementNameResolver(lines, clinit);
        for(CallableDeclaration<?> cd : JavaParserUtils.extractNode(targetClass, CallableDeclaration.class)){
            if(cd.getRange().isPresent()) continue;
            Range methodRange = cd.getRange().get();
            MethodElementName method = new MethodElementName(targetClass.fullyQualifiedClassName() + "#" + cd.getSignature());
            resolver.putMethodRange(methodRange.begin.line, methodRange.end.line, method);
        }
        return resolver;
    }
}
