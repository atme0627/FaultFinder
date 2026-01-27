package jisd.fl.infra.jdi;

import com.sun.jdi.*;
import com.sun.jdi.Location;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.infra.jvm.JVMProcess;

import java.io.*;
import java.util.*;
import java.util.function.Supplier;


public abstract class EnhancedDebugger implements Closeable {
    public VirtualMachine vm;
    public JVMProcess p;
    private volatile boolean closed = false;

    private final Map<Class<? extends Event>, List<JDIEventHandler<? extends Event>>> handlers = new HashMap<>();
    private final Set<Integer> breakpointLines = new HashSet<>();
    //breakpointは単一のクラスにのみ置く想定
    private String targetClass;

    public EnhancedDebugger(JVMProcess p, String hostName, String port) {
        this.vm = createVM(hostName, port);
        this.p = p;
    }

    public void registerEventHandler(Class<? extends Event> eventType, JDIEventHandler<? extends Event> handler) {
        handlers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handler);
    }

    public void setBreakpoints(String fqcn, List<Integer> lines) {
        this.targetClass = fqcn;
        this.breakpointLines.addAll(lines);
    }

    public void executeTMP() {
        vm.resume();
    }

    public void execute() {
        execute(null);
    }

    public void execute(Supplier<Boolean> shouldStop) {
        //ロード済みのクラスに対しbreakPointを設定
        EventRequestManager manager = vm.eventRequestManager();
        //通常は一つのはず
        List<ReferenceType> loaded = vm.classesByName(targetClass);
        for (ReferenceType rt : loaded) {
            for(int line : breakpointLines) {
                setBreakpointIfLoaded(rt, manager, line);
            }
        }

        //未ロードのクラスがロードされたタイミングを監視
        //ロードされたらbreakpointをセットする予定
        ClassPrepareRequest cpr = manager.createClassPrepareRequest();
        cpr.addClassFilter(targetClass);
        cpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        cpr.enable();

        // 統合イベントループ
        EventQueue queue = vm.eventQueue();
        EventSet eventSet = null;
        try {
            while((eventSet = queue.remove()) != null) {
                // 終了条件チェック
                if (shouldStop != null && shouldStop.get()) {
                    break;
                }

                for(Event ev : eventSet) {
                    //VMStartEventは無視
                    if (ev instanceof VMStartEvent) {
                        continue;
                    }
                    //ブレークポイントを設置したいクラスがロードされたら対象の行にBPを置く
                    if (ev instanceof ClassPrepareEvent cpe) {
                        ReferenceType ref = cpe.referenceType();
                        if (ref.name().equals(targetClass)) {
                            for(int line : breakpointLines) {
                                setBreakpointIfLoaded(ref, manager, line);
                            }
                        }
                        continue;
                    }

                    // 登録されたハンドラーを実行
                    for (Map.Entry<Class<? extends Event>, List<JDIEventHandler<?>>> entry : handlers.entrySet()) {
                        Class<? extends Event> eventType = entry.getKey();
                        if (eventType.isInstance(ev)) {
                            List<JDIEventHandler<?>> eventHandlers = entry.getValue();
                            for (JDIEventHandler handler : eventHandlers) {
                                //noinspection unchecked
                                handler.handle(vm, ev);
                            }
                        }
                    }

                }
                vm.resume();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (VMDisconnectedException ignored) {
        } finally {
            close();
        }
    }

    protected VirtualMachine createVM(String hostName, String port) {
            VirtualMachineManager vmManager = Bootstrap.virtualMachineManager();
            AttachingConnector socket = vmManager.attachingConnectors().stream()
                    .filter(c -> c.name().equals("com.sun.jdi.SocketAttach"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("SocketAttach connector not found"));
            Map<String, Connector.Argument> args = socket.defaultArguments();
            args.get("hostname").setValue(hostName);
            args.get("port").setValue(port);

            // debuggee が listen を開始する前に attach すると Connection refused になるのでリトライする
            int maxTry = 50;          // 50 * 50ms = 2.5s
            long sleepMs = 50;

            for (int i = 0; i < maxTry; i++) {
                try {
                    return socket.attach(args);
                } catch (IOException e) {
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ie);
                    }
                } catch (IllegalConnectorArgumentsException e) {
                    throw new RuntimeException(e);
                }
            }
            throw new RuntimeException("Failed to attach debugger (Connection refused). host=" + hostName + " port=" + port);
        }

    /**
     * プレークポイントを行で指定し、ヒットした場合handlerで指定された処理を行う
     *
     * @param fqcn    対象の行が属するクラスの完全修飾名
     * @param line    対象の行
     * @param handler プレークポイントがヒットした場合の処理
     */
    @Deprecated
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
        executeTMP();

        //イベントループ
        EventQueue queue = vm.eventQueue();
        EventSet eventSet = null;
        try {
            while ((eventSet = queue.remove()) != null) {
                for (Event ev : eventSet) {
                    //VMStartEventは無視
                    if (ev instanceof VMStartEvent) {
                        vm.resume();
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
        } finally {
            close();
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
        executeTMP();

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
                close();
                break;
            }
        }
    }

    @Override
    public void close() {
        if(closed) return;
        closed = true;

        //VMを止める
        try {
            if (vm != null){
                try {
                    vm.dispose();
                } catch (VMDisconnectedException ignored) {
                }
            }
        } finally {
            //プロセスを止める
            if (p != null && p.process != null && p.process.isAlive()) {
                p.process.destroy();
                try {
                    p.process.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    if (p.process.isAlive()) p.process.destroyForcibly();
                }
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
        return new MethodElementName(n.toString()).fullyQualifiedName() ;
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