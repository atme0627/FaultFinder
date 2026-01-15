package jisd.fl.util.analyze;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.infra.javaparser.TmpJavaParserUtils;

import java.nio.file.NoSuchFileException;
import java.util.List;

public class JavaParserUtil {
    static {
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21); // または JAVA_16/21 など
        StaticJavaParser.setConfiguration(config);
    }

    public static List<CallableDeclaration> extractCallableDeclaration(MethodElementName targetClass) throws NoSuchFileException {
        return extractNode(targetClass, CallableDeclaration.class);
    }

    public static BlockStmt extractBodyOfMethod(MethodElementName targetMethod) throws NoSuchFileException {
        return TmpJavaParserUtils.extractBodyOfMethod(targetMethod);
    }

    public static <T extends Node> List<T> extractNode(MethodElementName targetClass, Class<T> nodeClass) throws NoSuchFileException {
        return TmpJavaParserUtils.parseClass(targetClass)
                .findAll(nodeClass);
    }

}
