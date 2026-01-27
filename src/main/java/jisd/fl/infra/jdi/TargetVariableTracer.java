package jisd.fl.infra.jdi;

import com.sun.jdi.*;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.StepEvent;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.core.entity.TracedValue;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.infra.javaparser.JavaParserTraceTargetLineFinder;
import jisd.fl.infra.javaparser.JavaParserUtils;
import jisd.fl.infra.junit.JUnitDebugger;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 指定されたsuspiciousVariableに基づき、各行で怪しい変数が取る値を観測、記録する。
 */
public class TargetVariableTracer {

    public TargetVariableTracer() {
    }

    //TODO: field未対応
    public List<TracedValue> traceValuesOfTarget(SuspiciousVariable target) {
        if(!(target instanceof SuspiciousLocalVariable localVariable)) throw new RuntimeException("Field variable has not been supported.");
        //targetVariableのVariableDeclaratorを特定
        MethodElementName targetMethod = localVariable.locateMethod();
        List<Integer> vdLines = JavaParserUtils.findLocalVariableDeclarationLine(targetMethod, localVariable.variableName());
        List<Integer> canSetLines = JavaParserTraceTargetLineFinder.traceTargetLineNumbers(localVariable);

        //step後に観測した値が、どの行の実行によるものだったのかを記録する。
        // マルチスレッドに備えて、Thread -> line のmapで管理
        final Map<ThreadReference, Integer> stepSourceLine = new HashMap<>();



        //Debugger生成
        JUnitDebugger debugger = new JUnitDebugger(localVariable.failedTest());
        List<TracedValue> result = new ArrayList<>();

        //ブレークポイント設定
        debugger.setBreakpoints(localVariable.locateClass().fullyQualifiedClassName(), canSetLines);

        //BreakPointEvent handler を登録
        debugger.registerEventHandler(BreakpointEvent.class, (vm, event) -> {
            BreakpointEvent bpEvent = (BreakpointEvent) event;
            try {
                StackFrame frame = bpEvent.thread().frame(0);
                int currentLine = frame.location().lineNumber();
                stepSourceLine.put(bpEvent.thread(), currentLine);
                // StepRequest を作成
                EnhancedDebugger.createStepOverRequest(vm.eventRequestManager(), bpEvent.thread());
            }catch (IncompatibleThreadStateException e){
                throw new RuntimeException(e);
            }
        });

        //stepEvent handler を登録
        debugger.registerEventHandler(StepEvent.class, (vm, event) -> {
            StepEvent stepEvent = (StepEvent) event;
            try {
                // post-state を観測
                StackFrame frame = stepEvent.thread().frame(0);
                Optional<TracedValue> postState = watchVariableInLine(
                        frame, localVariable, stepSourceLine.get(stepEvent.thread()), LocalDateTime.now());
                if (postState.isPresent()) {
                    result.add(postState.get());
                }

                // StepRequest を削除
                // （どの StepRequest を削除するか特定が必要）
                vm.eventRequestManager().stepRequests().forEach(req -> {
                    req.disable();
                    vm.eventRequestManager().deleteEventRequest(req);
                });

            } catch (IncompatibleThreadStateException e) {
                throw new RuntimeException(e);
            }
        });

        debugger.execute();
        return result;
    }

    private Optional<TracedValue> watchVariableInLine(StackFrame frame, SuspiciousLocalVariable sv, int locateLine, LocalDateTime watchedAt) {
        // （1）ローカル変数
        if (!sv.isField()) {
            LocalVariable local;
            try {
                local = frame.visibleVariableByName(sv.variableName());
                if(local == null) return Optional.empty();
            } catch (AbsentInformationException e) {
                throw new RuntimeException(e);
            }
            Value v = frame.getValue(local);
            if (v == null) return Optional.empty();

            //配列の場合[0]のみ観測
            if (local instanceof ArrayReference ar) {
                if (!sv.isArray()) {
                    System.err.println("Something is wrong. [ARRAY NAME] " + sv.variableName());
                    if (ar.length() == 0) {
                        return Optional.of(
                                new TracedValue(
                                        watchedAt,
                                        local.name() + "[0]",
                                        "null",
                                        locateLine
                                ));
                    }
                    return Optional.of(new TracedValue(
                            watchedAt,
                            local.name() + "[0]",
                            JDIUtils.getValueString(ar.getValue(0)),
                            locateLine
                    ));
                }

            }

            return Optional.of(new TracedValue(
                    watchedAt,
                    local.name(),
                    JDIUtils.getValueString(v),
                    locateLine
            ));
        } else {
            // (2) インスタンスフィールド
            ObjectReference thisObj = frame.thisObject();
            if (thisObj != null) {
                ReferenceType rt = thisObj.referenceType();
                Field f = rt.fieldByName(sv.variableName());
                if (f != null) {
                    return Optional.of(new TracedValue(
                            watchedAt,
                            "this." + f.name(),
                            JDIUtils.getValueString(thisObj.getValue(f)),
                            locateLine
                    ));
                }
            }
            // (3) static フィールド
            ReferenceType rt = frame.location().declaringType();
            Field f = rt.fieldByName(sv.variableName());
            if (f != null) {
                return Optional.of(new TracedValue(
                        watchedAt,
                        "this." + f.name(),
                        JDIUtils.getValueString(rt.getValue(f)),
                        locateLine
                ));
            }
        }
        throw new RuntimeException("Cannot find variable in line. [VARIABLE] " + sv);
    }
}
