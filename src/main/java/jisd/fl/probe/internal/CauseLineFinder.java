package jisd.fl.probe.internal;

import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.probe.record.TracedValue;
import jisd.fl.probe.record.TracedValueCollection;

import java.util.List;
import java.util.Optional;

public class CauseLineFinder {
    SuspiciousVariable target;

    public CauseLineFinder(SuspiciousVariable target) {
        this.target = target;
    }
    /**
     * 与えられたSuspiciousVariableに対して、その直接的な原因となるExpressionをSuspiciousExpressionとして返す
     * 原因が呼び出し元の引数にある場合は、その引数のExprに対応するものを返す
     *
     */
    public Optional<SuspiciousExpression> find() {
        //ターゲット変数が変更されうる行を観測し、全変数の情報を取得
        System.out.println("    >> Probe Info: Running debugger and extract watched info.");
        TargetVariableTracer tracer = new TargetVariableTracer(target);
        TracedValueCollection tracedValues = tracer.traceValuesOfTarget();

        tracedValues.printAll();
        //対象の変数に変更が起き、actualを取るようになった行（原因行）を探索
        List<TracedValue> watchedValues = tracedValues.getAll();
        System.out.println("    >> Probe Info: Searching probe line.");
        ProbeLineSearcher searcher = new ProbeLineSearcher(watchedValues, target);
        Optional<SuspiciousExpression> result = searcher.searchProbeLine();
        return result;
    }
}
