package jisd.fl.probe;

import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.sun.jdi.*;
import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import jisd.debug.Location;
import jisd.debug.Point;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.util.analyze.JavaParserUtil;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.analyze.CodeElement;
import jisd.fl.util.analyze.StaticAnalyzer;
import jisd.fl.util.TestUtil;
import jisd.info.ClassInfo;
import jisd.info.StaticInfoFactory;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONException;

import java.io.*;
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
        this.dbg = createDebugger();
        this.jiProcessor = new JisdInfoProcessor();

        this.targetSif = new StaticInfoFactory(targetSrcDir, targetBinDir);
        this.testSif = new StaticInfoFactory(testSrcDir, testBinDir);
    }

    public static String shortMethodName(String fullMethodName){
        String name = fullMethodName.split("\\(")[0];
        String args = fullMethodName.substring(fullMethodName.indexOf("(")+1, fullMethodName.indexOf(")"));
        List<String> argList = new ArrayList<>(List.of(args.split(", ")));
        List<String> shortArgList = new ArrayList<>();
        for(String arg : argList){
            if(arg.contains(".") || arg.contains("/")) {
                String[] splitArgs = arg.split("[./]");
                shortArgList.add(splitArgs[splitArgs.length - 1]);
            }
            else {
                shortArgList.add(arg);
            }
        }
        StringBuilder shortMethod = new StringBuilder(name + "(");
        for(int i = 0; i < shortArgList.size(); i++){
            String shortArg = shortArgList.get(i);
            shortMethod.append(shortArg);
            if (i != shortArgList.size() - 1) shortMethod.append(", ");
        }
        shortMethod.append(")");
        return shortMethod.toString();
    }

    //一回のprobeを行う
    //条件を満たす行の情報を返す
    protected ProbeResult probing(int sleepTime, VariableInfo variableInfo){
        ProbeInfoCollection watchedValueCollection = extractInfoFromDebugger(variableInfo, sleepTime);
        List<ProbeInfo> watchedValues = watchedValueCollection.getPis(variableInfo.getVariableName(true, true));
        //printWatchedValues(watchedValueCollection,variableInfo.getVariableName(true, true));
        //printWatchedValues(watchedValueCollection, null);
        ProbeResult result = null;
        try {
            result = searchProbeLine(watchedValues, variableInfo.getActualValue(), variableInfo);
        }
        catch (RuntimeException e){
            System.out.println(e);
        }

        int count = 0;
        //debugで値を取れなかった場合、やり直す
        while(result == null || result.isNotFound()){

            if(count >= 5) break;
            disableStdOut("Retrying to run debugger");
            sleepTime += 5000;
            watchedValueCollection = extractInfoFromDebugger(variableInfo, sleepTime);
            watchedValues = watchedValueCollection.getPis(variableInfo.getVariableName(true, true));
            printWatchedValues(watchedValueCollection,variableInfo.getVariableName(true, true));
            try {
                result = searchProbeLine(watchedValues, variableInfo.getActualValue(), variableInfo);
            }
            catch (RuntimeException e){
                System.out.println(e);
                count += 1;
                if(count >= 5) break;
                continue;
            }
            count += 1;
        }

        //probe lineが特定できなかった場合
        if(result == null || result.isNotFound()) return null;

        result.setVariableInfo(variableInfo);
        //if(!result.isArgument()) result.setValuesInLine(watchedValueCollection.getValuesFromLines(result.getProbeLines()));
        if(!result.isArgument()) result.setValuesInLine(watchedValueCollection.getValuesAtSameTime(result.getCreateAt()));

        //free memory
        watchedValueCollection.clear();
        return result;
    }

    protected ProbeInfoCollection extractInfoFromDebugger(VariableInfo variableInfo, int sleepTime){
        disableStdOut("    >> Probe Info: Running debugger and extract watched info.");
        List<Integer> canSetLines = getCanSetLineByJP(variableInfo);
        String dbgMain = variableInfo.getLocateClass();
        //disableStdOut("[canSetLines] " + Arrays.toString(canSetLines.toArray()));
        List<Optional<Point>> watchPoints = new ArrayList<>();
        dbg = createDebugger();
        //set watchPoint
        dbg.setMain(dbgMain);
        for (int line : canSetLines) {
            watchPoints.add(dbg.watch(line));
        }

        //run Test debugger
        try {
            dbg.run(sleepTime);
        } catch (VMDisconnectedException | InvalidStackFrameException e) {
            //throw new RuntimeException(e);
            System.err.println(e);
        }
        catch (NullPointerException ignored){
        }

        enableStdOut();
        ProbeInfoCollection watchedValues = jiProcessor.getInfoFromWatchPoints(watchPoints, variableInfo);
        dbg.exit();
        dbg.clearResults();
        watchedValues.considerNotDefinedVariable();
        watchedValues.sort();
        return watchedValues;
    }

