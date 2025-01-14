package jisd.fl.probe;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.sun.jdi.VMDisconnectedException;
import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import jisd.debug.Location;
import jisd.debug.Point;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.util.JavaParserUtil;
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
        ProbeInfoCollection watchedValueCollection = extractInfoFromDebugger(variableInfo, sleepTime);
        List<ProbeInfo> watchedValues = watchedValueCollection.getPis(variableInfo.getVariableName(true, true));
        printWatchedValues(watchedValueCollection,variableInfo.getVariableName(true, true));
        //printWatchedValues(watchedValueCollection, null);
        ProbeResult result = null;
        try {
            result = searchProbeLine(watchedValues, variableInfo.getActualValue());
        }
        catch (RuntimeException e){
            System.out.println(e);
        }

        int count = 0;
        //debugで値を取れなかった場合、やり直す
        while(result == null){

            if(count > 2) break;
            disableStdOut("Retrying to run debugger");
            dbg.exit();
            sleepTime += 1000;
            watchedValueCollection = extractInfoFromDebugger(variableInfo, sleepTime);
            watchedValues = watchedValueCollection.getPis(variableInfo.getVariableName(true, true));
            printWatchedValues(watchedValueCollection,variableInfo.getVariableName(true, true));
            try {
                result = searchProbeLine(watchedValues, variableInfo.getActualValue());
            }
            catch (RuntimeException e){
                System.out.println(e);
                count += 1;
                if(count > 2) break;
                continue;
            }
            count += 1;
        }

        if(result == null) return null;

        result.setVariableInfo(variableInfo);
        //if(!result.isArgument()) result.setValuesInLine(watchedValueCollection.getValuesFromLines(result.getProbeLines()));
        if(!result.isArgument()) result.setValuesInLine(watchedValueCollection.getValuesAtSameTime(result.getCreateAt()));
        return result;
    }

    protected ProbeInfoCollection extractInfoFromDebugger(VariableInfo variableInfo, int sleepTime){
        disableStdOut("    >> Probe Info: Running debugger and extract watched info.");
        List<Integer> canSetLines = getCanSetLineByJP(variableInfo);
        String dbgMain = variableInfo.getLocateClass();
        disableStdOut("[canSetLines] " + Arrays.toString(canSetLines.toArray()));
        List<Optional<Point>> watchPoints = new ArrayList<>();
        dbg = createDebugger(1000);
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
        watchedValues.considerNotDefinedVariable();
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


    private ProbeResult searchProbeLine(List<ProbeInfo> watchedValues, String actual){
        System.out.println("    >> Probe Info: Searching probe line.");
        ProbeInfo pi;
        boolean isFound = false;

        int probeLine = 0;
        String locationClass = null;

        //観測した値の中で、一番初めの時点でactualの値を取っているパターン
        //1. 初期化の時点でその値が代入されている。
        //2. その変数が引数由来で、かつメソッド内で上書きされていない。
        //3. throw内などブレークポイントが置けない行で、代入が行われている。 --> 未想定
        pi = watchedValues.get(0);
        if (actual.equals(pi.value)) {
            String variableName;
            //暫定的にprobe lineを最初に値が観測された地点にする。
            variableName = watchedValues.get(0).variableName;
            probeLine = watchedValues.get(0).loc.getLineNumber();
            locationClass = watchedValues.get(0).loc.getClassName();
            return resultIfNotAssigned(probeLine, locationClass, variableName, pi.createAt, probeLine);
        }

        //観測した値の中で、途中からでactualの値を取るようになったパターン
        //次の場合は一番初めの時点でactualの値を取っているパターンと同じ扱い。
        //probe lineがmethodの初めの行
        //そうでない場合、値がactualになった行の前に観測した行が、実際に値を変更した行(probe line)
        for (int i = 1; i < watchedValues.size(); i++) {
            pi = watchedValues.get(i);
            if (actual.equals(pi.value)) {
                //methodの始まりの行か判定
                boolean isBeginLine;
                probeLine = pi.loc.getLineNumber();
                locationClass = pi.loc.getClassName();
                String probeMethod;
                try {
                    probeMethod = StaticAnalyzer.getMethodNameFormLine(locationClass, probeLine);
                } catch (NoSuchFileException e) {
                    throw new RuntimeException(e);
                }
                BlockStmt bs = StaticAnalyzer.bodyOfMethod(probeMethod);

                isBeginLine = bs.getBegin().get().line - 1 <= probeLine && probeLine <= bs.getBegin().get().line + 1 ;
                if(isBeginLine){
                    String variableName = watchedValues.get(0).variableName;
                    return resultIfNotAssigned(probeLine, locationClass, variableName, pi.createAt, pi.loc.getLineNumber());
                }
                else {
                    pi = watchedValues.get(i - 1);
                    probeLine = pi.loc.getLineNumber();
                    locationClass = pi.loc.getClassName();
                    return resultIfAssigned(probeLine, locationClass, watchedValues.get(i).createAt, watchedValues.get(i).loc.getLineNumber());
                }
            }
        }

        throw new RuntimeException("There is no value which same to actual.");
    }

    private ProbeResult resultIfAssigned(int probeLine, String locationClass, LocalDateTime createAt, int watchedAt){
        //観測した値の中で、途中からでactualの値を取るようになったパターン
        //actualを取るようになった行が、methodの始まりの行の場合、
        //一番初めの時点でactualの値を取っているパターンと同じ扱い。
        //そうでない場合、値がactualになった行の前に観測した行が、実際に値を変更した行(probe line)
        ProbeResult result = new ProbeResult();
        String probeMethod;
        Pair<Integer, Integer> probeLines = null;
        Map<Integer, Pair<Integer, Integer>> rangeOfAllStmt;

        try {
            probeMethod = StaticAnalyzer.getMethodNameFormLine(locationClass, probeLine);
            rangeOfAllStmt = StaticAnalyzer.getRangeOfAllStatements(locationClass);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        probeLines = rangeOfAllStmt.getOrDefault(probeLine, Pair.of(probeLine, probeLine));
        result.setArgument(false);
        result.setLines(probeLines);
        result.setProbeMethod(probeMethod);
        result.setSrc(getProbeStatement(locationClass, probeLines));
        result.setCreateAt(createAt);
        result.setWatchedAt(watchedAt);
        return result;
    }

    private ProbeResult resultIfNotAssigned(int probeLine, String locationClass, String variableName, LocalDateTime createAt, int watchedAt){
        //観測した値の中で、一番初めの時点でactualの値を取っているパターン
        //1. 初期化の時点でその値が代入されている。
        //2. その変数が引数由来で、かつメソッド内で上書きされていない。
        //3. throw内などブレークポイントが置けない行で、代入が行われている。 --> 未想定
        ProbeResult result = new ProbeResult();
        String probeMethod;
        Pair<Integer, Integer> probeLines = null;

        //実行しているメソッドを取得
        try {
            probeMethod = StaticAnalyzer.getMethodNameFormLine(locationClass, probeLine);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        //1. 初期化の時点でその値が代入されている。
        //この場合、probeLineは必ずmethod内にいる。
        boolean isThereVariableDeclaration = false;
        BlockStmt bs = StaticAnalyzer.bodyOfMethod(probeMethod);
        List<VariableDeclarator> vds = bs.findAll(VariableDeclarator.class);
        for (VariableDeclarator vd : vds) {
            if (vd.getNameAsString().equals(variableName)) {
                isThereVariableDeclaration = true;
                probeLines = Pair.of(vd.getBegin().get().line, vd.getEnd().get().line);
                break;
            }
        }
        if (isThereVariableDeclaration) {
            result.setArgument(false);
            result.setLines(probeLines);
            result.setProbeMethod(probeMethod);
            result.setSrc(getProbeStatement(locationClass, probeLines));
            result.setCreateAt(createAt);
            result.setWatchedAt(watchedAt);
            return result;
        }

        //2. その変数が引数orフィールド由来で、かつメソッド内で上書きされていない
        //暫定的にprobeLinesを設定
        probeLines = Pair.of(probeLine, probeLine);
        result.setLines(probeLines);
        result.setProbeMethod(probeMethod);
        result.setWatchedAt(watchedAt);
        result.setArgument(true);
        return result;
    }

    //Statementのソースコードを取得
    protected String getProbeStatement(String locationClass, Pair<Integer, Integer> probeLines){
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

    protected void printWatchedValues(ProbeInfoCollection watchedValues, String variableName){
        //System.out.println("    >> [assigned line] " + Arrays.toString(assignLine.toArray()));
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
        }

        Pair<Integer, Integer> probeLines = result.getProbeLines();
        if(result.getSrc() != null) {
            String[] src = result.getSrc().split("\n");
            int l = 0;
            for (int i = probeLines.getLeft(); i <= probeLines.getRight(); i++) {
                System.out.println("    >> " + i + ": " + src[l++]);
            }
        }
    }

    protected Debugger createDebugger(int sleepTime) {
        if(dbg != null) dbg.exit();
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
                //dbgがbreak pointで止まらないかstepOutで次の行に移った場合終了
                if(dbg.loc() == null || dbg.loc().getLineNumber() != probingLine) break;
                //stepOutで元の行に戻った場合、ステップして新しいメソッド呼び出しが行われなければ終了
                dbg.step();
                st = getStackTrace(dbg);
                if (st.getMethod(0).equals(locateMethod.substring(0, locateMethod.lastIndexOf("(")))
                        || st.getMethod(0).equals(locateMethod.substring(0, locateMethod.lastIndexOf("#") + 1) + "<init>")
                        || st.getMethod(0).equals(callerMethod)) {
                    break;
                }
            }
            //debugがbreakpointに達しなかった場合は終了
            if(dbg.loc() == null) break;
            //すでにbreakpointにいる場合はスキップしない
            if(!methodCallingLines.contains(dbg.loc().getLineNumber())) {
                dbg.cont(2000);
            }
        }

        enableStdOut();
        return calleeMethods;
    }

    protected Pair<Integer, String> getCallerMethod(int watchedAt, VariableInfo vi) {
        dbg = createDebugger(500);
        dbg.setMain(vi.getLocateClass());
        disableStdOut("    >> Probe Info: Search caller method.");
        Optional<Point> p = dbg.stopAt(watchedAt);
        dbg.run(2000);
        // probe対象がactualの値を取っているか確認
        if(p.isPresent() && p.get().getResults(vi.getVariableName(true, false)).isPresent()){
            DebugResult dr = p.get().getResults(vi.getVariableName(true, false)).get();
            List<ProbeInfo> pis = jiProcessor.getValuesFromDebugResult(vi, dr);
            int index = 0;
            if(vi.isArray()) index = vi.getArrayNth();
            if (!pis.get(index).value.equals(vi.getActualValue())) dbg.cont(1000);
        }
        else {
            throw new RuntimeException("Cannot watch Probe target.");
        }
        enableStdOut();
        StackTrace st = getStackTrace(dbg);
        //callerMethodをシグニチャ付きで取得する
        Pair<Integer, String> caller = st.getCallerMethodAndCallLocation(1);
        //callerがnullの場合、callerMethodがtargetSrcDir内にない。（テストメソッドに戻った場合など）
        if(caller == null) System.out.println("    >> Probe Info: caller method is not found in Target Src: " + st.getMethod(1));
        System.out.println("    >> [ CALLER METHOD ]");
        if(caller != null) {
            System.out.println("    >> " + caller.getRight());
        }
        else {
            System.out.println("    >> " );
        }
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
            //配列の場合[0]がついていないものも一緒に返す
            if(key.contains("[")){
                List<ProbeInfo> pis = new ArrayList<>();
                pis.addAll(piCollection.get(key));
                List<ProbeInfo> tmp = piCollection.get(key.split("\\[")[0]);
                if(tmp != null) pis.addAll(tmp);
                pis.sort(ProbeInfo::compareTo);
                return pis;
            }
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
        public Map<String, String> getValuesAtSameTime(LocalDateTime createAt){
            Map<String, String> pis = new HashMap<>();
            for(List<ProbeInfo> l : piCollection.values()){
                for(ProbeInfo pi : l){
                    if(pi.createAt.equals(createAt)) {
                        pis.put(pi.variableName, pi.value);
                    }
                }
            }
            return pis;
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

        //値の宣言行など、実行はされているが値が定義されていない行も
        //実行されていることを認識するために、定義されていない行の値は"not defined"として埋める。
        private void considerNotDefinedVariable(){
            Set<Pair<LocalDateTime, Integer>> executedLines = new HashSet<>();
            for(List<ProbeInfo> pis : piCollection.values()){
                for(ProbeInfo pi : pis){
                    executedLines.add(Pair.of(pi.createAt, pi.loc.getLineNumber()));
                }
            }

            for(List<ProbeInfo> pis : piCollection.values()){
                String variableName = pis.get(0).variableName;
                Set<Pair<LocalDateTime, Integer>> executedLinesInThisPis = new HashSet<>();
                for(ProbeInfo pi : pis){
                    executedLinesInThisPis.add(Pair.of(pi.createAt, pi.loc.getLineNumber()));
                }

                Set<Pair<LocalDateTime, Integer>> notExecutedLines = new HashSet<>();
                //変数が配列で、[]あり、なしのどちらかが存在するときは含めない
                //[]ありの場合
                if(variableName.contains("[")){
                   // continue;
                    Set<Pair<LocalDateTime, Integer>> executedLinesOfWithoutBracket = new HashSet<>();
                    if(getPis(variableName.split("\\[")[0]) != null) {
                        for (ProbeInfo pi : getPis(variableName.split("\\[")[0])) {
                            executedLinesOfWithoutBracket.add(Pair.of(pi.createAt, pi.loc.getLineNumber()));
                        }
                    }

                    for(Pair<LocalDateTime, Integer> execline : executedLines){
                        if(!executedLinesInThisPis.contains(execline) &&
                                !executedLinesOfWithoutBracket.contains(execline)) notExecutedLines.add(execline);
                    }
                }
                //[]なしの場合
                else {
                    boolean isArray = false;
                    for(String varName : piCollection.keySet()){
                        if(varName.contains("[") && varName.split("\\[")[0].equals(variableName)){
                            isArray = true;
                        }
                    }
                    if (isArray){
                       // continue;
                        Set<Pair<LocalDateTime, Integer>> executedLinesOfWithBracket = new HashSet<>();
                        if(getPis(variableName + "[0]") != null) {
                            for (ProbeInfo pi : getPis(variableName + "[0]")) {
                                executedLinesOfWithBracket.add(Pair.of(pi.createAt, pi.loc.getLineNumber()));
                            }
                        }

                        for(Pair<LocalDateTime, Integer> execline : executedLines){
                            if(!executedLinesInThisPis.contains(execline) &&
                                    !executedLinesOfWithBracket.contains(execline)) notExecutedLines.add(execline);
                        }
                    }
                    else {
                        //配列でない場合
                        for (Pair<LocalDateTime, Integer> execline : executedLines) {
                            if (!executedLinesInThisPis.contains(execline)) notExecutedLines.add(execline);
                        }
                    }
                }

                for(Pair<LocalDateTime, Integer> notExecline : notExecutedLines){
                    LocalDateTime createAt = notExecline.getLeft();
                    Location pisLoc = pis.get(0).loc;
                    Location loc = new Location(pisLoc.getClassName(), pisLoc.getMethodName(), notExecline.getRight(), "No defined");
                    pis.add(new ProbeInfo(createAt, loc, variableName, "Not defined"));
                }
            }
        }

        public void print(String key){
            for(ProbeInfo pi : getPis(key)) {
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