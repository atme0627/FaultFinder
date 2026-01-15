package jisd.fl.util.analyze;

import com.github.javaparser.Range;
import com.github.javaparser.ast.body.CallableDeclaration;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.infra.javaparser.TmpJavaParserUtils;

import java.nio.file.*;
import java.util.*;

public class StaticAnalyzer {

    public static Map<Integer, MethodElementName> getMethodNamesWithLine(MethodElementName targetClass) throws NoSuchFileException {
        Map<Integer, MethodElementName> result = new HashMap<>();
        for(CallableDeclaration cd : TmpJavaParserUtils.extractNode(targetClass, CallableDeclaration.class)){
            Range methodRange = cd.getRange().get();
            for(int line = methodRange.begin.line; line <= methodRange.end.line; line++){
                result.put(line, new MethodElementName(targetClass.getFullyQualifiedClassName() + "#" + cd.getSignature()));
            }
        }
        return result;
    }
}

