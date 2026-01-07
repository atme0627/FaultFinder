package jisd.fl.probe.info;

import com.fasterxml.jackson.annotation.*;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.core.entity.MethodElementName;

import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "failedTest", "locateMethod", "locateLine", "stmt", "expr", "actualValue", "children" })

public class SuspiciousAssignment extends SuspiciousExpression {

    //左辺で値が代入されている変数の情報
    @JsonIgnore
    public final SuspiciousVariable assignTarget;

    public SuspiciousAssignment(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, SuspiciousVariable assignTarget) {
        super(failedTest, locateMethod, locateLine, assignTarget.getActualValue());
        this.expr = JavaParserSuspAssign.extractExprAssign(true, stmt);
        this.assignTarget = assignTarget;
    }

    @JsonCreator
    private SuspiciousAssignment(
            @JsonProperty("failedTest") String failedTest,
            @JsonProperty("locateMethod") String locateMethod,
            @JsonProperty("locateLine") int locateLine,
            @JsonProperty("actualValue") String actualValue,
            @JsonProperty("children") List<SuspiciousExpression> children
            ){
        super(failedTest, locateMethod, locateLine, actualValue, children);
        this.assignTarget = null;
        this.expr = JavaParserSuspAssign.extractExprAssign(true, stmt);
    }

    @Override
    //TODO: 今はオブジェクトの違いを考慮していない
    public List<SuspiciousReturnValue> searchSuspiciousReturns() throws NoSuchElementException {
        return JDISuspAssign.searchSuspiciousReturns(this);
    }

    @Override
    public String toString(){
        return "[ SUSPICIOUS ASSIGNMENT ]\n" + "    " + locateMethod.methodSignature + "{\n       ...\n" + super.toString() + "\n       ...\n    }";
    }

}
