package jisd.fl.infra.javaparser;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import jisd.fl.core.entity.ClassElementName;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousVariable;

import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class JavaParserTraceTargetLineFinder {
    public static List<Integer> traceTargetLineNumbers(SuspiciousVariable suspiciousVariable) {
        if(suspiciousVariable.isField()) {
            return traceLinesOfClass(suspiciousVariable.getLocateMethodElement().classElementName, suspiciousVariable.getSimpleVariableName());
        }
        else {
            return traceLineOfMethod(suspiciousVariable.getLocateMethodElement(), suspiciousVariable.getSimpleVariableName());
        }
    }

    private static List<Integer> traceLinesOfClass(ClassElementName targetClass, String variable){
        Set<String> methods;
        List<Integer> canSet = new ArrayList<>();

        try {
            methods = getMethodNames(targetClass);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        methods.stream()
                .map(MethodElementName::new)
                .forEach(e -> canSet.addAll(traceLineOfMethod(e, variable)));

        return canSet.stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }


    private static List<Integer> traceLineOfMethod(MethodElementName targetMethod, String variable){
        List<Integer> canSet = new ArrayList<>();
        BlockStmt bs = null;
        try {
            bs = JavaParserUtils.extractBodyOfMethod(targetMethod);
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

    //targetClassNameはdemo.SortTestのように記述
    //返り値は demo.SortTest#test1(int a)の形式
    private static Set<String> getMethodNames(ClassElementName targetClass) throws NoSuchFileException {
        return JavaParserUtils.extractNode(targetClass, CallableDeclaration.class)
                .stream()
                .map(cd -> (targetClass.fullyQualifiedClassName() + "#" + cd.getSignature()))
                .collect(Collectors.toSet());
    }
}
