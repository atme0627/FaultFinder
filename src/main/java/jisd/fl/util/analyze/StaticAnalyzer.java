package jisd.fl.util.analyze;

import com.github.javaparser.Range;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousVariable;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class StaticAnalyzer {
    //targetClassNameはdemo.SortTestのように記述
    //返り値は demo.SortTest#test1(int a)の形式
    public static Set<String> getMethodNames(MethodElementName targetClass) throws NoSuchFileException {
        return JavaParserUtil
                .extractCallableDeclaration(targetClass)
                .stream()
                .map(cd -> (targetClass.getFullyQualifiedClassName() + "#" + cd.getSignature()))
                .collect(Collectors.toSet());
    }

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

    public static List<Integer> getCanSetLine(SuspiciousVariable suspiciousVariable) {
        if(suspiciousVariable.isField()) {
            return StaticAnalyzer.canSetLineOfClass(suspiciousVariable.getLocateMethodElement(), suspiciousVariable.getSimpleVariableName());
        }
        else {
            return StaticAnalyzer.canSetLineOfMethod(suspiciousVariable.getLocateMethodElement(), suspiciousVariable.getSimpleVariableName());
        }
    }

    public static List<Integer> canSetLineOfClass(MethodElementName targetClass, String variable){
        Set<String> methods;
        List<Integer> canSet = new ArrayList<>();

        try {
            methods = getMethodNames(targetClass);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        methods.stream()
                .map(MethodElementName::new)
                .forEach(e -> canSet.addAll(canSetLineOfMethod(e, variable)));

        return canSet.stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }


    public static List<Integer> canSetLineOfMethod(MethodElementName targetMethod, String variable){
        List<Integer> canSet = new ArrayList<>();
        BlockStmt bs = null;
        try {
            bs = JavaParserUtil.extractBodyOfMethod(targetMethod);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
        //bodyが空の場合がある。
        if(bs == null) return canSet;

        BlockStmt finalBs = bs;
        bs.findAll(SimpleName.class)
                .stream()
                .filter(sn -> sn.getIdentifier().endsWith(variable))
                .forEach(sn -> {
                    for(int i = -2; i <= 2; i++) {
                        if (finalBs.getBegin().get().line < sn.getBegin().get().line + i
                                && sn.getBegin().get().line + i <= finalBs.getEnd().get().line) {
                            canSet.add(sn.getBegin().get().line + i);
                        }
                    }
                });

        return canSet.stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    //あるメソッド内の特定の変数の定義行を取得する。
    public static List<VariableDeclarator> findLocalVarDeclaration(MethodElementName targetMethod, String localVarName){
        try {
            BlockStmt bs = JavaParserUtil.extractBodyOfMethod(targetMethod);
            List<VariableDeclarator> vds = bs.findAll(VariableDeclarator.class);
            return vds.stream()
                    .filter(vd -> vd.getNameAsString().equals(localVarName))
                    .toList();
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

    }
}

