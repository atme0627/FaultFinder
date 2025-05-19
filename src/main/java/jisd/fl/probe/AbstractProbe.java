package jisd.fl.probe;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import jisd.debug.*;
import jisd.debug.Location;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.probe.record.TracedValue;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.probe.record.TracedValuesAtLine;
import jisd.fl.probe.record.TracedValuesOfTarget;
import jisd.fl.util.analyze.*;
import jisd.fl.util.TestUtil;
import org.apache.commons.lang3.tuple.Pair;

import java.io.*;
import java.nio.file.NoSuchFileException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public abstract class AbstractProbe {

    FailedAssertInfo assertInfo;
    Debugger dbg;
    JisdInfoProcessor jiProcessor;
    static PrintStream stdOut = System.out;

    public AbstractProbe(FailedAssertInfo assertInfo) {
        this.assertInfo = assertInfo;
        this.dbg = createDebugger();
        this.jiProcessor = new JisdInfoProcessor();
    }

    //一回のprobeを行う
    //条件を満たす行の情報を返す
    protected ProbeResult probing(int sleepTime, VariableInfo variableInfo){
        //ターゲット変数が変更されうる行を観測し、全変数の情報を取得
        disableStdOut("    >> Probe Info: Running debugger and extract watched info.");
        TracedValueCollection tracedValues = traceValuesOfTarget(variableInfo, sleepTime);
        tracedValues.printAll();
        //対象の変数に変更が起き、actualを取るようになった行（原因行）を探索
        List<TracedValue> watchedValues = tracedValues.filterByVariableName(variableInfo.getVariableName(true, true));
        System.out.println("    >> Probe Info: Searching probe line.");
        ProbeResult result = searchProbeLine(watchedValues, variableInfo);

        //probe lineが特定できなかった場合nullを返す
        if(result.isNotFound()) return null;
        return result;
    }

    //variableInfoに指定された変数のみを観測し、各行で取っている値を記録する
    protected TracedValueCollection traceValuesOfTarget(VariableInfo target, int sleepTime){
        List<Integer> canSetLines = StaticAnalyzer.getCanSetLine(target);
        String dbgMain = target.getLocateClass();
        dbg = createDebugger();
        String[] targetValueName = new String[]{target.getVariableName()};
        //set watchPoint
        dbg.setMain(dbgMain);
        List<Optional<Point>> watchPoints =
                canSetLines.stream()
                        .map(l -> dbg.watch(l, targetValueName))
                        .collect(Collectors.toList());

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
        //各行でのデバッグ情報
        List<DebugResult> drs = watchPoints.stream()
                //WatchPointからDebugResultを得る
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(wp -> wp.getResults(targetValueName[0]))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        //各行での値の情報 (Location情報なし)
        List<ValueInfo> valuesOfTarget = drs.stream()
                .map(DebugResult::getValues)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        //LocalDateTime --> Locationのマップ
        Map<LocalDateTime, Location> locationAtTime = new HashMap<>();
        for(DebugResult dr : drs){
            Location loc = dr.getLocation();
            for(ValueInfo vi : dr.getValues()){
                locationAtTime.put(vi.getCreatedAt(), loc);
            }
        }

        TracedValueCollection watchedValues = new TracedValuesOfTarget(target, valuesOfTarget, locationAtTime);
        dbg.exit();
        dbg.clearResults();
        return watchedValues;
    }

    //viの原因行で、全ての変数が取っている値を記録する
    //何回目のループで観測された値かを入力する
    protected TracedValueCollection traceAllValuesAtLine(CodeElementName targetClassName, int line, int nthLoop, int sleepTime){
        disableStdOut("");
        dbg = createDebugger();
        dbg.setMain(targetClassName.getFullyQualifiedClassName());
        Optional<Point> watchPointAtLine = dbg.watch(line);

        //run Test debugger
        try {
            dbg.run(sleepTime);
        } catch (VMDisconnectedException | InvalidStackFrameException e) {
            System.err.println(e);
        }
        enableStdOut();

        //この行で値が観測されることが保証されている
        List<DebugResult> drs = new ArrayList<>(watchPointAtLine.get().getResults().values());
        Location loc = drs.get(0).getLocation();
        //行のnthLoop番目のvalueInfoを取得
        List<ValueInfo> valuesAtLine = drs.stream()
                .map(DebugResult::getValues)
                .map(vis -> vis.get(nthLoop))
                .collect(Collectors.toList());

        TracedValueCollection watchedValues = new TracedValuesAtLine(valuesAtLine, loc);
        dbg.exit();
        dbg.clearResults();
        return watchedValues;
    }


    //TODO: 原因行が何回目のループのものかを取得し、probeResultに与える
    private ProbeResult searchProbeLine(List<TracedValue> tracedValues, VariableInfo vi){
        //対象の変数に値の変化が起きている行の特定
        List<Integer> valueChangingLines = valueChangingLine(vi);

        //代入の実行後にactualの値に変化している行の特定(ない場合あり)
        List<TracedValue> changeToActualLines = valueChangedToActualLine(tracedValues, valueChangingLines, vi.getActualValue());

        //代入の実行後にactualの値に変化している行あり -> その中で最後に実行された行がprobe line
        if(!changeToActualLines.isEmpty()) {
            //原因行
            TracedValue causeLine = changeToActualLines.get(changeToActualLines.size() - 1);
            //原因行の次に実行された行
            TracedValue afterAssignedLine = tracedValues.get(tracedValues.indexOf(causeLine));

            return resultIfAssigned(causeLine, vi);
        }

        //fieldは代入以外での値の変更を特定できない
        if(vi.isField()){
            System.err.println("Cannot find probe line of field. [FIELD NAME] " + vi.getVariableName());
            ProbeResult result = new ProbeResult(vi, null);
            result.setNotFound(true);
            return result;
        }

        //実行された代入行がないパターン
        //初めて値がactualと一致した行の前に実行された行を暫定的にprobe lineとする。
        TracedValue firstMatchedLine;
        for (TracedValue tracedValue : tracedValues) {
            firstMatchedLine = tracedValue;
            if (vi.getActualValue().equals(firstMatchedLine.value)) {
                return resultIfNotAssigned(
                        vi.getVariableName(false, false),
                        firstMatchedLine.loc.getLineNumber(),
                        vi);
            }
        }

        throw new RuntimeException("There is no value which same to actual.");
    }

    private List<Integer> valueChangingLine(VariableInfo vi){
        //代入行の特定
        //unaryExpr(ex a++)も含める
        CodeElementName locateElement = vi.getLocateMethodElement();
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
            BlockStmt bs = null;
            try {
                bs = JavaParserUtil.extractBodyOfMethod(locateElement);
            } catch (NoSuchFileException e) {
                throw new RuntimeException(e);
            }
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



    private ProbeResult resultIfAssigned(TracedValue causeLineData, VariableInfo vi){
        //代入によって変数がactualの値を取るようになったパターン
        //値がactualになった行の前に観測した行が、実際に値を変更した行(probe line)
        int causeLineNumber = causeLineData.loc.getLineNumber();
        LocalDateTime createAt = causeLineData.createAt;

        //実行しているメソッドを取得
        CodeElementName locateMethodElementName = vi.getLocateMethodElement();
        MethodElement locateMethodElement;
        try {
            locateMethodElement = MethodElement.getMethodElementByName(locateMethodElementName);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
        StatementElement probeStmt = locateMethodElement.FindStatementByLine(causeLineNumber).get();

        ProbeResult result = new ProbeResult(vi, probeStmt);
        result.setProbeMethodName(locateMethodElementName.getFullyQualifiedMethodName());
        return result;
    }

    private ProbeResult resultIfNotAssigned(String variableName, int watchedAt, VariableInfo vi){
        //代入以外の要因で変数がactualの値をとるようになったパターン
        //1. 初期化の時点でその値が代入されている。
        //2. その変数が引数由来で、かつメソッド内で上書きされていない。
        //3. throw内などブレークポイントが置けない行で、代入が行われている。 --> 未想定

        //実行しているメソッドを取得
        CodeElementName locateMethodElementName = vi.getLocateMethodElement();
        MethodElement locateMethodElement;
        try {
            locateMethodElement = MethodElement.getMethodElementByName(locateMethodElementName);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        //1. 初期化の時点でその値が代入されている。
        //変数が存在し、宣言と同時に初期化がされている時点で、これを満たすことにする
        Optional<VariableDeclarator> ovd = locateMethodElement.findLocalVarDeclaration(variableName);
        boolean isThereVariableDeclaration = ovd.isPresent() && ovd.get().getInitializer().isPresent();
        //この場合、probeLineは必ずmethod内にいる。
        if (isThereVariableDeclaration) {
            int varDeclarationLine = ovd.get().getBegin().get().line;
            StatementElement probeStmt = locateMethodElement.FindStatementByLine(varDeclarationLine).get();
            ProbeResult result = new ProbeResult(vi, probeStmt);
            result.setProbeMethodName(locateMethodElementName.getFullyQualifiedMethodName());
            return result;
        }

        //2. その変数が引数由来で、かつメソッド内で上書きされていない
        //暫定的にprobeLinesを設定
        ProbeResult result = new ProbeResult(vi);
        result.setProbeMethodName(locateMethodElementName.getFullyQualifiedMethodName());
        result.setWatchedAt(watchedAt);
        return result;
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

    protected void printWatchedValues(TracedValueCollection watchedValues, String variableName){
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
        if(result.isCausedByArgument()) {
            System.out.println("    >> Variable defect is derived from caller method. ");
        }
        System.out.println("    >> " + result.getSrc());
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


    public Set<String> getCalleeMethods(CodeElementName targetClass, int line){
        //vm生成
        String main = TestUtil.getJVMMain(new CodeElementName(assertInfo.getTestMethodName()));
        String options = TestUtil.getJVMOption();
        VirtualMachine vm;

        try {
            VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
            LaunchingConnector connector = vmm.defaultConnector();
            Map<String, Connector.Argument> cArgs = connector.defaultArguments();
            cArgs.get("options").setValue(options);
            cArgs.get("main").setValue(main);
            //起動後すぐにsuspendされるはず
            vm = connector.launch(cArgs);
        }
        catch (IllegalConnectorArgumentsException | VMStartException | IOException e) {
            throw new RuntimeException(e);
        }


        //リクエストを先に立てる
        //ロード済みのクラスに対しbreakPointを設定
        EventRequestManager manager = vm.eventRequestManager();
        List<ReferenceType> loaded = vm.classesByName(targetClass.getFullyQualifiedClassName());
        for(ReferenceType rt : loaded) {
            setBreakpoint(rt, manager, line);
        }

        //未ロードのクラスがロードされたタイミングを監視
        ClassPrepareRequest cpr = manager.createClassPrepareRequest();
        cpr.addClassFilter(targetClass.getFullyQualifiedClassName());
        cpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        cpr.enable();

        //リクエストが立ったらVMを再開
        vm.resume();

        Set<String> result = new HashSet<>();
        EventQueue queue = vm.eventQueue();
        EventSet eventSet = null;
        try {
            while ((eventSet = queue.remove()) != null) {
                for (Event ev : eventSet) {
                    if (ev instanceof VMStartEvent) {
                        continue;
                    }
                    if (ev instanceof ClassPrepareEvent) {
                        //System.out.println("ClassPrepareEvent");
                        ReferenceType ref = ((ClassPrepareEvent) ev).referenceType();
                        // クラスロード直後にブレークポイントを設定
                        if(ref.name().equals(targetClass.getFullyQualifiedClassName())) {
                            setBreakpoint(ref, manager, line);
                        }
                        continue;
                    }
                    if (ev instanceof BreakpointEvent) {
                        //System.out.println("BreakPointEvent");
                        BreakpointEvent be = (BreakpointEvent) ev;
                        ThreadReference thread = be.thread();
                        //このスレッドでの MethodEntry を記録するリクエストを作成
                        MethodEntryRequest meReq = manager.createMethodEntryRequest();
                        meReq.addThreadFilter(thread);
                        meReq.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                        meReq.enable();

                        //この行の実行が終わったことを検知するステップリクエスト
                        StepRequest stepReq = manager.createStepRequest(
                                thread,
                                StepRequest.STEP_LINE,
                                StepRequest.STEP_OVER
                        );
                        stepReq.addCountFilter(1);  // 次の１ステップで止まる
                        stepReq.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                        stepReq.enable();

                        //一旦 resume して、内部ループで MethodEntry／Step を待つ
                        vm.resume();
                        boolean done = false;
                        while (!done) {
                            EventSet es2 = vm.eventQueue().remove();
                            for (Event ev2 : es2) {
                                if (ev2 instanceof MethodEntryEvent) {
                                    MethodEntryEvent mee = (MethodEntryEvent) ev2;
                                    StackFrame sf = mee.thread().frame(1);
                                    //指定した行で直接呼ばれたメソッドのみ対象
                                    if (mee.thread().equals(thread) && sf.location().method().equals(be.location().method())) {
                                        result.add(mee.method().toString());
                                    }

                                }
                                else if (ev2 instanceof StepEvent) {
                                    done = true;
                                }
                                ev2.request().virtualMachine().resume();
                            }
                        }

                        //動的に作ったリクエストは不要になったら無効化しておく
                        meReq.disable();
                        stepReq.disable();
                    }
                }

                vm.resume();
            }
        }
        catch (VMDisconnectedException ignored) {}
        catch (InterruptedException | IncompatibleThreadStateException e) {
            throw new RuntimeException(e);
        }
        //package.class.method() --> package.class#method()
        result = result.stream()
                .map(StringBuilder::new)
                .map(n -> {
                    n.setCharAt(n.lastIndexOf("."), '#');
                    if(n.toString().contains("<init>")){
                        String constructorName = n.substring(n.lastIndexOf("."), n.indexOf("#"));
                        n.replace(n.indexOf("#"), n.indexOf("("), constructorName);
                    }
                    return n.toString();
                })
                .collect(Collectors.toSet());

        return result;
    }

    private void setBreakpoint(ReferenceType rt, EventRequestManager manager, int line){
        try {
            List<com.sun.jdi.Location> bpLocs = rt.locationsOfLine(line);
            if(bpLocs.isEmpty()) {
                System.err.println("line " + line + " at " + rt.name() + " is not found.");
            }
            else {
                BreakpointRequest bpReq = manager.createBreakpointRequest(bpLocs.get(0));
                bpReq.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                bpReq.enable();
            }
        } catch (AbsentInformationException e) {
            throw new RuntimeException(e);
        }

    }

    protected Pair<Integer, String> getCallerMethod(CodeElementName targetMethod) {
        Pair<Integer, String> result = null;
        dbg = createDebugger();
        JDIManager jdi = (JDIManager) dbg.getVmManager();
        VirtualMachine vm = jdi.getJDI().vm();

        EventRequestManager manager = vm.eventRequestManager();
        MethodEntryRequest methodEntryRequest = manager.createMethodEntryRequest();
        methodEntryRequest.addClassFilter(targetMethod.getFullyQualifiedClassName());
        methodEntryRequest.enable();
        Thread testExec = new Thread(() -> {
            dbg.run(2000);
        });

        testExec.start();
        EventQueue queue = vm.eventQueue();
        while (true) {
            try {
                EventSet eventSet = queue.remove();
                for (Event ev : eventSet) {
                    if (ev instanceof MethodEntryEvent) {
                        MethodEntryEvent mEntry = (MethodEntryEvent) ev;
                        ThreadReference thread = mEntry.thread();
                        List<StackFrame> frames = thread.frames();
                        com.sun.jdi.Location callerLoc = frames.get(1).location();
                        Method callerMethod = callerLoc.method();
                        result = Pair.of(callerLoc.lineNumber(), callerMethod.declaringType().name() + "#" + callerMethod.name());
                    }
                }
                eventSet.resume();
            }
            catch (VMDisconnectedException | InterruptedException e){
                break;
            } catch (IncompatibleThreadStateException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
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

}