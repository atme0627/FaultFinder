package jisd.fl.probe;

import jisd.fl.probe.assertinfo.VariableInfo;
import org.apache.commons.lang3.tuple.Pair;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

public class ProbeResult {
    private VariableInfo vi;

    //これらはStatementNodeに置き換えられる
    //private Pair<Integer, Integer> lines;
    //private String src;
    //probeLineで観測された変数の値のペア
    //private Map<String, String> valuesInLine;

    private String probeMethod;
    //呼び出し側のメソッドと呼び出している行
    private Pair<Integer, String> callerMethod;
    //falseの場合はその変数の欠陥が引数由来
    private boolean isArgument = false;

    //private LocalDateTime createAt;

    //実際にactualとなっていたことが観測された行
    private int watchedAt;

    //probeLineの特定ができなかったかどうか
    private boolean notFound = false;
    public ProbeResult(){
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
        return lines;
    }

    public void setLines(Pair<Integer, Integer> lines) {
        this.lines = lines;
    }

    public String getSrc() {
        return src;
    }

    public void setSrc(String src) {
        this.src = src;
    }

    public boolean isArgument() {
        return isArgument;
    }

    public void setArgument(boolean argument) {
        isArgument = argument;
    }

    public VariableInfo getVariableInfo() {
        return vi;
    }

    public void setVariableInfo(VariableInfo vi) {
        this.vi = vi;
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
