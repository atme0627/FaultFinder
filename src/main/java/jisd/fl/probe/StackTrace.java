package jisd.fl.probe;

import jisd.fl.util.StaticAnalyzer;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.NoSuchFileException;

public class StackTrace {
    MethodCollection st = new MethodCollection();

    public boolean isEmpty(){
        return st.isEmpty();
    }

    //targetMethod: スタックトレース取得時に実行中のメソッド
    public StackTrace(String rawTrace, String targetMethod){
        parseRawTrace(rawTrace, targetMethod);
    }

    private void parseRawTrace(String rawTrace, String targetMethod) {
        String[] splitStackTrace = rawTrace.split("\\n");
        for (String e : splitStackTrace) {
            if(e.isEmpty()) continue;
            Pair<Integer, String> ne = normalizeElement(e);
            if(ne == null) return;
            st.addElement(ne);
        }
    }

    private Pair<Integer, String> normalizeElement(String e){
        StringBuilder sb = new StringBuilder(e);
        sb.setCharAt(sb.lastIndexOf("."), '#');
        //TODO: breakPointで止まらなかったとき、 >> Debugger not suspended now. のような文字列が入るため、用対処
        // exeptionなどが起きたことが原因で、methodCallingLineが実行されない場合がある。
        String method = null;
        try {
            method = sb.substring(sb.indexOf("]") + 2, sb.lastIndexOf("(") - 1);
        }
        catch (StringIndexOutOfBoundsException ex){
            System.err.println("normalize failed: " + e);
            return null;
        }

        int line = Integer.parseInt(e.substring(e.indexOf("(") + 1, e.length() - 1).substring(6));
        return Pair.of(line, method);
    }

    public int getLine(int depth){
        return st.getLine(depth);
    }

    public String getMethod(int depth){
        return st.getMethod(depth);
    }

    //TODO: locationだけ返すようにリファクタリング
    //methodはシグニチャつき
    //depthにあるメソッドが呼び出された場所とdepthにあるメソッド名を返す
    public Pair<Integer, String> getCalleeMethodAndCallLocation(int depth){
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

    //methodはシグニチャつき
    //depthにあるメソッドが呼び出した場所とdepthにあるメソッド名を返す
    public Pair<Integer, String> getCallerMethodAndCallLocation(int depth){
        int callLocation = st.getLine(depth);
        String targetClass = st.getMethod(depth).split("#")[0];
        String method = null;
        try {
            method = StaticAnalyzer.getMethodNameFormLine(targetClass, callLocation);
        } catch (NoSuchFileException e) {
            return null;
        }
        return Pair.of(callLocation, method);
    }
}
