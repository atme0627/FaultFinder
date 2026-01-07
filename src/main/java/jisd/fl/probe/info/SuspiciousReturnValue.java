package jisd.fl.probe.info;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import jisd.debug.EnhancedDebugger;
import jisd.fl.probe.record.TracedValue;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.probe.record.TracedValuesAtLine;
import jisd.fl.util.TestUtil;
import jisd.fl.core.entity.MethodElementName;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "failedTest", "locateMethod", "locateLine", "stmt", "expr", "actualValue", "children" })

public class SuspiciousReturnValue extends SuspiciousExpression {
    public SuspiciousReturnValue(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue) {
        super(failedTest, locateMethod, locateLine, actualValue);
        this.expr = ExtractExprReturnValue.extractExprReturnValue(stmt);
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
        this.expr = ExtractExprReturnValue.extractExprReturnValue(stmt);
    }
    
    @Override
    public List<SuspiciousReturnValue> searchSuspiciousReturns() throws NoSuchElementException {
        return JDISuspReturn.searchSuspiciousReturns(this);
    }

    static private boolean validateIsTargetExecution(MethodExitEvent recent, String actualValue){
        return TmpJDIUtils.getValueString(recent.returnValue()).equals(actualValue);
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
