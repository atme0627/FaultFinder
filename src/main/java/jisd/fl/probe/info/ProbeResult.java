package jisd.fl.probe.info;

import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.util.analyze.CodeElementName;
import jisd.fl.util.analyze.MethodElement;
import jisd.fl.util.analyze.StatementElement;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.stream.Collectors;

public class ProbeResult {
    //probe対象の変数
    private final SuspiciousVariable vi;

    //変数の原因行
    private final StatementElement stmt;
    private final CodeElementName probeMethodName;
    private final int probeIterateNum;

    //原因行で観測された他の変数とその値
    private TracedValueCollection neighborVariables;


    //trueの場合はその変数の欠陥が引数由来
    private boolean isCausedByArgument;
    //呼び出し側のメソッドと呼び出している行
    private Pair<Integer, MethodElement> callerMethod;
    private CodeElementName calleeMethodName;

    //probeLineの特定ができなかったかどうか
    private boolean notFound = false;

    public ProbeResult(SuspiciousVariable vi, StatementElement stmt, CodeElementName probeMethodName){
        this.vi = vi;
        this.stmt = stmt;
        this.probeMethodName = probeMethodName;
        this.isCausedByArgument = false;
        this.probeIterateNum = 0;
    }

    public ProbeResult(SuspiciousVariable vi, StatementElement stmt, CodeElementName probeMethodName, int probeIterateNum){
        this.vi = vi;
        this.stmt = stmt;
        this.probeMethodName = probeMethodName;
        this.isCausedByArgument = false;
        this.probeIterateNum = probeIterateNum;
    }


    public String getProbeMethodName() {
        return probeMethodName.getFullyQualifiedMethodName();
    }

    public Pair<Integer, MethodElement> getCallerMethod() {
        return Pair.of(callerMethod.getLeft(), callerMethod.getRight());
    }

    public void setCallerMethod(Pair<Integer, MethodElement> callerMethod) {
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

    public SuspiciousVariable getVariableInfo() {
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

    public boolean isNotFound() {
        return notFound;
    }

    public void setNotFound(boolean notFound) {
        this.notFound = notFound;
    }

    public CodeElementName probeMethod(){
        return probeMethodName;
    }

    public int probeLine(){
        return stmt.statement().getBegin().get().line;
    }

    //loop内に原因行がある場合、何回目のループのものかを返す必要がある
    public int probeIterateNum(){
        return probeIterateNum;
    }

    static public ProbeResult notFound(){
        ProbeResult notFound = new ProbeResult(null, null, null);
        notFound.setNotFound(true);
        return notFound;
    }

    public CodeElementName getCalleeMethodName() {
        return calleeMethodName;
    }

    public int getCallLocationLine(){
        return callerMethod.getLeft();
    }

    public StatementElement getProbeStmt(){
        return stmt;
    }

    public void setCalleeMethodName(CodeElementName calleeMethodName) {
        this.calleeMethodName = calleeMethodName;
    }

    //TODO: ProbeResultをSuspiciousExpressionに置き換える変更を行うための一時的なメソッド
    @Deprecated
    public ProbeResult convertSuspExpr(SuspiciousAssignment se){
        return new ProbeResult(
                se.getAssignTarget(),
                new StatementElement(se.getStmt()),
                se.locateClass
        );
    }
}
