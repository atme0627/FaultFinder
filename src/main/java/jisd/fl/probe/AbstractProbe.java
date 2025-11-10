package jisd.fl.probe;

import jisd.debug.util.ProbeLineSearcher;
import jisd.debug.util.TargetVariableTracer;
import jisd.fl.probe.info.*;
import jisd.fl.probe.record.TracedValue;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.util.analyze.*;

import java.util.*;

public abstract class AbstractProbe {

    SuspiciousVariable firstTarget;
    MethodElementName failedTest;

    public AbstractProbe(SuspiciousVariable target) {
        this.firstTarget = target;
        this.failedTest = firstTarget.getFailedTest();
    }

    /**
     * 与えられたSuspiciousVariableに対して、その直接的な原因となるExpressionをSuspiciousExpressionとして返す
     * 原因が呼び出し元の引数にある場合は、その引数のExprに対応するものを返す
     *
     * @param suspVar
     * @return
     */
    protected Optional<SuspiciousExpression> probing(SuspiciousVariable suspVar) {
        //ターゲット変数が変更されうる行を観測し、全変数の情報を取得
        System.out.println("    >> Probe Info: Running debugger and extract watched info.");
        TargetVariableTracer tracer = new TargetVariableTracer(suspVar);
        TracedValueCollection tracedValues = tracer.traceValuesOfTarget();

        tracedValues.printAll();
        //対象の変数に変更が起き、actualを取るようになった行（原因行）を探索
        List<TracedValue> watchedValues = tracedValues.getAll();
        System.out.println("    >> Probe Info: Searching probe line.");
        ProbeLineSearcher searcher = new ProbeLineSearcher(watchedValues, suspVar);
        Optional<SuspiciousExpression> result = searcher.searchProbeLine();
        return result;
    }
}