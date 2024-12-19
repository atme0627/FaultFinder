package jisd.fl.probe;

import com.sun.jdi.ReferenceType;
import com.sun.jdi.VMDisconnectedException;
import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import jisd.debug.Location;
import jisd.debug.Point;
import jisd.debug.value.PrimitiveInfo;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.StaticAnalyzer;
import jisd.fl.util.TestUtil;
import jisd.info.ClassInfo;
import jisd.info.LocalInfo;
import jisd.info.MethodInfo;
import jisd.info.StaticInfoFactory;
import org.apache.commons.lang3.tuple.Pair;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.NoSuchFileException;
import java.time.LocalDateTime;
import java.util.*;

public abstract class AbstractProbe {

    FailedAssertInfo assertInfo;
    Debugger dbg;
    StaticInfoFactory targetSif;
    StaticInfoFactory testSif;
    static PrintStream stdOut = System.out;
    static PrintStream stdErr = System.err;


    public AbstractProbe(FailedAssertInfo assertInfo) {
        String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");
        String targetBinDir = PropertyLoader.getProperty("d4jTargetBinDir");
        String testSrcDir = PropertyLoader.getProperty("d4jTestSrcDir");
        String testBinDir = PropertyLoader.getProperty("d4jTestBinDir");

        this.assertInfo = assertInfo;
        this.dbg = createDebugger();

        this.targetSif = new StaticInfoFactory(targetSrcDir, targetBinDir);
        this.testSif = new StaticInfoFactory(testSrcDir, testBinDir);
    }

    public abstract ProbeResult run(int sleepTime) throws IOException;

    public List<Integer> getCanSetLine(VariableInfo variableInfo) {
        List<Integer> canSetLines = new ArrayList<>();
        ClassInfo ci = targetSif.createClass(variableInfo.getLocateClass());

        if(variableInfo.isField()) {
            Map<String, ArrayList<Integer>> canSet = ci.field(variableInfo.getVariableName()).canSet();
            for (List<Integer> lineWithVar : canSet.values()) {
                canSetLines.addAll(lineWithVar);
            }
            return canSetLines;
        }
        else {
            MethodInfo mi = ci.method(variableInfo.getLocateMethod());
            LocalInfo li = mi.local(variableInfo.getVariableName());

            return li.canSet();
        }
    }