//    //JisdのcanSetは同じ名前のローカル変数が出てきたときに、前のやつが上書きされる。
//    private List<Integer> getCanSetLine(VariableInfo variableInfo) {
//        Set<Integer> canSetSet = new HashSet<>();
//        List<Integer> canSetLines;
//        ClassInfo ci = createClassInfo(variableInfo.getLocateClass());
//
//        if(variableInfo.isField()) {
//            Map<String, ArrayList<Integer>> canSet = ci.field(variableInfo.getVariableName()).canSet();
//            for (List<Integer> lineWithVar : canSet.values()) {
//                canSetSet.addAll(lineWithVar);
//            }
//        }
//        else {
//            //ci.methodは引数に内部で定義されたクラスのインスタンスを含む場合、フルパスが必要
//            //ex.) SimplexTableau(org/apache/commons/math/optimization/linear/LinearObjectiveFunction, java.util.Collection, org/apache/commons/math/optimization/GoalType, boolean, double)
//
//            //ブレークポイントが付けられるのに含まれてない行が発生。
//            //throws で囲まれた行はブレークポイントが置けない。
//            String fullMethodName = StaticAnalyzer.fullNameOfMethod(variableInfo.getLocateMethod(), ci);
//            MethodInfo mi = ci.method(fullMethodName);
////            for(String localName : mi.localNames()) {
////                LocalInfo li = mi.local(localName);
////                canSetSet.addAll(li.canSet());
////            }
//            LocalInfo li = mi.local(variableInfo.getVariableName());
//            for(int canSet : li.canSet()){
//                canSetSet.add(canSet - 1);
//                canSetSet.add(canSet);
//                canSetSet.add(canSet + 1);
//            }
//        }
//        canSetLines = new ArrayList<>(canSetSet);
//        canSetLines.sort(Comparator.naturalOrder());
//        return canSetLines;
//    }

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


    private ProbeResult searchProbeLine(List<ProbeInfo> watchedValues, String actual, VariableInfo vi){
        System.out.println("    >> Probe Info: Searching probe line.");
        ProbeInfo pi;

        //代入行の特定
        //unaryExpr(ex a++)も含める
        Set<Integer> assignedLine = new HashSet<>();
        List<AssignExpr> aes;
        List<UnaryExpr> ues;
        if(vi.isField()) {
            try {
                CompilationUnit unit = JavaParserUtil.parseClass(vi.getLocateClass());
                aes = unit.findAll(AssignExpr.class);
                ues = unit.findAll(UnaryExpr.class, (n)-> {
                    UnaryExpr.Operator ope = n.getOperator();
                    return ope == UnaryExpr.Operator.POSTFIX_DECREMENT ||
                            ope == UnaryExpr.Operator.POSTFIX_INCREMENT ||
                            ope == UnaryExpr.Operator.PREFIX_DECREMENT ||
                            ope == UnaryExpr.Operator.PREFIX_INCREMENT;
                });
            } catch (NoSuchFileException e) {
                throw new RuntimeException(e);
            }
        }
        else {
            BlockStmt bs = JavaParserUtil.extractBodyOfMethod(vi.getLocateMethod(true));
            aes = bs.findAll(AssignExpr.class);
            ues = bs.findAll(UnaryExpr.class, (n)-> {
                UnaryExpr.Operator ope = n.getOperator();
                return ope == UnaryExpr.Operator.POSTFIX_DECREMENT ||
                        ope == UnaryExpr.Operator.POSTFIX_INCREMENT ||
                        ope == UnaryExpr.Operator.PREFIX_DECREMENT ||
                        ope == UnaryExpr.Operator.PREFIX_INCREMENT;
            });
        }
        for(AssignExpr ae : aes){
            //対象の変数に代入されているか確認
            Expression target = ae.getTarget();
            String targetName;
            if(target.isArrayAccessExpr()) {
                targetName = target.asArrayAccessExpr().getName().toString();
            }
            else if(target.isFieldAccessExpr()){
                targetName = target.asFieldAccessExpr().getName().toString();
            }
            else {
                targetName = target.toString();
            }

            if(targetName.equals(vi.getVariableName())) {
                if(vi.isField() == target.isFieldAccessExpr())
                    for(int i = ae.getBegin().get().line; i <= ae.getEnd().get().line; i++) {
                        assignedLine.add(i);
                    }
            }
        }
        for(UnaryExpr ue : ues){
            //対象の変数に代入されているか確認
            Expression target = ue.getExpression();
            String targetName = target.toString();

            if(targetName.equals(vi.getVariableName())) {
                if(vi.isField() == target.isFieldAccessExpr())
                    for(int i = ue.getBegin().get().line; i <= ue.getEnd().get().line; i++) {
                        assignedLine.add(i);
                    }
            }
        }

        //代入後にactualの値に変化している行の特定
        List<ProbeInfo> changeToActualLines = new ArrayList<>();
        for(int i = 0; i < watchedValues.size() - 1; i++){
            ProbeInfo watchingLine = watchedValues.get(i);
            if(!assignedLine.contains(watchingLine.loc.getLineNumber())) continue;
            ProbeInfo afterAssignLine = watchedValues.get(i+1);
            if(!afterAssignLine.value.equals(vi.getActualValue())) continue;
            changeToActualLines.add(watchingLine);
        }

        //実行された代入行が存在するパターン -->その中でさいごに実行された行がprobe line
        if(!changeToActualLines.isEmpty()) {
            changeToActualLines.sort(ProbeInfo::compareTo);
            ProbeInfo probeLineInfo = changeToActualLines.get(changeToActualLines.size() - 1);
            ProbeInfo afterAssignLine = watchedValues.get(watchedValues.indexOf(probeLineInfo));

            return resultIfAssigned(
                    probeLineInfo.loc.getLineNumber(),
                    vi.getLocateClass(),
                    probeLineInfo.createAt,
                    afterAssignLine.loc.getLineNumber());
        }

        //fieldは代入以外での値の変更を特定できない
        if(vi.isField()){
            System.err.println("Cannot find probe line of field. [FIELD NAME] " + vi.getVariableName());
            ProbeResult result = new ProbeResult();
            result.setNotFound(true);
            return result;
        }

        //実行された代入行がないパターン
        //初めて値がactualと一致した行の前に実行された行を暫定的にprobe lineとする。
        for (int i = 0; i < watchedValues.size(); i++) {
            pi = watchedValues.get(i);
            if (actual.equals(pi.value)) {
                return resultIfNotAssigned(
                        watchedValues.get(i == 0 ? i : i-1).loc.getLineNumber(),
                        vi.getLocateClass(),
                        vi.getVariableName(false, false),
                        pi.createAt,
                        pi.loc.getLineNumber());
            }
        }

        throw new RuntimeException("There is no value which same to actual.");
    }

    private ProbeResult resultIfAssigned(int probeLine, String locationClass, LocalDateTime createAt, int watchedAt){
        //代入によって変数がactualの値を取るようになったパターン
        //値がactualになった行の前に観測した行が、実際に値を変更した行(probe line)
        ProbeResult result = new ProbeResult();
        String probeMethod;
        Pair<Integer, Integer> probeLines = null;

        try {
            probeMethod = StaticAnalyzer.getMethodNameFormLine(locationClass, probeLine);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        CodeElement ce = new CodeElement(locationClass);
        Range probeRange = StaticAnalyzer.getRangeOfStatement(ce, probeLine).orElse(null);
        probeLines = probeRange != null ? Pair.of(probeRange.begin.line, probeRange.end.line) : Pair.of(probeLine, probeLine);
        result.setArgument(false);
        result.setLines(probeLines);
        result.setProbeMethod(probeMethod);
        result.setSrc(getProbeStatement(locationClass, probeLines));
        result.setCreateAt(createAt);
        result.setWatchedAt(watchedAt);
        return result;
    }

    private ProbeResult resultIfNotAssigned(int probeLine, String locationClass, String variableName, LocalDateTime createAt, int watchedAt){
        //代入以外の要因で変数がactualの値をとるようになったパターン
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
        BlockStmt bs = JavaParserUtil.extractBodyOfMethod(probeMethod);
        List<VariableDeclarator> vds = bs.findAll(VariableDeclarator.class);
        for (VariableDeclarator vd : vds) {
            if (vd.getNameAsString().equals(variableName) &&
            vd.getBegin().get().line <= probeLine && probeLine <= vd.getEnd().get().line) {
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

        //2. その変数が引数由来で、かつメソッド内で上書きされていない
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
        if(locationClass.contains("$")) locationClass = locationClass.split("\\$")[0];
        String targetSrcDir = PropertyLoader.getProperty("targetSrcDir");
        String path = targetSrcDir + "/" + locationClass.replace('.', '/') + ".java";
        List<String> src = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String string = reader.readLine();
            while (string != null){
                src.add(string);
                string = reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        StringBuilder stmt = new StringBuilder();
        for(int i = probeLines.getLeft(); i <= probeLines.getRight(); i++) {
            stmt.append(src.get(i - 1));
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
        System.setErr(nop);
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



    protected Debugger createDebugger() {
        return createDebugger(assertInfo.getTestMethodName());
    }

    protected  Debugger createDebugger(String targetMethod){
        //使い終わったTestLauncherのプロセスが生き残り続ける問題の対策
        if(dbg != null){
            ThreadReference tr = null;
            try {
                tr = dbg.thread();
            } catch(VMDisconnectedException ignored) {
            }

            try {
                if( tr != null && tr.isAtBreakpoint() ){
                    dbg.cont();
                }
            } catch(VMDisconnectedException ignored) {
            }
        dbg.exit();
        }

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return TestUtil.testDebuggerFactory(targetMethod);
    }


    //動的解析
    //メソッド実行時の実際に呼び出されているメソット群を返す
    //locateMethodはフルネーム、シグニチャあり
    //Assert文はなぜかミスる -> とりあえずはコメントアウトで対応
    protected Set<String> getAllCalleeMethods(String testMethod, String locateMethod){
        System.out.println("    >> Probe Info: Collecting all callee methods.");
        System.out.println("    >> Probe Info: Target method --> " + locateMethod);
        disableStdOut("");

        Set<String> calleeMethods = new HashSet<>();
        String locateClass = locateMethod.split("#")[0];
        List<Integer> methodCallingLines = null;
        CodeElement tmpCd = new CodeElement(locateMethod);
        try {
            methodCallingLines = StaticAnalyzer.getMethodCallingLine(tmpCd);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        Debugger dbg = createDebugger(testMethod);
        dbg.setMain(locateClass);
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
        String calleeMethod;
        //>> Debugger Info: The target VM thread is not suspended now.の時　空のcalleeMethodsを返す
        if(dbg.loc() == null) {
            enableStdOut();
            return calleeMethods;
        }

        for(int i = 0; i < methodCallingLines.size(); i++) {
            if(dbg.loc() == null) break;
            while (true) {
                dbg.step();
                calleeMethod = getMethodFromStackFrame(getStackFrame(dbg, 0));
                dbg.stepOut();
                calleeMethods.add(calleeMethod);

                //終了判定
                //ステップして新しいメソッド呼び出しが行われなければ終了
                String nowLocateMethod = getMethodFromStackFrame(getStackFrame(dbg, 0));
                if(nowLocateMethod.equals(locateMethod)) break;
            }
            //debugがbreakpointに達しなかった場合は終了
            if(dbg.loc() == null) break;
            //すでにbreakpointにいる場合はスキップしない
            if(!methodCallingLines.contains(dbg.loc().getLineNumber())) {
                dbg.cont(50);
            }
        }

        enableStdOut();
        return calleeMethods;
    }

    //動的解析
    //メソッド実行時の実際に特定の行で呼び出されているメソット群を返す
    //locateMethodはフルネーム、シグニチャあり
    //Assert文はなぜかミスる -> とりあえずはコメントアウトで対応
    protected Set<String> getCalleeMethods(String testMethod, String locateMethod, Pair<Integer, Integer> lines){
        System.out.println("    >> Probe Info: Collecting callee methods.");
        System.out.println("    >> Probe Info: Target method --> " + locateMethod);
        disableStdOut("");

        Set<String> calleeMethods = new HashSet<>();
        String locateClass = locateMethod.split("#")[0];
        Set<Integer> linesStopAt = new HashSet<>();
        for(int i = lines.getLeft(); i <= lines.getRight(); i++){
            linesStopAt.add(i);
        }

        Debugger dbg = createDebugger(testMethod);
        dbg.setMain(locateClass);
        for(int l : linesStopAt){
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
        String calleeMethod;
        StackFrame sf;
        //>> Debugger Info: The target VM thread is not suspended now.の時　空のcalleeMethodsを返す
        if(dbg.loc() == null) {
            enableStdOut();
            return calleeMethods;
        }
        for(int i = 0; i < linesStopAt.size(); i++) {
            //最大で20まで
            int j = 0;
            while (true) {
                if(j >= 20) break;
                //終了判定
                //stepで元の行に戻った場合、ステップして新しいメソッド呼び出しが行われなければ終了
                dbg.step();
                if(dbg.loc() == null) break;
                String nowLocateMethod = getMethodFromStackFrame(getStackFrame(dbg, 0));
                if(nowLocateMethod.equals(locateMethod)) break;

                calleeMethod = getMethodFromStackFrame(getStackFrame(dbg, 0));
                dbg.stepOut();
                calleeMethods.add(calleeMethod);
                j += 1;
            }
            //debugがbreakpointに達しなかった場合は終了
            if(dbg.loc() == null) break;
            //すでにbreakpointにいる場合はスキップしない
            if(!linesStopAt.contains(dbg.loc().getLineNumber())) {
                dbg.cont(50);
                i++;
            }
        }


        try{
            dbg.cont(10);
        }
        catch (InvalidStackFrameException ignored){}
        dbg.exit();
        enableStdOut();
        return calleeMethods;
    }


    protected Pair<Integer, String> getCallerMethod(int watchedAt, VariableInfo vi) {
        dbg = createDebugger();
        dbg.setMain(vi.getLocateClass());
        disableStdOut("    >> Probe Info: Search caller method.");
        Optional<Point> p = dbg.stopAt(watchedAt);
        dbg.run(2000);
        // probe対象がactualの値を取っているか確認
        while(true) {
            if (p.isPresent() && p.get().getResults(vi.getVariableName(true, false)).isPresent()) {
                DebugResult dr = p.get().getResults(vi.getVariableName(true, false)).get();
                List<ProbeInfo> pis = jiProcessor.getValuesFromDebugResult(vi, dr);
                int index = 0;
                if (vi.isArray()) index = vi.getArrayNth();
                if (pis.get(index).value.equals(vi.getActualValue())) {
                    break;
                }
                else {
                    p.get().clearDebugResults();
                    dbg.cont(50);
                }
            } else {
                //throw new RuntimeException("Cannot watch Probe target.");
                System.err.println("Cannot watch Probe target.");
                dbg.cont(50);
            }
        }
//        //callerMethodをシグニチャ付きで取得する
        StackFrame sf = getStackFrame(dbg, 1);
        int callLine = sf.location().lineNumber();
        String callerMethod = getMethodFromStackFrame(sf);
        Pair<Integer, String> caller
                = Pair.of(callLine, callerMethod);
        System.out.println("    >> [ CALLER METHOD ]");
        System.out.println("    >> " + caller.getRight());

        dbg.cont(10);
        dbg.exit();
        enableStdOut();
        return caller;
    }

    private StackFrame getStackFrame(Debugger dbg, int depth){
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        try {
            return dbg.thread().frame(depth);
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            return null;
        }
    }

    private String getMethodFromStackFrame(StackFrame sf){
        String methodName = sf.location().method().toString();
        if(methodName.contains("<init>")){
            String shortName = methodName.substring(0, methodName.lastIndexOf("<") - 1);
            shortName = shortName.substring(shortName.lastIndexOf(".") + 1);
            methodName =  methodName.replace("<init>", shortName);
        }
        methodName = shortMethodName(methodName);
        StringBuilder sb = new StringBuilder(methodName);
        sb.setCharAt(sb.lastIndexOf("."), '#');
        return sb.toString();
    }

    protected ClassInfo createClassInfo(String className){
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
            List<ProbeInfo> pis = getPis(key);
            if(pis == null) throw new RuntimeException("key " + key + " is not exist.");
            for(ProbeInfo pi : pis) {
                System.out.println("    >> " + pi);
            }
        }

        public void printAll(){
            for(String key : piCollection.keySet()){
                print(key);
            }
        }

        public void clear(){
            for(List<AbstractProbe. ProbeInfo> l : piCollection.values()){
                l = null;
            }
        }
    }
}