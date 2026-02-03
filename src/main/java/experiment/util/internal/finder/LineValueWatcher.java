package experiment.util.internal.finder;

import com.sun.jdi.*;
import jisd.fl.core.entity.susp.SuspiciousFieldVariable;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.infra.jdi.EnhancedDebugger;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.infra.junit.JUnitDebugger;
import jisd.fl.core.entity.element.MethodElementName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LineValueWatcher {
    private final MethodElementName targetTestCaseName;
    public LineValueWatcher(MethodElementName targetTestCaseName) {
        this.targetTestCaseName = targetTestCaseName;
    }
    public List<SuspiciousVariable> watchAllValuesInAssertLine(int failureLine, MethodElementName locateMethod){
        final List<SuspiciousVariable> result = new ArrayList<>();
        //Debugger生成
        JUnitDebugger debugger = new JUnitDebugger(this.targetTestCaseName);

        //失敗行にブレークポイントを置きsuspend
        EnhancedDebugger.BreakpointHandler handler = (vm, bpe) -> {
            try {
                ThreadReference thread = bpe.thread();
                //その行が実際には複数回実行される可能性がある。
                //その際には失敗する時の情報（つまり、一番最後に実行された時の情報）を取得する。
                //そのために、まずresultを初期化することで暫定の最終実行時の情報がresultに保持される。
                result.clear();

                result.addAll(watchAllVariablesInLine(thread.frame(0), locateMethod));
            } catch (IncompatibleThreadStateException e) {
                throw new RuntimeException(e);
            }
        };

        //VMを実行し情報を収集
        debugger.handleAtBreakPoint(locateMethod.fullyQualifiedClassName(), failureLine, handler);
        return result;
    }


    private List<SuspiciousVariable> watchAllVariablesInLine(StackFrame frame, MethodElementName locateMethod){
        List<SuspiciousVariable> result = new ArrayList<>();

        // （1）ローカル変数
        List<LocalVariable> locals;
        try {
            locals = frame.visibleVariables();
        } catch (AbsentInformationException e) {
            throw new RuntimeException(e);
        }
        Map<LocalVariable, Value> localVals = frame.getValues(locals);
        localVals.forEach((lv, v) -> {
            //配列の場合[0]のみ観測
            if(v instanceof ArrayReference ar){
                if(ar.length() == 0) return;
                if(ar.getValue(0) == null) return;
                if(!(ar.getValue(0) instanceof PrimitiveValue)) return;
                result.add(new SuspiciousLocalVariable(
                        this.targetTestCaseName,
                        locateMethod.fullyQualifiedName(),
                        lv.name(),
                        ar.getValue(0).toString(),
                        true,
                        0
                ));
            }
            if(v instanceof PrimitiveValue || (v != null && v.type().name().equals("java.lang.String"))) {
                result.add(new SuspiciousLocalVariable(
                        this.targetTestCaseName,
                        locateMethod.fullyQualifiedName(),
                        lv.name(),
                        v.toString(),
                        true
                ));
            }
        });

        // (2) インスタンスフィールド
        ObjectReference thisObj = frame.thisObject();
        if (thisObj != null) {
            ReferenceType  rt = thisObj.referenceType();
            for (Field f : rt.visibleFields()) {
                if (f.isStatic()) continue;
                if(thisObj.getValue(f) == null) continue;

                result.add(new SuspiciousFieldVariable(
                        this.targetTestCaseName,
                        locateMethod.classElementName,
                        f.name(),
                        thisObj.getValue(f).toString(),
                        true
                ));
            }
        }
        return result;
    }
}