    protected ProbeResult searchProbeLine(List<ProbeInfo> watchedValues, List<Integer> assignedLine){
        System.out.println("    >> Probe Info: Searching probe line.");
        boolean isFound = false;
        ProbeResult result = new ProbeResult();


//        List<ProbeInfo> matchValues = new ArrayList<>();
//        for (ProbeInfo pi : watchedValues) {
//            if (!assertInfo.eval(pi.value)) continue;
//            matchValues.add(pi);
//            isFound = true;
//        }
//        if (!isFound) throw new RuntimeException("No matching rows found.");

        //assignLineが実行された直後のProbeInfoの行を集める
        //watchedValuesの最後の要素がassignLineになることはない(はず)
        List<Integer> afterAssignedLines = new ArrayList<>();
        for(int i = 0; i < watchedValues.size() - 1; i++){
            ProbeInfo pi = watchedValues.get(i);
            for(int l : assignedLine){
                //assignLine lが実行された場合
                if(pi.loc.getLineNumber() == l) {
                    //直後のProbeInfoの行を追加
                    afterAssignedLines.add(watchedValues.get(i + 1).loc.getLineNumber());
                    break;
                }
            }
        }

        //新しいものから探して最初にexecutedAssignedLines内の値に一致したものがobjective
        int probeLine = 0;
        String locationClass = null;
        if(afterAssignedLines.isEmpty()) {
            //ここに来た時点で、値はこのメソッド内で変更されていない
            //渡された値がそもそも間違い
            //引数のパターンしかない(?)
            probeLine = watchedValues.get(0).loc.getLineNumber();
            locationClass = watchedValues.get(0).loc.getClassName();
            String probeMethod = null;
            try {
                probeMethod = StaticAnalyzer.getMethodNameFormLine(locationClass ,probeLine);
            } catch (NoSuchFileException e) {
                throw new RuntimeException(e);
            }

            result.setProbeMethod(probeMethod);
            result.setArgument(true);
            return result;
        }
        else {
            watchedValues.sort(Comparator.reverseOrder());
            //watchedValuesの最後の要素がassignLineになることはない(はず)
            for(int i = 0; i < watchedValues.size() - 1; i++){
                ProbeInfo pi = watchedValues.get(i);
                //piがafterAssignedLineだった場合
                if(afterAssignedLines.contains(pi.loc.getLineNumber())){
                    //その直前のProbeInfoが正解の行
                    pi = watchedValues.get(i + 1);
                    probeLine = pi.loc.getLineNumber();
                    locationClass = pi.loc.getClassName();
                    break;
                }
            }
        }

        //実行しているメソッドを取得
        String probeMethod = null;
        Pair<Integer, Integer> probeLines = null;
        try {
            probeMethod = StaticAnalyzer.getMethodNameFormLine(locationClass ,probeLine);
            probeLines = StaticAnalyzer.getRangeOfStatement(locationClass).get(probeLine);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        //シグニチャも含める
        result.setLines(probeLines);
        result.setProbeMethod(probeMethod);
        result.setSrc(getProbeStatement(locationClass, probeLines));
        return result;
    }

    //Statementのソースコードを取得
    private String getProbeStatement(String locationClass, Pair<Integer, Integer> probeLines){
        ClassInfo ci = targetSif.createClass(locationClass);
        String[] src = ci.src().split("\n");
        StringBuilder stmt = new StringBuilder();
        for(int i = probeLines.getLeft(); i <= probeLines.getRight(); i++) {
            stmt.append(src[i - 1]);
            stmt.append("\n");
        }
        return stmt.toString();
    }

    //一回のprobeを行う
    //条件を満たす行の情報を返す
    protected ProbeResult probing(int sleepTime, VariableInfo variableInfo){
        ProbeInfoCollection watchedValueCollection = extractInfoFromDebugger(variableInfo, sleepTime);
        List<Integer> assignLines = null;
        try {
            assignLines = StaticAnalyzer.getAssignLine(
                    variableInfo.getLocateClass(),
                    variableInfo.getVariableName(true));
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
        printWatchedValues(watchedValueCollection, variableInfo, assignLines);
        List<ProbeInfo> watchedValues = watchedValueCollection.getPis(variableInfo.getVariableName(true));
        ProbeResult result = searchProbeLine(watchedValues, assignLines);
        if(!result.isArgument()) printProbeStatement(result, variableInfo);
        result.setVariableInfo(variableInfo);
        return result;
    }

    //primitive型の値のみを取得
    //variableInfoが参照型の場合、fieldを取得してその中から目的のprimitive型の値を探す
    private List<ProbeInfo> getValuesFromDebugResult(VariableInfo targetInfo, HashMap<String, DebugResult> drs){
        List<ProbeInfo> pis = new ArrayList<>();
        drs.forEach((variable, dr) -> {
            List<ValueInfo> vis = null;
            try {
                vis = new ArrayList<>(dr.getValues());
            } catch (RuntimeException e) {
                return;
            }

            VariableInfo variableInfo = variable.equals(targetInfo.getVariableName(true)) ? targetInfo : null;

            for (ValueInfo vi : vis) {
                LocalDateTime createdAt = vi.getCreatedAt();
                Location loc = dr.getLocation();
                String variableName = vi.getName();
                String value;
                //対象の変数がnullの場合
                if (vi.getValue().isEmpty()) {
                    value = "null";
                } else {
                    PrimitiveInfo pi = getPrimitiveInfoFromValueInfo(vi, variableInfo);
                    if(pi == null){
                        return;
                    }
                    value = pi.getValue();
                }
                pis.add(new ProbeInfo(createdAt, loc, variableName, value));
            }
        });
        return pis;
    }

    //参照型の配列には未対応
    //viがnullの時はprimitive型かそのラッパー型の場合のみ探す。
    private PrimitiveInfo getPrimitiveInfoFromValueInfo(ValueInfo vi, VariableInfo variableInfo){
        if(variableInfo == null) return getPrimitiveInfoFromValueInfo(vi);
        //プリミティブ型の場合
        if(variableInfo.isPrimitive()) return (PrimitiveInfo) vi;

        //プリミティブ型の配列の場合
        if(variableInfo.isArray()){
            int arrayNth = variableInfo.getArrayNth();
            ArrayList<ValueInfo> arrayElements = vi.ch();
            return (PrimitiveInfo) arrayElements.get(arrayNth);
        }

        //参照型の場合
        if(variableInfo.isPrimitive()){
            ArrayList<ValueInfo> fieldElements = vi.ch();
            boolean isFound = false;
            String fieldName = variableInfo.getTargetField().getVariableName();
            for(ValueInfo e : fieldElements){
                if(e.getName().equals(fieldName)){
                    getPrimitiveInfoFromValueInfo(e, variableInfo.getTargetField());
                    isFound = true;
                    break;
                }
            }
            if(!isFound) throw new NoSuchElementException(fieldName + " is not found in fields of" + variableInfo.getVariableName(false));
        }
        return null;
    }

    //TODO: viがprimitive型とそのラッパー型である場合のみ考える。
    //そうでない場合はnullを返す。
    private PrimitiveInfo getPrimitiveInfoFromValueInfo(ValueInfo vi) {
        if(!(vi instanceof PrimitiveInfo)) return null;
        return (PrimitiveInfo) vi;
    }

    protected ProbeInfoCollection extractInfoFromDebugger(VariableInfo variableInfo, int sleepTime){
        disableStdOut("    >> Probe Info: Running debugger.");
        List<Integer> canSetLines = getCanSetLine(variableInfo);
        String dbgMain = variableInfo.getLocateClass();
        String varName = variableInfo.getVariableName(true);
        List<Optional<Point>> watchPoints = new ArrayList<>();
        //set watchPoint
        dbg.setMain(dbgMain);
        //String[] fieldNames = {varName};
        for (int line : canSetLines) {
            //watchPoints.add(dbg.watch(line, fieldNames));
            watchPoints.add(dbg.watch(line));
        }

        //run debugger
        try {
            dbg.run(sleepTime);
        } catch (VMDisconnectedException ignored) {
        }
        dbg.exit();

        enableStdOut();
        ProbeInfoCollection watchedValues = getInfoFromWatchPoints(watchPoints, variableInfo);
        watchedValues.sort();
        return watchedValues;
    }

    private ProbeInfoCollection getInfoFromWatchPoints(List<Optional<Point>> watchPoints, VariableInfo variableInfo){
        //get Values from debugResult
        //実行されなかった行の情報は飛ばす。
        //実行されたがnullのものは含む。
        String varName = variableInfo.getVariableName(true);
        ProbeInfoCollection watchedValues = new ProbeInfoCollection();
        for (Optional<Point> op : watchPoints) {
            Point p;
            if (op.isEmpty()) continue;
            p = op.get();
            Optional<DebugResult> od = p.getResults(varName);
            HashMap<String, DebugResult> drs = p.getResults();
            if (od.isEmpty()) continue;
            watchedValues.addElements(getValuesFromDebugResult(variableInfo, drs));
        }

        if (watchedValues.isEmpty()) {
            throw new RuntimeException("Probe#run\n" +
                    "there is not target value in watch point.");
        }
        return watchedValues;
    }

    protected void disableStdOut(String msg){
        if(!msg.isEmpty()) System.out.println(msg);
        PrintStream nop = new PrintStream(new OutputStream() {
            public void write(int b) { /* noop */ }
        });
        System.setOut(nop);
        System.setErr(nop);
    }

    protected void enableStdOut(){
        System.setOut(stdOut);
        System.setErr(stdErr);
    }

    protected void printWatchedValues(ProbeInfoCollection watchedValues, VariableInfo variableInfo, List<Integer> assignLine){
        System.out.println("    >> ---------------------------------");
        System.out.println("    >> [field name] "
                + variableInfo.getVariableName(true)
                + " [type] "
                + variableInfo.getVariableType());
        System.out.println("    >> [assigned line] " + Arrays.toString(assignLine.toArray()));
        watchedValues.print(variableInfo.getVariableName(true));
    }

    protected void printProbeStatement(ProbeResult result, VariableInfo variableInfo){
        System.out.println("    >> [probe Statement]");
        Pair<Integer, Integer> probeLines = result.getProbeLines();
        String[] src = result.getSrc().split("\n");
        int l = 0;
        for(int i = probeLines.getLeft(); i <= probeLines.getRight(); i++) {
            System.out.println("    >> " + i + ": " + src[l++]);
        }
        System.out.println("    >> ---------------------------------");
    }

    protected Debugger createDebugger(){
        return TestUtil.testDebuggerFactory(assertInfo.getTestMethodName());
    }

    //動的解析
    //テストケース実行時の実際に呼び出されているメソット群を返す
    //locateMethodはフルネーム、シグニチャあり
    MethodCollection getCalleeMethods(String testMethod,
                                 String locateMethod){
        System.out.println("    >> Probe Info: Collecting callee methods.");
        System.out.println("    >> Probe Info: Target method --> " + locateMethod);

        MethodCollection calleeMethods = new MethodCollection();
        String locateClass = locateMethod.split("#")[0];
        List<Integer> methodCallingLines = null;
        try {
            methodCallingLines = StaticAnalyzer.getMethodCallingLine(locateMethod);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        Debugger dbg = TestUtil.testDebuggerFactory(testMethod);
        dbg.setMain(locateClass);

        disableStdOut("");
        StackTrace st;

        for(int l : methodCallingLines){
            dbg.stopAt(l);
        }

        dbg.run(2000);
        //callerMethodを取得
        st = getStackTrace(dbg);
        String callerMethod = st.getMethod(1);

        for(int i = 0; i < methodCallingLines.size(); i++) {
            //TODO: メソッド呼び出しが行われるまで
            dbg.step();
            while (true) {
                int probingLine = methodCallingLines.get(i);
                st = getStackTrace(dbg);
                dbg.stepOut();
                Pair<Integer, String> e = st.getMethodAndCallLocation(0);
                //ここで外部APIのメソッドは弾かれる
                if(e != null) calleeMethods.addElement(e);

                //終了判定
                //stepOutで次の行に移った場合終了
                if(dbg.loc().getLineNumber() != probingLine) break;
                //stepOutで元の行に戻った場合、ステップして新しいメソッド呼び出しが行われなければ終了
                dbg.step();
                st = getStackTrace(dbg);
                if (st.getMethod(0).equals(locateMethod.substring(0, locateMethod.lastIndexOf("(")))
                        || st.getMethod(0).equals(callerMethod)) {
                    break;
                }
            }
            //すでにbreakpointにいる場合はスキップしない
            if(!methodCallingLines.contains(dbg.loc().getLineNumber())) {
                dbg.cont(2000);
            }
        }

        enableStdOut();

        return calleeMethods;
    }

    protected StackTrace getStackTrace(Debugger dbg){
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos);
        PrintStream out = System.out;
        System.setOut(ps);
        bos.reset();
        dbg.where();
        System.setOut(out);

        return new StackTrace(bos.toString(), "");
    }

    Pair<Integer, String> getCallerMethod(Pair<Integer, Integer> probeLines, String locateClass) {
        dbg = createDebugger();
        dbg.setMain(locateClass);
        disableStdOut("    >> Probe Info: Running debugger.");
        dbg.stopAt(probeLines.getLeft());
        dbg.run(2000);
        enableStdOut();
        StackTrace st = getStackTrace(dbg);

        //callerMethodをシグニチャ付きで取得する
        return st.getMethodAndCallLocation(1);
    }

    protected static class ProbeInfo implements Comparable<ProbeInfo>{
        LocalDateTime createAt;
        Location loc;
        String variableName;
        String value;

        ProbeInfo(LocalDateTime createAt,
                  Location loc,
                  String variableName,
                  String value){
            this.createAt = createAt;
            this.loc = loc;
            this.variableName = variableName;
            this.value = value;
        }

        @Override
        public int compareTo(ProbeInfo o) {
            return createAt.compareTo(o.createAt);
        }

        @Override
        public String toString(){
            return   "[CreateAt] " + createAt +
                    " [Variable] " + variableName +
                    " [Line] " + loc.getLineNumber() +
                    " [value] " + value;
        }
    }

    protected static class ProbeInfoCollection {
        Map<String, List<ProbeInfo>> piCollection = new HashMap<>();

        public void addElements(List<ProbeInfo> pis){
            for(ProbeInfo pi : pis){
                if(piCollection.containsKey(pi.variableName)){
                    piCollection.get(pi.variableName).add(pi);
                }
                else {
                    List<ProbeInfo> newPis = new ArrayList<>();
                    newPis.add(pi);
                    piCollection.put(pi.variableName, newPis);
                }
            }
        }

        public List<ProbeInfo> getPis(String key){
            return piCollection.get(key);
        }

        public boolean isEmpty(){
            return piCollection.isEmpty();
        }

        public void sort(){
            for(List<ProbeInfo> pis : piCollection.values()){
                pis.sort(ProbeInfo::compareTo);
            }
        }

        public void print(String key){
            for(ProbeInfo pi : piCollection.get(key)) {
                System.out.println("    >> " + pi);
            }
        }

        public void printAll(){
            for(String key : piCollection.keySet()){
                print(key);
            }
        }
    }
}
