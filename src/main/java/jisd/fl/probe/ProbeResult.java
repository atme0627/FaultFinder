package jisd.fl.probe;

import jisd.fl.probe.assertinfo.VariableInfo;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.Set;

public class ProbeResult {
    private Pair<Integer, Integer> lines;
    private String src;
    private String probeMethod;
    //呼び出し側のメソッドと呼び出している行
    private Pair<Integer, String> callerMethod;
    private Set<String> siblingMethods;
    //falseの場合はその変数の欠陥が引数由来
    private boolean isArgument = false;
    private VariableInfo vi;
    //probeLineで観測された変数の値のペア
    private Map<String, String> valuesInLine;

    public ProbeResult(){
    }

    public String getProbeMethod() {
        return probeMethod;
    }

    public Pair<Integer, String> getCallerMethod() {
        return callerMethod;
    }

    public Set<String> getSiblingMethods() {
        return siblingMethods;
    }

    void setProbeMethod(String probeMethod) {
        this.probeMethod = probeMethod;
    }

    void setCallerMethod(Pair<Integer, String> callerMethod) {
        this.callerMethod = callerMethod;
    }

    void setSiblingMethods(Set<String> siblingMethods) {
        this.siblingMethods = siblingMethods;
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
}
