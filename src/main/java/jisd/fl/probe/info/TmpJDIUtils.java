package jisd.fl.probe.info;

import com.sun.jdi.*;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.MethodExitEvent;
import jisd.fl.probe.record.TracedValue;

import java.time.LocalDateTime;
import java.util.*;

public class TmpJDIUtils {
    //SuspiciousExpressionリファクタリングのための一時的なクラス
    static boolean isPrimitiveWrapper(Type type) {
        //プリミティブ型のラッパークラスの名前
        final Set<String> WRAPPER_CLASS_NAMES = new HashSet<>(Arrays.asList(
                Boolean.class.getName(),
                Byte.class.getName(),
                Character.class.getName(),
                Short.class.getName(),
                Integer.class.getName(),
                Long.class.getName(),
                Float.class.getName(),
                Double.class.getName(),
                Void.class.getName()
        ));

        if (type instanceof ClassType) {
            return WRAPPER_CLASS_NAMES.contains(type.name());
        }
        return false;
    }

    static public boolean validateIsTargetExecution(MethodExitEvent recent, String actualValue){
        return TmpJDIUtils.getValueString(recent.returnValue()).equals(actualValue);
    }

    static public boolean validateIsTargetExecutionArg(MethodEntryEvent mEntry, String actualValue, int argIndex){
        try {
            //対象の引数が目的の値を取っている
            List<Value> args = mEntry.thread().frame(0).getArgumentValues();
            return args.size() > argIndex && TmpJDIUtils.getValueString(args.get(argIndex)).equals(actualValue);
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException("Target thread must be suspended.");
        }
    }

    public static String getValueString(Value v){
        if(v == null) return "null";
        if(v instanceof ObjectReference obj){
            //プリミティブ型のラッパークラスの名前
            if(TmpJDIUtils.isPrimitiveWrapper(obj.referenceType())) {
                try {
                    Field valueField = obj.referenceType().fieldByName("value");
                    Value primitiveValue = obj.getValue(valueField);
                    return primitiveValue.toString();
                } catch (Exception e) {
                    return v.toString();
                }
            }
            return v.toString();
        }
        return v.toString();
    }

    public static int getCallStackDepth(ThreadReference th){
        try {
            return th.frameCount();
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException(e);
        }
    }

    static List<TracedValue> watchAllVariablesInLine(StackFrame frame, int locateLine){
        List<TracedValue> result = new ArrayList<>();

        // （1）ローカル変数
        List<LocalVariable> locals;
        try {
            locals = frame.visibleVariables();
        } catch (AbsentInformationException e) {
            throw new RuntimeException(e);
        }
        Map<LocalVariable, Value> localVals = frame.getValues(locals);
        localVals.forEach((lv, v) -> {
            if(v == null) return;
            //配列の場合[0]のみ観測
            if(v instanceof ArrayReference ar){
                if(ar.length() == 0) return;
                result.add(new TracedValue(
                        LocalDateTime.MIN,
                        lv.name() + "[0]",
                        TmpJDIUtils.getValueString(ar.getValue(0)),
                        locateLine
                ));
            }

            result.add(new TracedValue(
                    LocalDateTime.MIN,
                    lv.name(),
                    TmpJDIUtils.getValueString(v),
                    locateLine
            ));
        });

        // (2) インスタンスフィールド
        ObjectReference thisObj = frame.thisObject();
        if (thisObj != null) {
            ReferenceType  rt = thisObj.referenceType();
            for (Field f : rt.visibleFields()) {
                if (f.isStatic()) continue;
                result.add(new TracedValue(
                        LocalDateTime.MIN,
                        "this." + f.name(),
                        TmpJDIUtils.getValueString(thisObj.getValue(f)),
                        locateLine
                ));
            }
        }

        // (3) static フィールド
        ReferenceType rt = frame.location().declaringType();
        for (Field f : rt.visibleFields()) {
            if (!f.isStatic()) continue;
            result.add(new TracedValue(
                    LocalDateTime.MIN,
                    "this." + f.name(),
                    TmpJDIUtils.getValueString(rt.getValue(f)),
                    locateLine
            ));
        }
        return result;
    }
}
