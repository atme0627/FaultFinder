package jisd.fl.probe;

import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.util.analyze.StatementElement;
import org.apache.commons.lang3.tuple.Pair;

import java.time.LocalDateTime;
import java.util.Map;

public class ProbeResult {
    //probe対象の変数
    private final VariableInfo vi;

    //変数の原因行
    private final StatementElement stmt;

    //probeLineで観測された変数の値のペア
    private Map<String, String> valuesInLine;

    private String probeMethod;
    //呼び出し側のメソッドと呼び出している行
    private Pair<Integer, String> callerMethod;
    //falseの場合はその変数の欠陥が引数由来
    private final boolean isCausedByArgument;

    private LocalDateTime createAt;

    //実際にactualとなっていたことが観測された行
    private int watchedAt;

    //probeLineの特定ができなかったかどうか
    private boolean notFound = false;

    //probeの結果、原因がパラメータとして渡された変数にある場合
    public ProbeResult(VariableInfo vi){
        this.vi = vi;
        this.stmt = null;
        this.isCausedByArgument = true;
    }

    public ProbeResult(VariableInfo vi, StatementElement stmt){
        this.vi = vi;
        this.stmt = stmt;
        this.isCausedByArgument = false;
    }

    public String getProbeMethod() {
        return probeMethod;
    }

    public Pair<Integer, String> getCallerMethod() {
        return callerMethod;
    }

    void setProbeMethod(String probeMethod) {
        this.probeMethod = probeMethod;
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
        return valuesInLine;
    }

    public void setValuesInLine(Map<String, String> valuesInLine) {
        this.valuesInLine = valuesInLine;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
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
}
