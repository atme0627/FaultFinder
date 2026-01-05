package jisd.fl.probe.info;

import com.sun.jdi.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TmpStaticUtils {
    //SuspiciousExpressionリファクタリングのための一時的なクラス
    protected static boolean isPrimitiveWrapper(Type type) {
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

    public static String getValueString(Value v){
        if(v == null) return "null";
        if(v instanceof ObjectReference obj){
            //プリミティブ型のラッパークラスの名前
            if(TmpStaticUtils.isPrimitiveWrapper(obj.referenceType())) {
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

    protected static int getCallStackDepth(ThreadReference th){
        try {
            return th.frameCount();
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException(e);
        }
    }
}
