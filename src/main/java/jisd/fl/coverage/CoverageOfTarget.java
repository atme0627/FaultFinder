package jisd.fl.coverage;

import jisd.fl.sbfl.SbflStatus;
import jisd.fl.util.StaticAnalyzer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.*;

public class CoverageOfTarget implements Serializable {
    protected String targetClassName;
    protected Set<String> targetMethodNames;


    //各行のカバレッジ情報 (行番号, メソッド名, クラス名) --> lineCoverage status
    protected Map<String, SbflStatus> lineCoverage;
    protected Map<String, SbflStatus> methodCoverage;
    protected Map<String, SbflStatus> classCoverage;

    public CoverageOfTarget(String targetClassName) throws IOException {
        this.targetClassName = targetClassName;
        this.targetMethodNames = StaticAnalyzer.getMethodNames(targetClassName, false, false, true, true);

        lineCoverage = new TreeMap<>();
        classCoverage = new TreeMap<>();
        methodCoverage = new TreeMap<>();
    }

    public void processCoverage(IClassCoverage cc, boolean isTestPassed) throws IOException {
        int targetClassFirstLine = cc.getFirstLine();
        int targetClassLastLine = cc.getLastLine();

        //line coverage
        for(int i = targetClassFirstLine; i <= targetClassLastLine; i++){
            if(cc.getLine(i).getStatus() == ICounter.EMPTY) continue;
            boolean isTestExecuted = !(cc.getLine(i).getStatus() == ICounter.NOT_COVERED);
            putCoverageStatus(Integer.toString(i), new SbflStatus(isTestExecuted , isTestPassed), Granularity.LINE);
        }

        //method coverage
        Map<String, Pair<Integer, Integer>> rangeOfMethod = StaticAnalyzer.getRangeOfAllMethods(targetClassName);
        for(String targetMethodName : targetMethodNames){
            Pair<Integer, Integer> range = rangeOfMethod.get(targetMethodName);
            putCoverageStatus(targetMethodName, getMethodSbflStatus(cc, range, isTestPassed), Granularity.METHOD);
        }

        //class coverage
        putCoverageStatus(targetClassName, getClassSbflStatus(cc, isTestPassed), Granularity.CLASS);
    }

    protected void putCoverageStatus(String element, SbflStatus status, Granularity granularity){
        switch (granularity){
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

    protected SbflStatus getMethodSbflStatus(IClassCoverage cc, Pair<Integer, Integer> range, boolean isTestPassed){
        int methodBegin = range.getLeft();
        int methodEnd = range.getRight();
        boolean isTestExecuted = false;
        for(int i = methodBegin; i <= methodEnd; i++){
            int status = cc.getLine(i).getStatus();
            if(status == ICounter.PARTLY_COVERED || status == ICounter.FULLY_COVERED) {
                isTestExecuted = true;
                break;
            }
        }
        return new SbflStatus(isTestExecuted, isTestPassed);
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


    public Map<String, SbflStatus> getCoverage(Granularity granularity){
        switch (granularity){
            case LINE:
                return lineCoverage;
            case METHOD:
                return methodCoverage;
            case CLASS:
                return classCoverage;
        }
        return null;
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
            out.println("|  " + StringUtils.leftPad(name, 100) +
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
                    out.println("|  " + StringUtils.leftPad(name.split("#")[1], nameLength) +
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

        List<String> keys = new ArrayList<>(lineCoverage.keySet());
        keys.sort(Comparator.comparingInt(Integer::parseInt));
        for(String line : keys){
            SbflStatus s = lineCoverage.get(line);

            out.println( String.format("| %4d |", Integer.parseInt(line))+
                    "| " + StringUtils.leftPad(String.valueOf(s.ep), 4) +
                    " | " + StringUtils.leftPad(String.valueOf(s.ef), 4) +
                    " | " + StringUtils.leftPad(String.valueOf(s.np), 4) +
                    " | " + StringUtils.leftPad(String.valueOf(s.nf), 4) + " |");
        }

        out.println(partition);
        out.println();
    }

    void combineCoverages(CoverageOfTarget cov){
        this.lineCoverage = combineCoverage(this.lineCoverage, cov.lineCoverage);
        this.methodCoverage = combineCoverage(this.methodCoverage,  cov.methodCoverage);
        this.classCoverage = combineCoverage(this.classCoverage, cov.classCoverage);
    }

    private Map<String, SbflStatus> combineCoverage(Map<String, SbflStatus> thisCov, Map<String, SbflStatus> otherCov){
        Map<String, SbflStatus> newCoverage = new HashMap<>(otherCov);
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

    private int maxLengthOfName(Map<String, SbflStatus> cov, boolean isMethod){
        int maxLength = 0;
        for(String name : cov.keySet()){
            int l = (isMethod) ? name.split("#")[1].length() : name.length();
            maxLength = Math.max(maxLength, l);
        }
        return maxLength;
    }
}
