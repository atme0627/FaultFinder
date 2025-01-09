package jisd.fl.probe;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.sun.jdi.VMDisconnectedException;
import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import jisd.debug.Location;
import jisd.debug.Point;
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
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
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
    JisdInfoProcessor jiProcessor;
    static PrintStream stdOut = System.out;
    static PrintStream stdErr = System.err;


    public AbstractProbe(FailedAssertInfo assertInfo) {
        String targetSrcDir = PropertyLoader.getProperty("targetSrcDir");
        String targetBinDir = PropertyLoader.getProperty("targetBinDir");
        String testSrcDir = PropertyLoader.getProperty("testSrcDir");
        String testBinDir = PropertyLoader.getProperty("testBinDir");

        this.assertInfo = assertInfo;
        this.dbg = createDebugger(500);
        this.jiProcessor = new JisdInfoProcessor();

        this.targetSif = new StaticInfoFactory(targetSrcDir, targetBinDir);
        this.testSif = new StaticInfoFactory(testSrcDir, testBinDir);
    }

    //一回のprobeを行う
    //条件を満たす行の情報を返す
    protected ProbeResult probing(int sleepTime, VariableInfo variableInfo){

        List<Integer> assignLines = null;
        try {
            assignLines = StaticAnalyzer.getAssignLine(
                    variableInfo.getLocateClass(),
                    variableInfo.getVariableName(true, false));
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        ProbeInfoCollection watchedValueCollection = extractInfoFromDebugger(variableInfo, sleepTime);
        List<ProbeInfo> watchedValues = watchedValueCollection.getPis(variableInfo.getVariableName(true, true));
        //printWatchedValues(watchedValueCollection,variableInfo.getVariableName(true, true) , assignLines);
        //printWatchedValues(watchedValueCollection, null, assignLines);
        ProbeResult result = searchProbeLine(watchedValues, assignLines, variableInfo.getActualValue());

        int count = 0;
        //debugが値を取れなかった場合、やり直す
        while(result == null){

            if(count > 1) throw new RuntimeException("FAILED");
            disableStdOut("Retrying to run debugger");
            dbg.exit();
            sleepTime += 1000;
            watchedValueCollection = extractInfoFromDebugger(variableInfo, sleepTime);
            watchedValues = watchedValueCollection.getPis(variableInfo.getVariableName(true, true));
            result = searchProbeLine(watchedValues, assignLines, variableInfo.getActualValue());
            count += 1;
        }

        result.setVariableInfo(variableInfo);
        if(!result.isArgument()) result.setValuesInLine(watchedValueCollection.getValuesFromLines(result.getProbeLines()));
        return result;
    }

    protected ProbeInfoCollection extractInfoFromDebugger(VariableInfo variableInfo, int sleepTime){
        disableStdOut("    >> Probe Info: Running debugger and extract watched info.");
        List<Integer> canSetLines = getCanSetLineByJP(variableInfo);
        String dbgMain = variableInfo.getLocateClass();
        disableStdOut("[canSetLines] " + Arrays.toString(canSetLines.toArray()));
        List<Optional<Point>> watchPoints = new ArrayList<>();
        dbg = createDebugger(500);
        //set watchPoint
        dbg.setMain(dbgMain);
        for (int line : canSetLines) {
            watchPoints.add(dbg.watch(line));
        }

        //run Test debugger
        try {

            dbg.run(sleepTime);
        } catch (VMDisconnectedException e) {
            System.err.println(e);
        }
        dbg.exit();

        enableStdOut();
        ProbeInfoCollection watchedValues = jiProcessor.getInfoFromWatchPoints(watchPoints, variableInfo);
        watchedValues.sort();
        return watchedValues;
    }

    //JisdのcanSetは同じ名前のローカル変数が出てきたときに、前のやつが上書きされる。
    private List<Integer> getCanSetLine(VariableInfo variableInfo) {
        Set<Integer> canSetSet = new HashSet<>();
        List<Integer> canSetLines;
        ClassInfo ci = createClassInfo(variableInfo.getLocateClass());

        if(variableInfo.isField()) {
            Map<String, ArrayList<Integer>> canSet = ci.field(variableInfo.getVariableName()).canSet();
            for (List<Integer> lineWithVar : canSet.values()) {
                canSetSet.addAll(lineWithVar);
            }
        }
        else {
            //ci.methodは引数に内部で定義されたクラスのインスタンスを含む場合、フルパスが必要
            //ex.) SimplexTableau(org/apache/commons/math/optimization/linear/LinearObjectiveFunction, java.util.Collection, org/apache/commons/math/optimization/GoalType, boolean, double)

            //ブレークポイントが付けられるのに含まれてない行が発生。
            //throws で囲まれた行はブレークポイントが置けない。
            String fullMethodName = StaticAnalyzer.fullNameOfMethod(variableInfo.getLocateMethod(), ci);
            MethodInfo mi = ci.method(fullMethodName);
//            for(String localName : mi.localNames()) {
//                LocalInfo li = mi.local(localName);
//                canSetSet.addAll(li.canSet());
//            }
            LocalInfo li = mi.local(variableInfo.getVariableName());
            for(int canSet : li.canSet()){
                canSetSet.add(canSet - 1);
                canSetSet.add(canSet);
                canSetSet.add(canSet + 1);
            }
        }
        canSetLines = new ArrayList<>(canSetSet);
        canSetLines.sort(Comparator.naturalOrder());
        return canSetLines;
    }

    private List<Integer> getCanSetLineByJP(VariableInfo variableInfo) {
        List<Integer> canSetLines;
        if(variableInfo.isField()) {
            canSetLines =  new ArrayList<>(StaticAnalyzer.canSetLineOfClass(variableInfo.getLocateClass(), variableInfo.getVariableName()));
        }
        else {
            canSetLines =  new ArrayList<>(StaticAnalyzer.canSetLineOfMethod(variableInfo.getLocateMethod(true), variableInfo.getVariableName()));
        }

        canSetLines.sort(Comparator.naturalOrder());
        return canSetLines;
    }


    private ProbeResult searchProbeLine(List<ProbeInfo> watchedValues, List<Integer> assignedLine, String actual){
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

        //新しいものから探して最後にexecutedAssignedLines内の値に一致したものがobjective
        // 変更: 古いものから調べて初めにexecutedAssignedLines内の値に一致したものがobjective
        //最新の値がactualと一致しない場合もある。
        //初めてactualと一致するまで遡る。
        boolean isFound = false;
        int probeLine = 0;
        String locationClass = null;
        if(!afterAssignedLines.isEmpty()) {
            //watchedValues.sort(Comparator.reverseOrder());
            //watchedValuesの最後の要素がassignLineになることはない(はず)
            boolean matched = false;
            for(int i = 1; i < watchedValues.size(); i++){
                ProbeInfo pi = watchedValues.get(i);
                //piがafterAssignedLineだった場合
                //if(afterAssignedLines.contains(pi.loc.getLineNumber()) && actual.equals(pi.value)){
                if(actual.equals(pi.value)){
                    //その直前のProbeInfoが正解の行
                    pi = watchedValues.get(i - 1);
                    isFound = true;
                    probeLine = pi.loc.getLineNumber();
                    locationClass = pi.loc.getClassName();
                    break;
                }

//                //probe対象変数がactualの値を取らなくなった場合、（時間的に）直後のpiが正解の行
//                if(pi.value.equals(actual)) {
//                    matched = true;
//                }
//                else {
////                    if(i == 0) {
////                    //TODO: 値が全然違う時がある。観測値が実行毎に変わる場合あり?
////                        enableStdOut();
////                        System.out.println("    >> Probe Info: failed to watch value. ");
////                        return null;
////                    }
//                    if(matched) {
//                        pi = watchedValues.get(i - 1);
//                        isFound = true;
//                        probeLine = pi.loc.getLineNumber();
//                        locationClass = pi.loc.getClassName();
//                        break;
//                    }
//                    else {
//                        continue;
//                    }
//                }
            }
        }

        if(!isFound) {
            //ここに来た時点で、値はこのメソッド内で変更されていない
            //初期化時の値か渡された値がそもそも間違い

            //-->> ブレークポイントが置けない行(ex. throw内)で代入が行われている場合があるため、そうでもない

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
                    if(vd.getNameAsString().equals(variableName)
                            //&& vd.getBegin().get().line <= probeLine && probeLine <= vd.getEnd().get().line
                    ){
                        isThereVariableDeclaration = true;
                        //probeLineが必ずしも代入行にならない
                        probeLine = vd.getBegin().get().line;
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

        //シグニチャも含める
        result.setLines(probeLines);
        result.setProbeMethod(probeMethod);
        result.setSrc(getProbeStatement(locationClass, probeLines));
        return result;
    }

    //Statementのソースコードを取得
    private String getProbeStatement(String locationClass, Pair<Integer, Integer> probeLines){
        ClassInfo ci = createClassInfo(locationClass);
        String[] src = ci.src().split("\n");
        StringBuilder stmt = new StringBuilder();
        for(int i = probeLines.getLeft(); i <= probeLines.getRight(); i++) {
            stmt.append(src[i - 1]);
            stmt.append("\n");
        }
        return stmt.toString();
    }

    protected void disableStdOut(String msg){
        enableStdOut();
        if(!msg.isEmpty()) System.out.println(msg);
        PrintStream nop = new PrintStream(new OutputStream() {
            public void write(int b) { /* noop */ }
        });
        System.setOut(nop);
    }

    protected void enableStdOut(){
        System.setOut(stdOut);
    }

    protected void printWatchedValues(ProbeInfoCollection watchedValues, String variableName, List<Integer> assignLine){
        System.out.println("    >> [assigned line] " + Arrays.toString(assignLine.toArray()));
        if(variableName != null) {
            watchedValues.print(variableName);
        }
        else {
            watchedValues.printAll();
        }
    }

    protected void printProbeStatement(ProbeResult result){
        System.out.println("    >> [PROBE LINES]");
        if(result.isArgument()) {
            System.out.println("    >> Variable defect is derived from caller method. ");
            if(result.getCallerMethod() != null) {
                System.out.println("    >> [CALLER] " + result.getCallerMethod().getRight());
                System.out.println("    >> [ LINE] " + result.getCallerMethod().getLeft());
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

    protected Debugger createDebugger(int sleepTime) {
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return TestUtil.testDebuggerFactory(assertInfo.getTestMethodName());
    }


    //動的解析
    //メソッド実行時の実際に呼び出されているメソット群を返す
    //locateMethodはフルネーム、シグニチャあり
    //Assert文はなぜかミスる -> とりあえずはコメントアウトで対応
    protected MethodCollection getCalleeMethods(String testMethod,
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
        try {
            dbg.run(2000);
        }
        //メモリエラーなどでVMが止まる場合、calleeは取れない
        catch (VMDisconnectedException e) {
            System.err.println(e);
            enableStdOut();
            return calleeMethods;
        }
        //callerMethodを取得
        st = getStackTrace(dbg);
        //>> Debugger Info: The target VM thread is not suspended now.の時　からのcalleeMethodsを返す
        if(st.isEmpty()) {
            enableStdOut();
            return calleeMethods;
        }
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

    protected Pair<Integer, String> getCallerMethod(Pair<Integer, Integer> probeLines, String locateClass) {
        dbg = createDebugger(500);
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

    private StackTrace getStackTrace(Debugger dbg){
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos);
        PrintStream out = System.out;
        System.setOut(ps);
        bos.reset();
        dbg.where();
        System.setOut(out);

        return new StackTrace(bos.toString(), "");
    }

    private ClassInfo createClassInfo(String className){
        //mainがダメならtestを試す
        ClassInfo ci;
        try {
            ci = targetSif.createClass(className);
        }
        catch (JSONException e){
            ci = testSif.createClass(className);
        }
        return ci;
    }

    public static class ProbeInfo implements Comparable<ProbeInfo>{
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

    public static class ProbeInfoCollection {
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

        public Map<String, String> getValuesFromLines(Pair<Integer, Integer> lines){
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