package jisd.fl.infra.jdi;

import com.sun.jdi.*;
import com.sun.jdi.Location;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;


public class EnhancedDebugger {
    public VirtualMachine vm;
    public EnhancedDebugger(String main, String options) {
        this.vm = createVM(main, options);
    }
    public EnhancedDebugger(VirtualMachine vm) {
        this.vm = vm;
    }

    public void run() {
        vm.resume();
    }

    public void enableOutput(){
        Process process = vm.process();
        Thread stdoutThread = new Thread(new StreamGobbler(process.getInputStream(), "[stdout]"));
        Thread stderrThread = new Thread(new StreamGobbler(process.getErrorStream(), "[stderr]"));

        stdoutThread.start();
        stderrThread.start();
    }

    protected VirtualMachine createVM(String main, String options) {
        VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
        LaunchingConnector connector = vmm.defaultConnector();
        Map<String, Connector.Argument> cArgs = connector.defaultArguments();
        cArgs.get("options").setValue(options);
        cArgs.get("main").setValue(main);
        //起動後すぐにsuspendされるはず
        try {
            return connector.launch(cArgs);
        } catch (IOException | IllegalConnectorArgumentsException e ) {
            throw new RuntimeException(e);
        }
        catch (VMStartException e){
            return createVM(main, options);
        }
    }

    /**
     * プレークポイントを行で指定し、ヒットした場合handlerで指定された処理を行う
     *
     * @param fqcn    対象の行が属するクラスの完全修飾名
     * @param line    対象の行
     * @param handler プレークポイントがヒットした場合の処理
     */
    public void handleAtBreakPoint(String fqcn, int line, BreakpointHandler handler) {
        handleAtBreakPoint(fqcn, Collections.singletonList(line), handler);
    }

