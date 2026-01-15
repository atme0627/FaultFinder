package jisd.fl.infra.jdi;

import com.sun.jdi.*;
import jisd.debug.EnhancedDebugger;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.core.entity.TracedValue;
import jisd.fl.infra.javaparser.JavaParserTraceTargetLineFinder;
import jisd.fl.infra.javaparser.TmpJavaParserUtils;
import jisd.fl.util.TestUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 指定されたsuspiciousVariableに基づき、各行で怪しい変数が取る値を観測、記録する。
 */
public class TargetVariableTracer {
    private SuspiciousVariable target;

    public TargetVariableTracer(SuspiciousVariable target) {
        this.target = target;
    }

    public List<TracedValue> traceValuesOfTarget() {
        //targetVariableのVariableDeclaratorを特定
        MethodElementName targetMethod = target.getLocateMethodElement();
        List<Integer> result1 = TmpJavaParserUtils.findLocalVariableDeclarationLine(targetMethod, target.getSimpleVariableName());
        List<Integer> vdLines = result1;
        List<Integer> canSetLines = JavaParserTraceTargetLineFinder.traceTargetLineNumbers(target);

        //Debugger生成
        String main = TestUtil.getJVMMain(target.getFailedTest());
        String options = TestUtil.getJVMOption();
        EnhancedDebugger eDbg = new EnhancedDebugger(main, options);
        List<TracedValue> result = new ArrayList<>();

        EnhancedDebugger.BreakpointHandler handler = (vm, event) -> {
            LocalDateTime watchTime = LocalDateTime.now();
            try {
                StackFrame frame = event.thread().frame(0);
                Optional<TracedValue> v = watchVariableInLine(frame, target, watchTime);
                if (v.isPresent()) {
                    result.add(v.get());
                    return;
                }

                //実行している行が宣言行の場合、そこを実行したことを示すためnullを追加する
                if (vdLines.contains(frame.location().lineNumber())) {
                    result.add(new TracedValue(
                            watchTime,
                            target.getVariableName(true, true),
                            "null",
                            frame.location().lineNumber()
                    ));
                }
            } catch (IncompatibleThreadStateException e) {
                throw new RuntimeException(e);
            }
        };

        eDbg.handleAtBreakPoint(target.getLocateClass(), canSetLines, handler);
        return result;
    }

    private Optional<TracedValue> watchVariableInLine(StackFrame frame, SuspiciousVariable sv, LocalDateTime watchedAt) {
        int locateLine = frame.location().lineNumber();
        // （1）ローカル変数
        if (!sv.isField()) {
            LocalVariable local;
            try {
                local = frame.visibleVariableByName(sv.getSimpleVariableName());
                if(local == null) return Optional.empty();
            } catch (AbsentInformationException e) {
                throw new RuntimeException(e);
            }
            Value v = frame.getValue(local);
            if (v == null) return Optional.empty();

            //配列の場合[0]のみ観測
            if (local instanceof ArrayReference ar) {
                if (!sv.isArray()) {
                    System.err.println("Something is wrong. [ARRAY NAME] " + sv.getSimpleVariableName());
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
                Field f = rt.fieldByName(sv.getSimpleVariableName());
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
            Field f = rt.fieldByName(sv.getSimpleVariableName());
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
