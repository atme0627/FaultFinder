package jisd.fl.probe;

import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.sun.jdi.*;
import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import jisd.debug.Point;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.probe.record.TracedValue;
import jisd.fl.probe.record.TracedValueRecord;
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
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    //一回のprobeを行う
    //条件を満たす行の情報を返す
    protected ProbeResult probing(int sleepTime, VariableInfo variableInfo){
        //ターゲット変数が変更されうる行を観測し、全変数の情報を取得
        disableStdOut("    >> Probe Info: Running debugger and extract watched info.");
        TracedValueRecord tracedValues = traceVariableValues(variableInfo, sleepTime);

        //対象の変数に変更が起き、actualを取るようになった行（原因行）を探索
        List<TracedValue> watchedValues = tracedValues.filterByVariableName(variableInfo.getVariableName(true, true));
        System.out.println("    >> Probe Info: Searching probe line.");
        ProbeResult result = searchProbeLine(watchedValues, variableInfo);

        //probe lineが特定できなかった場合nullを返す
        if(result.isNotFound()) return null;

        //原因行で他に登場した値をセット
        if(!result.isArgument()) result.setValuesInLine(tracedValues.filterByCreateAt(result.getCreateAt()));

        return result;
    }

    protected TracedValueRecord traceVariableValues(VariableInfo variableInfo, int sleepTime){
        List<Integer> canSetLines = getCanSetLine(variableInfo);
        String dbgMain = variableInfo.getLocateClass();
        dbg = createDebugger();
        //set watchPoint
        dbg.setMain(dbgMain);
//        List<Optional<Point>> watchPoints =
//                canSetLines.stream()
//                        .map(dbg::watch)
//                        .collect(Collectors.toList());

        List<Optional<Point>> watchPoints = new ArrayList<>();
        for(int line : canSetLines){
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
        TracedValueRecord watchedValues = jiProcessor.getInfoFromWatchPoints(watchPoints, variableInfo);
        watchedValues.considerNotDefinedVariable();
        watchedValues.sort();
        dbg.exit();
        dbg.clearResults();
        return watchedValues;
    }

    private List<Integer> getCanSetLine(VariableInfo variableInfo) {
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


    private ProbeResult searchProbeLine(List<TracedValue> tracedValues, VariableInfo vi){
        //対象の変数に値の変化が起きている行の特定
        List<Integer> assignedLine = valueChangedLine(vi);

        //代入の実行後にactualの値に変化している行の特定(ない場合あり)
        List<TracedValue> changeToActualLines = valueChangedToActualLine(tracedValues, assignedLine, vi.getActualValue());

        //代入の実行後にactualの値に変化している行あり -> その中で最後に実行された行がprobe line
        if(!changeToActualLines.isEmpty()) {
            TracedValue causeLine = changeToActualLines.get(changeToActualLines.size() - 1);
            TracedValue afterAssignLine = tracedValues.get(tracedValues.indexOf(causeLine));

            return resultIfAssigned(
                    causeLine.loc.getLineNumber(),
                    vi.getLocateMethodElement(),
                    causeLine.createAt,
                    afterAssignLine.loc.getLineNumber(),
                    vi);
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
        TracedValue firstMatchedLine;
        for (int i = 0; i < tracedValues.size(); i++) {
            firstMatchedLine = tracedValues.get(i);
            if (vi.getActualValue().equals(firstMatchedLine.value)) {
                return resultIfNotAssigned(
                        tracedValues.get(i == 0 ? i : i-1).loc.getLineNumber(),
                        vi.getLocateClass(),
                        vi.getVariableName(false, false),
                        firstMatchedLine.createAt,
                        firstMatchedLine.loc.getLineNumber(),
                        vi);
            }
        }

        throw new RuntimeException("There is no value which same to actual.");
    }

    private List<Integer> valueChangedLine(VariableInfo vi){
        //代入行の特定
        //unaryExpr(ex a++)も含める
        CodeElement locateElement = vi.getLocateMethodElement();
        List<Integer> result = new ArrayList<>();
        List<AssignExpr> aes;
        List<UnaryExpr> ues;
        if(vi.isField()) {
            try {
                aes = JavaParserUtil.extractAssignExpr(locateElement);
                CompilationUnit unit = JavaParserUtil.parseClass(locateElement);
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
            BlockStmt bs = JavaParserUtil.extractBodyOfMethod(locateElement);
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
                        result.add(i);
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
                        result.add(i);
                    }
            }
        }
        return result;
    }

    private List<TracedValue> valueChangedToActualLine(List<TracedValue> tracedValues, List<Integer> assignedLine, String actual){
        List<TracedValue> changedToActualLines = new ArrayList<>();
        for(int i = 0; i < tracedValues.size() - 1; i++){
            TracedValue watchingLine = tracedValues.get(i);
            //watchingLineでは代入が行われていない -> 原因行ではない
            if(!assignedLine.contains(watchingLine.loc.getLineNumber())) continue;
            //次の行で値がactualに変わっている -> その行が原因行の候補
            TracedValue afterAssignLine = tracedValues.get(i+1);
            if(afterAssignLine.value.equals(actual)) changedToActualLines.add(watchingLine);
        }
        changedToActualLines.sort(TracedValue::compareTo);
        return changedToActualLines;
    }



    private ProbeResult resultIfAssigned(int probeLine, CodeElement locationClass, LocalDateTime createAt, int watchedAt, VariableInfo vi){
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

        Range probeRange = StaticAnalyzer.getRangeOfStatement(locationClass, probeLine).orElse(null);
        probeLines = probeRange != null ? Pair.of(probeRange.begin.line, probeRange.end.line) : Pair.of(probeLine, probeLine);
        result.setArgument(false);
        result.setLines(probeLines);
        result.setProbeMethod(probeMethod);
        result.setSrc(getProbeStatement(locationClass, probeLines));
        result.setCreateAt(createAt);
        result.setWatchedAt(watchedAt);
        result.setVariableInfo(vi);
        return result;
    }

    private ProbeResult resultIfNotAssigned(int probeLine, String locationClass, String variableName, LocalDateTime createAt, int watchedAt, VariableInfo vi){
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
        result.setVariableInfo(vi);
        return result;
    }

    protected String getProbeStatement(String locationClass, Pair<Integer, Integer> probeLines) {
        return getProbeStatement(new CodeElement(locationClass), probeLines);
    }
    //Statementのソースコードを取得
    protected String getProbeStatement(CodeElement locationClass, Pair<Integer, Integer> probeLines){
        try {
            Optional<Statement> stmt = JavaParserUtil.getStatementByLine(locationClass, probeLines.getLeft());
            if(stmt.isEmpty()) throw new RuntimeException();
            return stmt.get().toString();
        } catch (NoSuchFileException e) {
            return "not found";
            //throw new RuntimeException(e);
        }
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

    protected void printWatchedValues(TracedValueRecord watchedValues, String variableName){
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
                List<TracedValue> pis = jiProcessor.getValuesFromDebugResult(vi, dr);
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
}