package jisd.debug;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import jisd.fl.util.analyze.CodeElementName;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EnhancedDebugger {
    public VirtualMachine vm;
    public EnhancedDebugger(String main, String options) {
        init(main, options);
    }

    void init(String main, String options) {
        this.vm = createVM(main, options);
    }

    VirtualMachine createVM(String main, String options){
        VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
        LaunchingConnector connector = vmm.defaultConnector();
        Map<String, Connector.Argument> cArgs = connector.defaultArguments();
        cArgs.get("options").setValue(options);
        cArgs.get("main").setValue(main);
        //起動後すぐにsuspendされるはず
        try {
            return connector.launch(cArgs);
        } catch (IOException | IllegalConnectorArgumentsException | VMStartException e) {
            throw new RuntimeException(e);
        }
    }

    //fqcn -> FullyQualifiedClassName
     public Set<String> getCallerMethod(String fqcn, int line){
         //リクエストを先に立てる
         //ロード済みのクラスに対しbreakPointを設定
         EventRequestManager manager = vm.eventRequestManager();
         List<ReferenceType> loaded = vm.classesByName(fqcn);
         for(ReferenceType rt : loaded) {
             setBreakpoint(rt, manager, line);
         }

         //未ロードのクラスがロードされたタイミングを監視
         ClassPrepareRequest cpr = manager.createClassPrepareRequest();
         cpr.addClassFilter(fqcn);
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
                         if(ref.name().equals(fqcn)){
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
}