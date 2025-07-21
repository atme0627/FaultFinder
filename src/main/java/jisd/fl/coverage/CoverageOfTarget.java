package jisd.fl.coverage;

import com.fasterxml.jackson.annotation.JsonCreator;
import jisd.fl.sbfl.SbflStatus;
import jisd.fl.util.analyze.CodeElementName;
import jisd.fl.util.analyze.LineElementName;
import jisd.fl.util.analyze.MethodElementName;
import jisd.fl.util.analyze.StaticAnalyzer;
import org.apache.commons.lang3.StringUtils;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.IMethodCoverage;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.stream.Collectors;

public class CoverageOfTarget {
    public String targetClassName;

    //各行のカバレッジ情報
    public Map<CodeElementName, SbflStatus> lineCoverage;
    public Map<CodeElementName, SbflStatus> methodCoverage;
    public Map<CodeElementName, SbflStatus> classCoverage;

    //行 --> LineElementName
    private Map<Integer, LineElementName> lineElementNames;

    @JsonCreator
    public CoverageOfTarget(){
    }

    public CoverageOfTarget(String targetClassName) {
        this.targetClassName = targetClassName;

        lineCoverage = new TreeMap<>();
        classCoverage = new TreeMap<>();
        methodCoverage = new TreeMap<>();

        try {
            lineElementNames = StaticAnalyzer.getMethodNamesWithLine(new MethodElementName(targetClassName));
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
    }

    public void processCoverage(IClassCoverage cc, boolean isTestPassed) {
        int targetClassFirstLine = cc.getFirstLine();
        int targetClassLastLine = cc.getLastLine();

        //line coverage
        for(int i = targetClassFirstLine; i <= targetClassLastLine; i++){
            if(cc.getLine(i).getStatus() == ICounter.EMPTY) continue;
            boolean isTestExecuted = !(cc.getLine(i).getStatus() == ICounter.NOT_COVERED);

            putCoverageStatus(getLineElementNameFromLine(i), new SbflStatus(isTestExecuted , isTestPassed), Granularity.LINE);
        }

        //method coverage
        for(IMethodCoverage mc : cc.getMethods()){
            boolean isTestExecuted = mc.getMethodCounter().getCoveredCount() == 1;
            String methodSignature = Arrays.stream(Type.getArgumentTypes(mc.getDesc()))
                    .map(Type::getClassName)
                    .collect(Collectors.joining(", "));
            String targetMethodName = targetClassName + "#" + mc.getName() + "(" + methodSignature + ")";
            MethodElementName ce = new MethodElementName(targetMethodName);
            putCoverageStatus(ce, new SbflStatus(isTestExecuted, isTestPassed), Granularity.METHOD);
        }

        //class coverage
        MethodElementName ce = new MethodElementName(targetClassName);
        putCoverageStatus(ce, getClassSbflStatus(cc, isTestPassed), Granularity.CLASS);
    }

    protected void putCoverageStatus(CodeElementName element, SbflStatus status, Granularity granularity) {
        switch (granularity) {
            case LINE:
                lineCoverage.put(element, status);
                break;
            case METHOD:
                methodCoverage.put(element, status);
                break;
            case CLASS:
                classCoverage.put(element, status);
                break;
        }
    }

    protected SbflStatus getClassSbflStatus(IClassCoverage cc, boolean isTestPassed){
        int classBegin = cc.getFirstLine();
        int classEnd = cc.getLastLine();
        boolean isTestExecuted = false;
        for(int i = classBegin; i <= classEnd; i++){
            int status = cc.getLine(i).getStatus();
            if(status == ICounter.PARTLY_COVERED || status == ICounter.FULLY_COVERED) {
                isTestExecuted = true;
                break;
            }
        }
        return new SbflStatus(isTestExecuted, isTestPassed);
    }


    public Map<CodeElementName, SbflStatus> getCoverage(Granularity granularity){
        return switch (granularity) {
            case LINE -> lineCoverage;
            case METHOD -> methodCoverage;
            case CLASS -> classCoverage;
        };
    }

    public String getTargetClassName() {
        return targetClassName;
    }

    public void printCoverage(PrintStream out, Granularity granularity) {
        switch (granularity) {
            case CLASS:
                printClassCoverage(out);
                break;
            case METHOD:
                printMethodCoverage(out);
                break;
            case LINE:
                printLineCoverage(out);
                break;
        }
    }

    private void printClassCoverage(PrintStream out){
        classCoverage.forEach((name, s) -> {
            out.println("|  " + StringUtils.leftPad(name.toString(), 100) +
                    " | " + StringUtils.leftPad(String.valueOf(s.ep), 4) +
                    " | " + StringUtils.leftPad(String.valueOf(s.ef), 4) +
                    " | " + StringUtils.leftPad(String.valueOf(s.np), 4) +
                    " | " + StringUtils.leftPad(String.valueOf(s.nf), 4) + " |");
        });
    }

    private void printMethodCoverage(PrintStream out){
        int nameLength = maxLengthOfName(methodCoverage, true);
        out.println("[TARGET: " + targetClassName + "]");
        String header = "| " + StringUtils.repeat(' ', nameLength - "METHOD NAME".length()) + " METHOD NAME " +
                        "|  EP  |  EF  |  NP  |  NF  |";
        String partition = StringUtils.repeat('=', header.length());

        out.println(partition);
        out.println(header);
        out.println(partition);
        methodCoverage.forEach((name, s) -> {
                    out.println("|  " + StringUtils.leftPad(name.getShortMethodName(), nameLength) +
                                      " | " + StringUtils.leftPad(String.valueOf(s.ep), 4) +
                                      " | " + StringUtils.leftPad(String.valueOf(s.ef), 4) +
                                      " | " + StringUtils.leftPad(String.valueOf(s.np), 4) +
                                      " | " + StringUtils.leftPad(String.valueOf(s.nf), 4) + " |");
                });
        out.println(partition);
        out.println();
    }

    private void printLineCoverage(PrintStream out){
        out.println("[TARGET: " + targetClassName + "]");
        String header = "| LINE ||  EP  |  EF  |  NP  |  NF  |";
        String partition = StringUtils.repeat('=', header.length());

        out.println(partition);
        out.println(header);
        out.println(partition);

        lineCoverage.forEach((line, s) -> {
            out.println( String.format("| %s |", line.compressedShortMethodName())+
                    "| " + StringUtils.leftPad(String.valueOf(s.ep), 4) +
                    " | " + StringUtils.leftPad(String.valueOf(s.ef), 4) +
                    " | " + StringUtils.leftPad(String.valueOf(s.np), 4) +
                    " | " + StringUtils.leftPad(String.valueOf(s.nf), 4) + " |");
        });

        out.println(partition);
        out.println();
    }

    void combineCoverages(CoverageOfTarget cov){
        this.lineCoverage = combineCoverage(this.lineCoverage, cov.lineCoverage);
        this.methodCoverage = combineCoverage(this.methodCoverage,  cov.methodCoverage);
        this.classCoverage = combineCoverage(this.classCoverage, cov.classCoverage);
    }


    private Map<CodeElementName, SbflStatus> combineCoverage(Map<CodeElementName, SbflStatus> thisCov, Map<CodeElementName, SbflStatus> otherCov){
        Map<CodeElementName, SbflStatus> newCoverage = new TreeMap<>(otherCov);
        thisCov.forEach((k,v)->{
            if(newCoverage.containsKey(k)){
                newCoverage.put(k, v.combine(newCoverage.get(k)));
            }
            else {
                newCoverage.put(k, v);
            }
        });
        return newCoverage;
    }

    private List<String> getSortedKeys(Set<String> keyset, Granularity granularity){
        ArrayList<String> keys =  new ArrayList<>(keyset);
        if(granularity == Granularity.LINE){
            //行数のStringをソートするための処理
            keys.sort((o1, o2) -> Integer.parseInt(o1) - Integer.parseInt(o2));
        }
        else {
            Collections.sort(keys);
        }
        return keys;
    }

    private int maxLengthOfName(Map<CodeElementName, SbflStatus> cov, boolean isMethod){
        int maxLength = 0;
        for(CodeElementName name : cov.keySet()){
            int l = (isMethod) ? name.getShortMethodName().length() : name.toString().length();
            maxLength = Math.max(maxLength, l);
        }
        return maxLength;
    }

    private LineElementName getLineElementNameFromLine(int line){
        LineElementName result = lineElementNames.get(line);
        if(result == null) {
            result = new LineElementName(targetClassName + "#<init>", line);
        }
        return result;
    }
}
