package jisd.fl.probe;

import jisd.fl.util.StaticAnalyzer;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.NoSuchFileException;
import java.util.Map;

public class StackTrace {
    MethodCollection st = new MethodCollection();

    //targetMethod: スタックトレース取得時に実行中のメソッド
    public StackTrace(String rawTrace, String targetMethod){
        parseRawTrace(rawTrace, targetMethod);
    }

    private void parseRawTrace(String rawTrace, String targetMethod) {
        String[] splitStackTrace = rawTrace.split("\\n");
        for (String e : splitStackTrace) {
            if(e.isEmpty()) continue;
            st.addElement(normalizeElement(e));
        }
    }

    private Pair<Integer, String> normalizeElement(String e){
        StringBuilder sb = new StringBuilder(e);
        sb.setCharAt(sb.lastIndexOf("."), '#');
        String method = sb.substring(sb.indexOf("]") + 2, sb.lastIndexOf("(") - 1);
        int line = Integer.parseInt(e.substring(e.indexOf("(") + 1, e.length() - 1).substring(6));
        return Pair.of(line, method);
    }

    public int getLine(int depth){
        return st.getLine(depth);
    }

    public String getMethod(int depth){
        return st.getMethod(depth);
    }

    //methodはシグニチャつき
    public Pair<Integer, String> getMethodAndCallLocation(int depth){
        int callLocation = st.getLine(depth + 1);
        int methodLocation = st.getLine(depth);
        String targetClass = st.getMethod(depth).split("#")[0];
        String method = null;
        try {
            method = StaticAnalyzer.getMethodNameFormLine(targetClass, methodLocation);
        } catch (NoSuchFileException e) {
            return null;
        }
        return Pair.of(callLocation, method);
    }
}
