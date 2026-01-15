package jisd.fl.util.analyze;

import com.github.javaparser.Range;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import jisd.fl.core.entity.MethodElementName;

import java.nio.file.*;
import java.util.*;

public class StaticAnalyzer {

    public static Map<Integer, MethodElementName> getMethodNamesWithLine(MethodElementName targetClass) throws NoSuchFileException {
        Map<Integer, MethodElementName> result = new HashMap<>();
        for(CallableDeclaration cd : JavaParserUtil.extractCallableDeclaration(targetClass)){
            Range methodRange = cd.getRange().get();
            for(int line = methodRange.begin.line; line <= methodRange.end.line; line++){
                result.put(line, new MethodElementName(targetClass.getFullyQualifiedClassName() + "#" + cd.getSignature()));
            }
        }
        return result;
    }


    //あるメソッド内の特定の変数の定義行の番号を取得する。
    public static List<Integer> findLocalVariableDeclarationLine(MethodElementName targetMethod, String localVarName){
        List<Integer> result;
        try {
            BlockStmt bs = JavaParserUtil.extractBodyOfMethod(targetMethod);
            List<VariableDeclarator> vds = bs.findAll(VariableDeclarator.class);
            result = vds.stream()
                    .filter(vd1 -> vd1.getNameAsString().equals(localVarName))
                    .map(vd -> vd.getRange().get().begin.line)
                    .toList();
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}

