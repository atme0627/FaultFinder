package experiment.util;

import com.github.javaparser.ast.expr.Expression;
import com.sun.jdi.InvalidStackFrameException;
import com.sun.jdi.VMDisconnectedException;
import jisd.debug.*;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.probe.record.TracedValuesAtLine;
import jisd.fl.util.TestUtil;
import jisd.fl.util.analyze.*;

import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.stream.Collectors;

public class SuspiciousVariableFinder {
    private final TestMethodElement targetTestCase;
    private final MethodElementName targetTestCaseName;

    public SuspiciousVariableFinder(MethodElementName targetTestCaseName) throws NoSuchFileException {
        this.targetTestCaseName = targetTestCaseName;
        this.targetTestCase = TestMethodElement.getTestMethodElementByName(targetTestCaseName);
    }

    private int getAssertLine(){
        return 1;
    }

    public List<SuspiciousVariable> find(){
        List<SuspiciousVariable> result = new ArrayList<>();
        List<Expression> assertActualExpr = targetTestCase.findAssertActualExpr();

        for(Expression actualExpr : assertActualExpr){
            int assertLine = actualExpr.getRange().get().begin.line;
            TracedValueCollection allWatchedValues = traceAllValuesAtLine(assertLine);
        }
        return result;
    }


    //line行目で、全ての変数が取っている値を記録する
    private TracedValueCollection traceAllValuesAtLine(int line){
        String main = TestUtil.getJVMMain(targetTestCaseName);
        String options = TestUtil.getJVMOption();
        Debugger dbg = new Debugger(main, options);

        dbg.setMain(targetTestCaseName.getFullyQualifiedClassName());
        Optional<Point> watchPointAtLine = dbg.watch(line);

        //run Test debugger
        try {
            dbg.run(3000);
        } catch (VMDisconnectedException | InvalidStackFrameException e) {
            System.err.println(e);
        }

        //この行で値が観測されることが保証されている
        List<DebugResult> drs = new ArrayList<>(watchPointAtLine.get().getResults().values());
        Location loc = drs.get(0).getLocation();
        //行のnthLoop番目のvalueInfoを取得
        List<ValueInfo> valuesAtLine = drs.stream()
                .map(DebugResult::getValues)
                .map(vis -> vis.get(0))
                .collect(Collectors.toList());

        TracedValueCollection watchedValues = new TracedValuesAtLine(valuesAtLine, loc);
        dbg.exit();
        dbg.clearResults();
        return watchedValues;
    }


}
