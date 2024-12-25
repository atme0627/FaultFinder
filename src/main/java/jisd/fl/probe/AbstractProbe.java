package jisd.fl.probe;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
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

    //一回のprobeを行う
    //条件を満たす行の情報を返す
    protected ProbeResult probing(int sleepTime, VariableInfo variableInfo){
        ProbeInfoCollection watchedValueCollection = extractInfoFromDebugger(variableInfo, sleepTime);
        List<Integer> assignLines = null;
        try {
            assignLines = StaticAnalyzer.getAssignLine(
                    variableInfo.getLocateClass(),
                    variableInfo.getVariableName(true, false));
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        List<ProbeInfo> watchedValues = watchedValueCollection.getPis(variableInfo.getVariableName(true, true));
        //printWatchedValues(watchedValueCollection, variableInfo, assignLines);
        ProbeResult result = searchProbeLine(watchedValues, assignLines, variableInfo.getActualValue());


        result.setVariableInfo(variableInfo);
        if(!result.isArgument()) result.setValuesInLine(watchedValueCollection.getvaluesFromLines(result.getProbeLines()));
        return result;
    }

    public List<Integer> getCanSetLine(VariableInfo variableInfo) {
        List<Integer> canSetLines = new ArrayList<>();
        ClassInfo ci = targetSif.createClass(variableInfo.getLocateClass());

        if(variableInfo.isField()) {
            Map<String, ArrayList<Integer>> canSet = ci.field(variableInfo.getVariableName()).canSet();
            for (List<Integer> lineWithVar : canSet.values()) {
                canSetLines.addAll(lineWithVar);
            }
        }
        else {
            //TODO: ci.methodは引数に内部で定義されたクラスのインスタンスを含む場合、フルパスが必要
            //TODO: ex.) SimplexTableau(org/apache/commons/math/optimization/linear/LinearObjectiveFunction, java.util.Collection, org/apache/commons/math/optimization/GoalType, boolean, double)
            String fullMethodName = StaticAnalyzer.getFullNameOfMethod(variableInfo.getLocateMethod(), ci);
            MethodInfo mi = ci.method(fullMethodName);
            LocalInfo li = mi.local(variableInfo.getVariableName());
            canSetLines = li.canSet();
        }

        return canSetLines;
    }


    protected ProbeResult searchProbeLine(List<ProbeInfo> watchedValues, List<Integer> assignedLine, String actual){
        System.out.println("    >> Probe Info: Searching probe line.");
        ProbeResult result = new ProbeResult();

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
        boolean isFound = false;
        int probeLine = 0;
        String locationClass = null;
        if(!afterAssignedLines.isEmpty()) {
            watchedValues.sort(Comparator.reverseOrder());
            //watchedValuesの最後の要素がassignLineになることはない(はず)
            for(int i = 0; i < watchedValues.size() - 1; i++){
                ProbeInfo pi = watchedValues.get(i);
                //probe対象変数がactualの値を取らなくなった場合、直後のpiが正解の行
                if(!pi.value.equals(actual)) {
                    if(i == 0) {
                    //TODO: 値が全然違う時がある。観測値が実行毎に変わる場合あり?
                        disableStdOut("    >> Probe Info: failed to watch value. ");
                    }
                    pi = watchedValues.get(i - 1);
                    isFound = true;
                    probeLine = pi.loc.getLineNumber();
                    locationClass = pi.loc.getClassName();
                    break;
                };
                //piがafterAssignedLineだった場合
                if(afterAssignedLines.contains(pi.loc.getLineNumber())){
                    //その直前のProbeInfoが正解の行
                    pi = watchedValues.get(i + 1);
                    isFound = true;
                    probeLine = pi.loc.getLineNumber();
                    locationClass = pi.loc.getClassName();
                    break;
                }
            }
        }

        if(!isFound) {
            //ここに来た時点で、値はこのメソッド内で変更されていない
            //初期化時の値か渡された値がそもそも間違い

            //前の行にするのは、宣言されている行がそこにあるから
            // ex) 10: int a = 1;
            //     11: b = a + 1; <-- watchedLineにある最後の行

            probeLine = watchedValues.get(0).loc.getLineNumber() - 1;
            locationClass = watchedValues.get(0).loc.getClassName();


        }

        //実行しているメソッドを取得
        String probeMethod = null;
        Pair<Integer, Integer> probeLines = null;
        try {
            probeMethod = StaticAnalyzer.getMethodNameFormLine(locationClass ,probeLine);
            probeLines = StaticAnalyzer.getRangeOfAllStatements(locationClass).get(probeLine);

            //Argumentかどうか判定
            //(probeMethodがnull つまり probeLineがmethodの外) or methodの中かつblockStmtの外のとき変数は引数由来
            // <- 元のprobeLine
            // public void sample(int a) { <-ここが呼び出されたメソッド
            // ... <- 新しいprobeLine
            // (getCallerMethodではprobeLineを見てブレイクボイントをつけるため、メソッド内にいる必要がある)
            boolean isThereVariableDeclaration = false;
            String variableName = watchedValues.get(0).variableName;

            if(probeMethod != null){
                BlockStmt bs = StaticAnalyzer.bodyOfMethod(probeMethod);
                List<VariableDeclarator> vds = bs.findAll(VariableDeclarator.class);
                for(VariableDeclarator vd : vds){
                    if(vd.getNameAsString().equals(variableName) &&
                            vd.getBegin().get().line <= probeLine && probeLine <= vd.getEnd().get().line){
                        isThereVariableDeclaration = true;
                    }
                }
            }

            //probeLineがmethodBodyの範囲内で、かつprobeLineでvariableの宣言が行われていない場合、その変数はargument
            if(!isThereVariableDeclaration && !isFound){
                probeLine = watchedValues.get(0).loc.getLineNumber();
                result.setArgument(true);
            }

            //probeMethodがnull つまり probeLineがmethodの外にある場合、必ずargument
            if(probeMethod == null) {
                probeLine = watchedValues.get(0).loc.getLineNumber();
                probeMethod = StaticAnalyzer.getMethodNameFormLine(locationClass ,probeLine);
                result.setArgument(true);
            }

            //probeLinesがnull -> probeLineがstatementでない時、probeLineはfor文の始まりなど
            if(probeLines == null) {
                probeLines = Pair.of(probeLine, probeLine);
            }

        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        //Argumentかどうか判定
        //probeMethodの1行目がprobeLineの時、probe対象の変数は引数由来
//        int methodBeginLine;
//        try {
//            Map<String, Pair<Integer, Integer>> rangeOfMethods = StaticAnalyzer.getRangeOfAllMethods(locationClass);
//            methodBeginLine = rangeOfMethods.get(probeMethod).getLeft();
//            if(methodBeginLine == probeLine)    result.setArgument(true);
//        } catch (NoSuchFileException e) {
//            throw new RuntimeException(e);
//        }

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

            VariableInfo variableInfo = variable.equals(targetInfo.getVariableName(true, false)) ? targetInfo : null;

            for (ValueInfo vi : vis) {
                LocalDateTime createdAt = vi.getCreatedAt();
                Location loc = dr.getLocation();
                String variableName = (variableInfo == null) ? vi.getName() : variableInfo.getVariableName(true, true);
                String value;
                //対象の変数がnullの場合
                if (vi.getValue().isEmpty()) {
                    value = "null";
                    pis.add(new ProbeInfo(createdAt, loc, variableName, value));
                } else {
                    //viがprobe対象
                    if(variableInfo != null){
                        value = getPrimitiveInfoFromReferenceType(vi, variableInfo).getValue();
                        pis.add(new ProbeInfo(createdAt, loc, variableName, value));
                    }
                    //viがプリミティブ型の一次元配列
                    else if(vi.getValue().contains("[") && !vi.getValue().contains("][")) {
                        List<PrimitiveInfo> piList = getPrimitiveInfoFromArrayType(vi);
                        for(int i = 0; i < piList.size(); i++){
                            value = piList.get(i).getValue();
                            pis.add(new ProbeInfo(createdAt, loc, variableName + "[" + i + "]", value));
                        }
                    }

                    //viがプリミティブ型かそのラッパー
                    else if(isPrimitive(vi)) {
                        value = getPrimitiveInfoFromPrimitiveType(vi).getValue();
                        pis.add(new ProbeInfo(createdAt, loc, variableName, value));
                    }
                }
            }
        });
        return pis;
    }

    //参照型の配列には未対応
    private PrimitiveInfo getPrimitiveInfoFromReferenceType(ValueInfo vi, VariableInfo variableInfo){
        //プリミティブ型の配列の場合
        if(variableInfo.isArray()){
            int arrayNth = variableInfo.getArrayNth();
            ArrayList<ValueInfo> arrayElements = vi.ch();
            return (PrimitiveInfo) arrayElements.get(arrayNth);
        }

        //プリミティブ型の場合
        if(variableInfo.isPrimitive()) {
            return getPrimitiveInfoFromPrimitiveType(vi);
        }
        //参照型の場合
        else {
            ArrayList<ValueInfo> fieldElements = vi.ch();
            boolean isFound = false;
            String fieldName = variableInfo.getTargetField().getVariableName();
            for(ValueInfo e : fieldElements){
                if(e.getName().equals(fieldName)){
                    getPrimitiveInfoFromReferenceType(e, variableInfo.getTargetField());
                    isFound = true;
                    break;
                }
            }
            if(!isFound) throw new NoSuchElementException(fieldName + " is not found in fields of" + variableInfo.getVariableName(false, false));
        }
        throw new RuntimeException();
    }

    private boolean isPrimitive(ValueInfo vi){
        Set<String> primitiveWrapper = new HashSet<>(List.of(
                "java.lang.Boolean",
                "java.lang.Byte",
                "java.lang.Character",
                "java.lang.Double",
                "java.lang.Float",
                "java.lang.Integer",
                "java.lang.Long",
                "java.lang.Short"
        ));

        if (vi instanceof PrimitiveInfo) return true;

        String law = vi.getValue();
        String valueType = law.substring("instance of".length(), law.indexOf("(")).trim();
        return primitiveWrapper.contains(valueType);
    }
    //viがprimitive型とそのラッパー型である場合のみ考える。
    //そうでない場合はnullを返す。
    private PrimitiveInfo getPrimitiveInfoFromPrimitiveType(ValueInfo vi) {
        if(vi instanceof PrimitiveInfo) return (PrimitiveInfo) vi;

        Set<String> primitiveWrapper = new HashSet<>(List.of(
                "java.lang.Boolean",
                "java.lang.Byte",
                "java.lang.Character",
                "java.lang.Double",
                "java.lang.Float",
                "java.lang.Integer",
                "java.lang.Long",
                "java.lang.Short"
        ));

        //ex) vi.getValue() --> instance of java.lang.Integer(id=2827)
        String law = vi.getValue();
        String valueType = law.substring("instance of".length(), law.indexOf("(")).trim();

        //プリミティブ型のラッパークラスのとき
        if(primitiveWrapper.contains(valueType)) {
            return (PrimitiveInfo) vi.ch().get(0);
        }

        throw new RuntimeException(vi.getName() + " is not primitive.");
    }

    private ArrayList<PrimitiveInfo> getPrimitiveInfoFromArrayType(ValueInfo vi) {
        //    vi.getValue() --> instance of double[1] (id=2814)
        String law = vi.getValue();
        String valueType = law.substring("instance of".length(), law.indexOf("(")).trim();

        Set<String> primitiveType = new HashSet<>(List.of(
                "boolean",
                "byte",
                "char",
                "double",
                "float",
                "int",
                "long",
                "short"
        ));

        //プリミティブ型の配列のとき
        if(primitiveType.contains(valueType.substring(0, valueType.indexOf("[")))){
            ArrayList<PrimitiveInfo> pis = new ArrayList<>();
            vi.ch().forEach(e -> pis.add(getPrimitiveInfoFromPrimitiveType(e)));
            return pis;
        }
        throw new RuntimeException(vi.getName() + "is not array.");
    }

    protected ProbeInfoCollection extractInfoFromDebugger(VariableInfo variableInfo, int sleepTime){
        disableStdOut("    >> Probe Info: Running debugger and extract watched info.");
        List<Integer> canSetLines = getCanSetLine(variableInfo);
        String dbgMain = variableInfo.getLocateClass();
        List<Optional<Point>> watchPoints = new ArrayList<>();
        dbg = createDebugger();
        //set watchPoint
        dbg.setMain(dbgMain);
        for (int line : canSetLines) {
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
        String varName = variableInfo.getVariableName(true, false);
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
        System.out.println("    >> [assigned line] " + Arrays.toString(assignLine.toArray()));
        //watchedValues.print(variableInfo.getVariableName(true, true));
        watchedValues.printAll();
    }

    protected void printProbeStatement(ProbeResult result){
        System.out.println("    >> [PROBE LINES]");
        if(result.isArgument()) {
            System.out.println("    >> Variable defect is derived from caller method. ");
            if(result.getCallerMethod() != null) {
                System.out.println("    >> [CALLER] " + result.getCallerMethod().getRight());
                System.out.println("    >> [LINE] " + result.getCallerMethod().getLeft());
            }
        } else {
            Pair<Integer, Integer> probeLines = result.getProbeLines();
            String[] src = result.getSrc().split("\n");
            int l = 0;
            for (int i = probeLines.getLeft(); i <= probeLines.getRight(); i++) {
                System.out.println("    >> " + i + ": " + src[l++]);
            }
        }
    }

    protected Debugger createDebugger(){
        return TestUtil.testDebuggerFactory(assertInfo.getTestMethodName());
    }

    //動的解析
    //メソッド実行時の実際に呼び出されているメソット群を返す
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
                Pair<Integer, String> e = st.getCalleeMethodAndCallLocation(0);
                //ここで外部APIのメソッドは弾かれる
                if(e != null) calleeMethods.addElement(e);

                //終了判定
                //stepOutで次の行に移った場合終了
                if(dbg.loc().getLineNumber() != probingLine) break;
                //stepOutで元の行に戻った場合、ステップして新しいメソッド呼び出しが行われなければ終了
                dbg.step();
                st = getStackTrace(dbg);
                if (st.getMethod(0).equals(locateMethod.substring(0, locateMethod.lastIndexOf("(")))
                        || st.getMethod(0).equals(locateMethod.substring(0, locateMethod.lastIndexOf("#") + 1) + "<init>")
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
        disableStdOut("    >> Probe Info: Search caller method.");
        dbg.stopAt(probeLines.getLeft());
        dbg.run(2000);
        enableStdOut();
        StackTrace st = getStackTrace(dbg);

        //callerMethodをシグニチャ付きで取得する
        Pair<Integer, String> caller = st.getCallerMethodAndCallLocation(1);
        //callerがnullの場合、callerMethodがtargetSrcDir内にない。（テストメソッドに戻った場合など）
        if(caller == null) System.out.println("    >> Probe Info: caller method is not found in Target Src: " + st.getMethod(1));
        return caller;
    }

    protected static class ProbeInfo implements Comparable<ProbeInfo>{
        LocalDateTime createAt;
        Location loc;
        String variableName;
        String value;
        int arrayIndex = -1;

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

        public Map<String, String> getvaluesFromLines(Pair<Integer, Integer> lines){
            Map<String, String> pis = new HashMap<>();
            for(List<ProbeInfo> l : piCollection.values()){
                for(ProbeInfo pi : l){
                    for(int i = lines.getLeft(); i <= lines.getRight(); i++) {
                        if (pi.loc.getLineNumber() == i) {
                            pis.put(pi.variableName, pi.value);
                        }
                    }
                }
            }
            return pis;
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
