package jisd.fl.probe;

import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.util.analyze.CodeElementName;
import jisd.fl.util.analyze.MethodElement;
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
    private Pair<Integer, MethodElement> callerMethod;
    //trueの場合はその変数の欠陥が引数由来
    private boolean isCausedByArgument;


    //実際にactualとなっていたことが観測された行
    private int watchedAt;

    //probeLineの特定ができなかったかどうか
    private boolean notFound = false;

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
        return Pair.of(callerMethod.getLeft(), callerMethod.getRight().fqmn()) ;
    }

    void setProbeMethodName(String probeMethodName) {
        this.probeMethodName = probeMethodName;
    }

    void setCallerMethod(Pair<Integer, MethodElement> callerMethod) {
        this.callerMethod = callerMethod;
    }

    public String getSrc() {
        return stmt == null ? "" : stmt.statement().toString();
    }

    public void setCausedByArgument(boolean isCausedByArgument) {
        this.isCausedByArgument = isCausedByArgument;
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
        ProbeResult notFound = new ProbeResult(null, null);
        notFound.setNotFound(true);
        return notFound;
    }
}
