package jisd.fl.probe;

import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.util.analyze.CodeElementName;
import jisd.fl.util.analyze.StatementElement;
import org.apache.commons.lang3.tuple.Pair;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

public class ProbeResult {
    //probe対象の変数
    private final VariableInfo vi;

    //変数の原因行
    private final StatementElement stmt;
    private String probeMethodName;
    private final int probeIterateNum;

    //原因行で観測された他の変数とその値
    private TracedValueCollection neighborVariables;

    //呼び出し側のメソッドと呼び出している行
    private Pair<Integer, String> callerMethod;
    //falseの場合はその変数の欠陥が引数由来
    private final boolean isCausedByArgument;


    //実際にactualとなっていたことが観測された行
    private int watchedAt;

    //probeLineの特定ができなかったかどうか
    private boolean notFound = false;

    //probeの結果、原因がパラメータとして渡された変数にある場合
    public ProbeResult(VariableInfo vi){
        this.vi = vi;
        this.stmt = null;
        this.isCausedByArgument = true;
        this.probeIterateNum = 0;
    }

    public ProbeResult(VariableInfo vi, StatementElement stmt){
        this.vi = vi;
        this.stmt = stmt;
        this.isCausedByArgument = false;
        this.probeIterateNum = 0;
    }

    public ProbeResult(VariableInfo vi, StatementElement stmt, int probeIterateNum){
        this.vi = vi;
        this.stmt = stmt;
        this.isCausedByArgument = false;
        this.probeIterateNum = probeIterateNum;
    }


    public String getProbeMethodName() {
        return probeMethodName;
    }

    public Pair<Integer, String> getCallerMethod() {
        return callerMethod;
    }

    void setProbeMethodName(String probeMethodName) {
        this.probeMethodName = probeMethodName;
    }

    void setCallerMethod(Pair<Integer, String> callerMethod) {
        this.callerMethod = callerMethod;
    }

    public Pair<Integer, Integer> getProbeLines() {
        return Pair.of(stmt.statement().getBegin().get().line, stmt.statement().getEnd().get().line);
    }

    public String getSrc() {
        return stmt == null ? "" : stmt.statement().toString();
    }

    public boolean isCausedByArgument() {
        return isCausedByArgument;
    }

    public VariableInfo getVariableInfo() {
        return vi;
    }

    public Map<String, String> getValuesInLine() {
        return neighborVariables.getAll()
                .stream()
                .collect(Collectors.toMap(tv -> tv.variableName, tv -> tv.value));
    }

    public void setValuesInLine(TracedValueCollection neighborVariables) {
        this.neighborVariables = neighborVariables;
    }

    public int getWatchedAt() {
        return watchedAt;
    }

    public void setWatchedAt(int watchedAt) {
        this.watchedAt = watchedAt;
    }

    public boolean isNotFound() {
        return notFound;
    }

    public void setNotFound(boolean notFound) {
        this.notFound = notFound;
    }

    public CodeElementName probeMethod(){
        return new CodeElementName(probeMethodName);
    }

    public int probeLine(){
        return stmt.statement().getBegin().get().line;
    }

    //loop内に原因行がある場合、何回目のループのものかを返す必要がある
    public int probeIterateNum(){
        return probeIterateNum;
    }

    static public ProbeResult notFound(){
        ProbeResult notFound = new ProbeResult(null);
        notFound.setNotFound(true);
        return notFound;
    }
}
