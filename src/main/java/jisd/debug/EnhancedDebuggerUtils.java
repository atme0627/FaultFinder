package jisd.debug;

import com.sun.jdi.*;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.analyze.MethodElementName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EnhancedDebuggerで使用するユーティリティクラス。
 * 例えばstackFrameに対する処理など。
 * ゆくゆくはEnhanceDebuggerに統合したい。
 */
public class EnhancedDebuggerUtils {

    /**
     * suspiciousVariableFinderのwatchAllValuesInAssertLineで使われるメソッド。
     * stackframeから変数情報を取得して、SuspiciousVariableに変換する。
     * 他のとこでも使ってそうだから切り分けて共通化を図る。
     * @param targetTestCaseName
     * @param frame
     * @param locateMethod
     * @return
     */
    public static List<SuspiciousVariable> watchAllVariablesInLineForSuspiciousVariableFinder(MethodElementName targetTestCaseName, StackFrame frame, MethodElementName locateMethod){
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
                result.add(new SuspiciousVariable(
                        targetTestCaseName,
                        locateMethod.getFullyQualifiedMethodName(),
                        lv.name() + "[0]",
                        ar.getValue(0).toString(),
                        true,
                        false,
                        0
                ));
            }
            if(v instanceof PrimitiveValue || (v != null && v.type().name().equals("java.lang.String"))) {
                result.add(new SuspiciousVariable(
                        targetTestCaseName,
                        locateMethod.getFullyQualifiedMethodName(),
                        lv.name(),
                        v.toString(),
                        true,
                        false
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

                result.add(new SuspiciousVariable(
                        targetTestCaseName,
                        locateMethod.getFullyQualifiedMethodName(),
                        f.name(),
                        thisObj.getValue(f).toString(),
                        true,
                        true
                ));
            }
        }
        return result;
    }
}
