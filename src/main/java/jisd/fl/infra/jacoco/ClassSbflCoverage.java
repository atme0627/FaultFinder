package jisd.fl.infra.jacoco;

import jisd.fl.core.entity.coverage.ElementIDRegistry;
import jisd.fl.core.entity.coverage.SbflCounts;
import jisd.fl.core.entity.coverage.SbflCountsTable;
import jisd.fl.core.entity.coverage.SbflCoverageView;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.LineElementName;
import jisd.fl.core.entity.element.LineElementNameResolver;
import jisd.fl.core.entity.element.MethodElementName;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.IMethodCoverage;

import java.nio.file.NoSuchFileException;
import java.util.Arrays;

public class ClassSbflCoverage {

    public final ClassElementName targetClass;
    private final LineElementNameResolver lineElementNameResolver;

    private int totalPass = 0;
    private int totalFail = 0;

    private final ElementIDRegistry<LineElementName> lineIds = new ElementIDRegistry<>();
    private final SbflCountsTable lineCounts = new SbflCountsTable();

    private final ElementIDRegistry<MethodElementName> methodIds = new ElementIDRegistry<>();
    private final SbflCountsTable methodCounts = new SbflCountsTable();

    private int classEp = 0;
    private int classEf = 0;

    //--- for optimize ---
    private boolean initialized = false;
    private int firstLIne = -1;
    private int lastLine = -1;

    private int[] lineIdByLine;
    private int[] methodIdByLine;
    //--------------------


    public ClassSbflCoverage(ClassElementName targetClass, LineElementNameResolver resolver) throws NoSuchFileException {
        this.targetClass = targetClass;
        this.lineElementNameResolver = resolver;
    }

    public int totalPass() {
        return totalPass;
    }
    public int totalFail() {
        return totalFail;
    }

    public SbflCoverageView<LineElementName> lineCoverageView(){
        return new SbflCoverageView<>(lineIds, lineCounts, totalPass, totalFail);
    }
    public SbflCoverageView<MethodElementName> methodCoverageView(){
        return new SbflCoverageView<>(methodIds, methodCounts, totalPass, totalFail);
    }

    public boolean hasAnyCoverage(){
        return (classEf + classEp) > 0;
    }

    public SbflCounts classCounts(){
        int ep = classEp;
        int ef = classEf;
        int np = totalPass - ep;
        int nf = totalFail - ef;
        return new SbflCounts(ep, ef, np, nf);
    }

    public void accept(IClassCoverage cc, boolean testPassed) {
        initializeIfNeeded(cc);
        if(testPassed) totalPass++; else totalFail++;

        //行単位カバレッジ
        for(int line = firstLIne; line <= lastLine; line++){
            int stat = cc.getLine(line).getStatus();
            if(stat == ICounter.EMPTY) continue;

            boolean executed = (stat != ICounter.NOT_COVERED);
            if(!executed) continue;
            int id = lineIdByLine[line];
            if(id < 0){
                System.err.println("[INITIALIZE MAY FAILED] target: " + targetClass + ", lineIdByLine[" + line + "] = " + id);
                continue;
            }
            if(testPassed) lineCounts.incEp(id); else lineCounts.incEf(id);
        }

        //メソッド単位カバレッジ
        for(IMethodCoverage mc : cc.getMethods()){
            boolean executed = mc.getMethodCounter().getCoveredCount() > 0;
            if(!executed) continue;

            int fl = mc.getFirstLine();
            if(fl <= 0 || fl > lastLine) continue;

            int id = methodIdByLine[fl];
            if(id < 0){
                System.err.println("[INITIALIZE MAY FAILED] target: " + targetClass + ", methodIdByLine[" + fl + "] = " + id);
                continue;
            }
            if(testPassed) methodCounts.incEp(id); else methodCounts.incEf(id);
        }

        //クラス単位カバレッジ
        boolean executed = isClassExecuted(cc);
        if(executed) {
            if (testPassed) classEp++;
            else classEf++;
        }
    }

    private static boolean isClassExecuted(IClassCoverage cc) {
        int first = cc.getFirstLine();
        int last = cc.getLastLine();
        for(int line = first; line <= last; line++){
            int stat = cc.getLine(line).getStatus();
            if(stat == ICounter.PARTLY_COVERED || stat == ICounter.FULLY_COVERED) return true;
        }
        return false;
    }

    private void initializeIfNeeded(IClassCoverage cc){
        if(initialized) return;
        this.firstLIne = cc.getFirstLine();
        this.lastLine = cc.getLastLine();

        lineIdByLine = new int[lastLine + 1];
        methodIdByLine = new int[lastLine + 1];
        Arrays.fill(lineIdByLine, -1);
        Arrays.fill(methodIdByLine, -1);

        //行単位要素の初期化
        for(int line = firstLIne; line <= lastLine; line++){
            int stat = cc.getLine(line).getStatus();
            if(stat == ICounter.EMPTY) continue; // 実行不可能行 or 範囲外

            LineElementName e = lineElementNameResolver.lineElementAt(line);
            int id = lineIds.getOrCreate(e);
            lineIdByLine[line] = id;
        }
        lineCounts.ensureCapacity(lineIds.size());

        //メソッド単位要素の初期化
        for(IMethodCoverage mc : cc.getMethods()){
            int fl = mc.getFirstLine();
            if(fl <= 0 || fl > lastLine) continue;
            MethodElementName e = lineElementNameResolver.lineElementAt(fl).methodElementName;
            int id = methodIds.getOrCreate(e);
            methodIdByLine[fl] = id;
        }
        methodCounts.ensureCapacity(methodIds.size());
        initialized = true;
    }
}
