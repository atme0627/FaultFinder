package jisd.fl.infra.jdi;

import com.sun.jdi.*;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.StepEvent;
import jisd.fl.core.domain.port.SuspiciousExpressionFactory;
import jisd.fl.core.entity.TracedValue;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousFieldVariable;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.core.entity.susp.SuspiciousReturnValue;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

public class JDIUtils {
    private static final Logger logger = LoggerFactory.getLogger(JDIUtils.class);

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

    static public boolean validateIsTargetExecutionArg(MethodEntryEvent mEntry, String actualValue, int argIndex){
        try {
            //対象の引数が目的の値を取っている
            List<Value> args = mEntry.thread().frame(0).getArgumentValues();
            return args.size() > argIndex && JDIUtils.getValueString(args.get(argIndex)).equals(actualValue);
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException("Target thread must be suspended.");
        }
    }

    public static String getValueString(Value v){
        if(v == null) return "null";
        if(v instanceof ObjectReference obj){
            //プリミティブ型のラッパークラスの名前
            if(JDIUtils.isPrimitiveWrapper(obj.referenceType())) {
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

    /**
     * フィールドの値を取得する。
     *
     * @param frame     スタックフレーム
     * @param fieldName フィールド名
     * @return フィールドの値の文字列表現
     * @throws NoSuchElementException フィールドが見つからない場合
     * @throws IllegalStateException  静的コンテキストからインスタンスフィールドにアクセスしようとした場合
     */
    public static String getFieldValue(StackFrame frame, String fieldName) {
        ObjectReference targetObject = frame.thisObject();
        ReferenceType refType = (targetObject != null)
                ? targetObject.referenceType()
                : frame.location().declaringType();

        Field field = refType.fieldByName(fieldName);
        if (field == null) {
            throw new NoSuchElementException("Field not found: " + fieldName);
        }

        Value value;
        if (field.isStatic()) {
            value = refType.getValue(field);
        } else {
            if (targetObject == null) {
                throw new IllegalStateException("Cannot access instance field from static context: " + fieldName);
            }
            value = targetObject.getValue(field);
        }

        return getValueString(value);
    }

    /**
     * ローカル変数の値を取得する。
     *
     * @param frame        スタックフレーム
     * @param variableName 変数名
     * @return 変数の値の文字列表現
     * @throws NoSuchElementException    変数が見つからない場合
     * @throws AbsentInformationException デバッグ情報が不足している場合
     */
    public static String getLocalVariableValue(StackFrame frame, String variableName)
            throws AbsentInformationException {
        LocalVariable lvalue = frame.visibleVariables().stream()
                .filter(lv -> lv.name().equals(variableName))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Local variable not found: " + variableName));

        return getValueString(frame.getValue(lvalue));
    }

    /**
     * 変数の値を取得する（フィールドまたはローカル変数）。
     *
     * @param frame        スタックフレーム
     * @param variableName 変数名
     * @param isField      フィールドの場合 true
     * @return 変数の値の文字列表現
     * @throws AbsentInformationException デバッグ情報が不足している場合
     */
    public static String getVariableValue(StackFrame frame, String variableName, boolean isField)
            throws AbsentInformationException {
        return isField
                ? getFieldValue(frame, variableName)
                : getLocalVariableValue(frame, variableName);
    }

    /**
     * 代入後の変数の現在値が actualValue と一致するか検証する。
     * 一致すれば目的の実行（代入）であると判断する。
     *
     * @param se           ステップイベント（代入行の実行直後）
     * @param assignTarget 検証対象の代入先変数
     * @return 目的の実行であれば true
     */
    public static boolean validateIsTargetExecution(StepEvent se, SuspiciousVariable assignTarget) {
        if (!assignTarget.isPrimitive()) {
            throw new RuntimeException("参照型はまだサポートされていません: " + assignTarget.variableName());
        }
        if (assignTarget.isArray()) {
            throw new RuntimeException("配列型はまだサポートされていません: " + assignTarget.variableName());
        }

        try {
            StackFrame frame = se.thread().frame(0);
            String evaluatedValue = switch (assignTarget) {
                case SuspiciousFieldVariable _ -> getFieldValue(frame, assignTarget.variableName());
                case SuspiciousLocalVariable _ -> getLocalVariableValue(frame, assignTarget.variableName());
            };
            return evaluatedValue.equals(assignTarget.actualValue());
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException("対象スレッドが中断状態ではありません", e);
        } catch (AbsentInformationException e) {
            throw new RuntimeException("デバッグ情報が不足しています（-g オプションでコンパイルされていない可能性）", e);
        } catch (NoSuchElementException e) {
            // 変数が存在しない → 目的の実行ではない
            return false;
        }
    }

    /**
     * MethodExitEvent から SuspiciousReturnValue を生成する。
     *
     * @param mee        メソッド終了イベント
     * @param failedTest 失敗テストメソッド
     * @param factory    SuspiciousExpression ファクトリ
     * @return 生成された SuspiciousReturnValue（生成失敗時は empty）
     */
    public static Optional<SuspiciousReturnValue> createSuspiciousReturnValue(
            MethodExitEvent mee, MethodElementName failedTest, SuspiciousExpressionFactory factory) {
        MethodElementName invokedMethod = new MethodElementName(EnhancedDebugger.getFqmn(mee.method()));
        int locateLine = mee.location().lineNumber();
        String actualValue = getValueString(mee.returnValue());
        try {
            return Optional.of(factory.createReturnValue(failedTest, invokedMethod, locateLine, actualValue));
        } catch (RuntimeException e) {
            logger.debug("SuspiciousReturnValue の作成に失敗: {} (method={}, line={})",
                    e.getMessage(), invokedMethod, locateLine);
            return Optional.empty();
        }
    }

    /**
     * 呼び出し元が対象の位置（メソッドと行番号）かどうかを確認する。
     *
     * @param thread       対象スレッド
     * @param locateMethod 期待する呼び出し元メソッド
     * @param locateLine   期待する呼び出し元行番号
     * @return 対象位置からの呼び出しの場合 true
     */
    public static boolean isCalledFromTargetLocation(
            ThreadReference thread, MethodElementName locateMethod, int locateLine) {
        try {
            if (thread.frameCount() < 2) {
                return false;
            }
            StackFrame callerFrame = thread.frame(1);
            Location callerLocation = callerFrame.location();

            return callerLocation.declaringType().name().equals(locateMethod.fullyQualifiedClassName())
                    && callerLocation.method().name().equals(locateMethod.shortMethodName())
                    && callerLocation.lineNumber() == locateLine;
        } catch (IncompatibleThreadStateException e) {
            logger.warn("呼び出し元の確認中にスレッドがサスペンド状態ではありません: {}", e.getMessage());
            return false;
        }
    }

    public static List<TracedValue> watchAllVariablesInLine(StackFrame frame, int locateLine){
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
                        JDIUtils.getValueString(ar.getValue(0)),
                        locateLine
                ));
            }

            result.add(new TracedValue(
                    LocalDateTime.MIN,
                    lv.name(),
                    JDIUtils.getValueString(v),
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
                        JDIUtils.getValueString(thisObj.getValue(f)),
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
                    JDIUtils.getValueString(rt.getValue(f)),
                    locateLine
            ));
        }
        return result;
    }
}
