package jisd.fl.probe.info;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.core.entity.MethodElementName;

import java.util.List;
import java.util.NoSuchElementException;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "failedTest", "locateMethod", "locateLine", "stmt", "expr", "actualValue", "children" })

public class SuspiciousReturnValue extends SuspiciousExpression {
    public SuspiciousReturnValue(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue) {
        super(failedTest, locateMethod, locateLine, actualValue);
        this.expr = JavaParserSuspReturn.extractExprReturnValue(stmt);
    }

    @JsonCreator
    private SuspiciousReturnValue(
            @JsonProperty("failedTest") String failedTest,
            @JsonProperty("locateMethod") String locateMethod,
            @JsonProperty("locateLine") int locateLine,
            @JsonProperty("actualValue") String actualValue,
            @JsonProperty("children") List<SuspiciousExpression> children
    ){
        super(failedTest, locateMethod, locateLine, actualValue, children);
        this.expr = JavaParserSuspReturn.extractExprReturnValue(stmt);
    }
    
    @Override
    public List<SuspiciousReturnValue> searchSuspiciousReturns() throws NoSuchElementException {
        return JDISuspReturn.searchSuspiciousReturns(this);
    }


    @Override
    public String toString(){
        return "[ SUSPICIOUS RETURN VALUE ]\n" + "    " + locateMethod.methodSignature + "{\n       ...\n" + super.toString() + "\n       ...\n    }";
    }

    @Override
    protected TracedValueCollection traceAllValuesAtSuspExpr(int sleepTime, SuspiciousExpression thisSuspExpr){
        return JDISuspReturn.traceAllValuesAtSuspExpr(sleepTime, thisSuspExpr);
    }
}
