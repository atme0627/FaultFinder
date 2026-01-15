package jisd.fl.util.analyze;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.infra.javaparser.TmpJavaParserUtils;

import java.nio.file.NoSuchFileException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class JavaParserUtil {
    static {
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21); // または JAVA_16/21 など
        StaticJavaParser.setConfiguration(config);
    }



    public static List<CallableDeclaration> extractCallableDeclaration(MethodElementName targetClass) throws NoSuchFileException {
        return extractNode(targetClass, CallableDeclaration.class);
    }

    public static List<VariableDeclarator> extractVariableDeclarator(MethodElementName targetClass) throws NoSuchFileException {
        return extractNode(targetClass, VariableDeclarator.class);
    }

    public static BlockStmt extractBodyOfMethod(MethodElementName targetMethod) throws NoSuchFileException {
        return TmpJavaParserUtils.extractBodyOfMethod(targetMethod);
    }

    public static <T extends Node> List<T> extractNode(MethodElementName targetClass, Class<T> nodeClass) throws NoSuchFileException {
        return TmpJavaParserUtils.parseClass(targetClass)
                .findAll(nodeClass);
    }

    public static Optional<CallableDeclaration> getCallableDeclarationByLine(MethodElementName targetClass, int line) throws NoSuchFileException {
        return extractNode(targetClass, CallableDeclaration.class)
                .stream()
                .filter(stmt -> stmt.getRange().isPresent())
                .filter(stmt -> (stmt.getRange().get().begin.line <= line))
                .filter(stmt -> (stmt.getRange().get().begin.line >= line))
                .min(Comparator.comparingInt(stmt -> stmt.getRange().get().getLineCount()));
    }
}
