package jisd.fl.sbfl.coverage;

import com.fasterxml.jackson.annotation.JsonCreator;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.infra.javaparser.JavaParserUtils;
import jisd.fl.sbfl.SbflStatus;
import jisd.fl.core.entity.element.CodeElementIdentifier;
import jisd.fl.core.entity.element.LineElementName;
import jisd.fl.core.entity.element.MethodElementName;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.IMethodCoverage;

import java.io.PrintStream;
import java.nio.file.NoSuchFileException;
import java.util.*;

//各クラスごとにインスタンスは一つのみ
public class CoverageOfTarget {
    public String targetClassName;
    //各行のカバレッジ情報
    public Map<CodeElementIdentifier<?>, SbflStatus> lineCoverage;
    public Map<CodeElementIdentifier<?>, SbflStatus> methodCoverage;
    public Map<CodeElementIdentifier<?>, SbflStatus> classCoverage;

    //行 --> MethodElementName
    private Map<Integer, MethodElementName> methodElementNames;

    @JsonCreator
    public CoverageOfTarget(){
    }

    CoverageOfTarget(String targetClassName) {
        this.targetClassName = targetClassName;

        lineCoverage = new HashMap<>();
        classCoverage = new HashMap<>();
        methodCoverage = new HashMap<>();

        try {
            Map<Integer, MethodElementName> result = JavaParserUtils.getMethodNamesWithLine(new ClassElementName(targetClassName));
            methodElementNames = result;
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

            putCoverageStatus(lineCoverage, getLineElementNameFromLine(i), new SbflStatus(isTestExecuted , isTestPassed));
        }

        //method coverage
        for(IMethodCoverage mc : cc.getMethods()){
            boolean isTestExecuted = mc.getMethodCounter().getCoveredCount() == 1;
            putCoverageStatus(methodCoverage, getMethodElementNameFromLine(mc.getFirstLine()), new SbflStatus(isTestExecuted, isTestPassed));
        }

        //class coverage
        ClassElementName ce = new ClassElementName(targetClassName);
        putCoverageStatus(classCoverage, ce, getClassSbflStatus(cc, isTestPassed));
    }

    protected void putCoverageStatus(Map<CodeElementIdentifier<?>, SbflStatus> coverage, CodeElementIdentifier<?> element, SbflStatus status) {
        if(!coverage.containsKey(element)){
            coverage.put(element, status);
            return;
        }
        coverage.put(element, coverage.get(element).combine(status));
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


    public Map<CodeElementIdentifier, SbflStatus> getCoverage(Granularity granularity){
        return switch (granularity) {
            case LINE -> new TreeMap<>(lineCoverage);
            case METHOD -> new TreeMap<>(methodCoverage);
            case CLASS -> new TreeMap<>(classCoverage);
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
        new TreeMap<>(classCoverage).forEach((name, s) -> {
            out.println("|  " + name.toString() +
                    " | " + String.valueOf(s.ep) +
                    " | " + String.valueOf(s.ef) +
                    " | " + String.valueOf(s.np) +
                    " | " + String.valueOf(s.nf) + " |");
        });
    }

    private void printMethodCoverage(PrintStream out){
        int nameLength = maxLengthOfName(methodCoverage, true);
        out.println("[TARGET: " + targetClassName + "]");
        String header = "| " + " ".repeat(nameLength - "METHOD NAME".length()) + " METHOD NAME " +
                        "|  EP  |  EF  |  NP  |  NF  |";
        String partition = "=".repeat( header.length());

        out.println(partition);
        out.println(header);
        out.println(partition);
        new TreeMap<>(methodCoverage).forEach((name, s) -> {
                    out.println("|  " + name.compressedName() +
                                      " | " + String.valueOf(s.ep) +
                                      " | " + String.valueOf(s.ef) +
                                      " | " + String.valueOf(s.np) +
                                      " | " + String.valueOf(s.nf) + " |");
                });
        out.println(partition);
        out.println();
    }

    private void printLineCoverage(PrintStream out){
        out.println("[TARGET: " + targetClassName + "]");
        String header = "| LINE ||  EP  |  EF  |  NP  |  NF  |";
        String partition = "=".repeat(header.length());

        out.println(partition);
        out.println(header);
        out.println(partition);

        new TreeMap<>(lineCoverage).forEach((line, s) -> {
            out.println( String.format("| %s |", line.compressedName())+
                    "| " + String.valueOf(s.ep) +
                    " | " + String.valueOf(s.ef) +
                    " | " + String.valueOf(s.np) +
                    " | " + String.valueOf(s.nf) + " |");
        });

        out.println(partition);
        out.println();
    }


    private int maxLengthOfName(Map<CodeElementIdentifier<?>, SbflStatus> cov, boolean isMethod){
        int maxLength = 0;
        for(CodeElementIdentifier<?> name : cov.keySet()){
            int l = (isMethod) ? name.compressedName().length() : name.toString().length();
            maxLength = Math.max(maxLength, l);
        }
        return maxLength;
    }

    private LineElementName getLineElementNameFromLine(int line){
        MethodElementName methodElementName = methodElementNames.get(line);
        if(methodElementName == null) {
            return new LineElementName(targetClassName + "#<clinit>()", line);
        }
        return methodElementName.toLineElementName(line);
    }

    private MethodElementName getMethodElementNameFromLine(int line){
        MethodElementName result = methodElementNames.get(line);
        if(result == null) {
            return new MethodElementName(targetClassName + "#<clinit>()");
        }
        return result;
    }
}