    public void handleAtBreakPoint(String fqcn, List<Integer> lines, BreakpointHandler handler) {
        //ロード済みのクラスに対しbreakPointを設定
        EventRequestManager manager = vm.eventRequestManager();
        //通常は一つのはず
        List<ReferenceType> loaded = vm.classesByName(fqcn);
        for (ReferenceType rt : loaded) {
            for(int line : lines) {
                setBreakpointIfLoaded(rt, manager, line);
            }
        }

        //未ロードのクラスがロードされたタイミングを監視
        //ロードされたらbreakpointをセットする予定
        ClassPrepareRequest cpr = manager.createClassPrepareRequest();
        cpr.addClassFilter(fqcn);
        cpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        cpr.enable();

        //リクエストが立ったらVMをスタート
        run();

        //イベントループ
        EventQueue queue = vm.eventQueue();
        EventSet eventSet = null;
        try {
            while ((eventSet = queue.remove()) != null) {
                for (Event ev : eventSet) {
                    //VMStartEventは無視
                    if (ev instanceof VMStartEvent) {
                        continue;
                    }
                    //ブレークポイントを設置したいクラスがロードされたら対象の行にBPを置く
                    if (ev instanceof ClassPrepareEvent) {
                        ReferenceType ref = ((ClassPrepareEvent) ev).referenceType();
                        if (ref.name().equals(fqcn)) {
                            for(int line : lines) {
                                setBreakpointIfLoaded(ref, manager, line);
                            }
                        }
                        vm.resume();
                        continue;
                    }
                    //ブレークポイントがヒット
                    if (ev instanceof BreakpointEvent) {
                        handler.handle(vm, (BreakpointEvent) ev);
                        vm.resume();
                    }
                }
            }
        } catch (VMDisconnectedException ignored) {
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 指定されたMethodが呼び出された時点でSuspendし、指定された処理を行う
     *
     * @param fqmn    suspend対象の完全修飾メソッド名
     * @param handler プレークポイントがヒットした場合の処理
     */
    public void handleAtMethodEntry(String fqmn, MethodEntryHandler handler) {
        //MethodEntryRequestを有効化
        EventRequestManager manager = vm.eventRequestManager();
        MethodEntryRequest methodEntryRequest = manager.createMethodEntryRequest();
        methodEntryRequest.addClassFilter(fqmn.split("#")[0]);
        methodEntryRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        methodEntryRequest.enable();

        //Requestが経ったらDebuggeeスレッドを再開
        run();

        EventQueue queue = vm.eventQueue();
        while (true) {
            try {
                EventSet eventSet = queue.remove();
                for (Event ev : eventSet) {
                    if (ev instanceof MethodEntryEvent) {
                        MethodEntryEvent mEntry = (MethodEntryEvent) ev;
                        //ターゲットのメソッドでない場合continue
                        String targetFqmn = getFqmn(mEntry.method());
                        if (!targetFqmn.equals(fqmn)) continue;

                        //ハンドラを実行
                        handler.handle(vm, mEntry);
                    }
                }
                eventSet.resume();
            } catch (VMDisconnectedException | InterruptedException e) {
                break;
            }
        }
    }

    private void setBreakpointIfLoaded(ReferenceType rt, EventRequestManager manager, int line) {
        try {
            List<com.sun.jdi.Location> bpLocs = rt.locationsOfLine(line);
            if (bpLocs.isEmpty()) {
                //System.err.println("Cannot set BreakPoint at line " + line + " at " + rt.name() + ".");
                return;
            } else {
                //bpLocsには指定した行に属する要素が複数含まれ、
                //先頭の要素が行内で一番初めに実行されるものとは限らない
                //そのためcodeIndexが最小のものを選び、それに対してbreakPointをおかなければならない
                com.sun.jdi.Location earliestLocation = bpLocs.stream()
                        .min(Comparator.comparingLong(Location::codeIndex))
                        .get();
                BreakpointRequest bpReq = manager.createBreakpointRequest(earliestLocation);
                bpReq.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                bpReq.enable();
            }
        } catch (AbsentInformationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Breakpointにヒットした際に行う処理を記述
     * 内部でvmをresume()して返してはいけない
     * 必ずvmがsuspendされた状態で返すこと
     */
    public interface BreakpointHandler {
        void handle(VirtualMachine vm, BreakpointEvent event) throws InterruptedException;
    }

    /**
     * 目的のメソッドが呼ばれた際に実行するハンドラを記述
     * 内部でvmをresume()して返してはいけない
     * 必ずvmがsuspendされた状態で返すこと
     */
    public interface MethodEntryHandler {
        void handle(VirtualMachine vm, MethodEntryEvent event) throws InterruptedException;
    }

    static public MethodExitRequest createMethodExitRequest(EventRequestManager manager, ThreadReference thread) {
        MethodExitRequest meReq = manager.createMethodExitRequest();
        meReq.addThreadFilter(thread);
        meReq.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        meReq.enable();
        return meReq;
    }

    static public MethodEntryRequest createMethodEntryRequest(EventRequestManager manager, ThreadReference thread) {
        MethodEntryRequest meReq = manager.createMethodEntryRequest();
        meReq.addThreadFilter(thread);
        meReq.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        meReq.enable();
        return meReq;
    }

    static public StepRequest createStepOverRequest(EventRequestManager manager, ThreadReference thread){
        StepRequest stepReq = manager.createStepRequest(
                thread,
                StepRequest.STEP_LINE,
                StepRequest.STEP_OVER
        );
        stepReq.addCountFilter(1);  // 次の１ステップで止まる
        stepReq.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        stepReq.enable();
        return stepReq;
    }

    static public StepRequest createStepOutRequest(EventRequestManager manager, ThreadReference thread){
        StepRequest stepReq = manager.createStepRequest(
                thread,
                StepRequest.STEP_LINE,
                StepRequest.STEP_OUT
        );
        stepReq.addCountFilter(1);  // 次の１ステップで止まる
        stepReq.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        stepReq.enable();
        return stepReq;
    }

    static public StepRequest createStepInRequest(EventRequestManager manager, ThreadReference thread){
        StepRequest stepReq = manager.createStepRequest(
                thread,
                StepRequest.STEP_LINE,
                StepRequest.STEP_INTO
        );
        stepReq.addCountFilter(1);  // 次の１ステップで止まる
        stepReq.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        stepReq.enable();
        return stepReq;
    }

    static public String getFqmn(Method m) {
        StringBuilder n = new StringBuilder(m.toString());
        n.setCharAt(m.toString().split("\\(")[0].lastIndexOf("."), '#');
        if((n.toString().contains("<clinit>"))){
            return n.substring(0, n.indexOf("#"));
        }
        if (n.toString().contains("<init>")) {
            String constructorName = n.substring(n.toString().split("\\(")[0].lastIndexOf(".") + 1, n.indexOf("#"));
            n.replace(n.indexOf("#") + 1, n.indexOf("("), constructorName);
        }

        return n.toString().split("\\(")[0] + normalizeMethodSignature("(" + n.toString().split("\\(")[1]);
    }

    public static String normalizeMethodSignature(String methodSignature){
        String shortMethodName = methodSignature.split("\\(")[0];
        String[] args = methodSignature.substring(methodSignature.indexOf("(") + 1, methodSignature.indexOf(")")).split(",");
        if(args.length == 1 && args[0].isEmpty()) return methodSignature;
        StringBuilder sb = new StringBuilder();

        sb.append(shortMethodName);
        sb.append("(");
        for(String arg : args){
            arg = arg.trim();
            if(!arg.contains(".")) {
                sb.append(arg);
            }
            else {
                sb.append(arg.substring(arg.lastIndexOf(".") + 1));
            }
            sb.append(", ");
        }

        sb.delete(sb.length() -2, sb.length());
        sb.append(")");
        return sb.toString();
    }


    /**
     * サブプロセスの標準（エラー）出力を
     * 別スレッドで逐次読み込み、任意の処理を行うためのヘルパークラス
     */
    private static class StreamGobbler implements Runnable {
        private final InputStream is;
        private final String prefix;

        /**
         * @param is     読み込む InputStream（プロセスの stdout か stderr）
         * @param prefix ログの先頭に付けるプレフィックス（例: "[stdout]" や "[stderr]"）
         */
        public StreamGobbler(InputStream is, String prefix) {
            this.is = is;
            this.prefix = prefix;
        }

        @Override
        public void run() {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = br.readLine()) != null) {
                    // 必要に応じてログ出力や別の処理に渡す
                    System.out.println(prefix + " " + line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}