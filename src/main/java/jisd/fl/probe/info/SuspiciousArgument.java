package jisd.fl.probe.info;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import jisd.debug.EnhancedDebugger;
import jisd.fl.util.TestUtil;
import jisd.fl.util.analyze.CodeElementName;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

public class SuspiciousArgument extends SuspiciousExpression {
    //引数を与え実行しようとしているメソッド
    private final String calleeMethodName;
    //何番目の引数に与えられたexprかを指定
    private final int argIndex;
    protected SuspiciousArgument(CodeElementName failedTest,
                                 CodeElementName locateClass,
                                 int locateLine,
                                 String actualValue,
                                 String calleeMethodName,
                                 int argIndex) {
        super(failedTest, locateClass, locateLine, actualValue);
        this.argIndex = argIndex;
        this.expr = extractExpr();
        this.calleeMethodName = calleeMethodName;
    }

    @Override
    //引数のindexを指定してその引数の評価の直前でsuspendするのは激ムズなのでやらない
    //引数を区別せず、引数の評価の際に呼ばれたすべてのメソッドについて情報を取得し
    //Expressionを静的解析してexpressionで直接呼ばれてるメソッドのみに絞る
    //ex.) expressionがx.f(y.g())の時、fのみとる。y.g()はfの探索の後行われるはず
    public List<SuspiciousReturnValue> searchSuspiciousReturns() throws NoSuchElementException{
        final List<SuspiciousReturnValue> result = new ArrayList<>();
        //探索対象のmethod名リストを取得
        List<String> targetMethodName = targetMethodName();
        //対象の引数内の最初のmethodCallがstmtで何番目か
        int targetCallCount = getCallCountBeforeTargetArgEval();
        //methodCallの回数をカウント
        int[] callCount = new int[]{0};

        //Debugger生成
        String main = TestUtil.getJVMMain(this.failedTest);
        String options = TestUtil.getJVMOption();
        EnhancedDebugger eDbg = new EnhancedDebugger(main, options);
        //調査対象の行実行に到達した時に行う処理を定義
        EnhancedDebugger.BreakpointHandler handler = (vm, bpe) -> {
            //既に情報が取得できている場合は終了
            if(!result.isEmpty()) return;

            List<SuspiciousReturnValue> resultCandidate = new ArrayList<>();
            EventRequestManager manager = vm.eventRequestManager();

            //このスレッドでの MethodExit を記録するリクエストを作成
            ThreadReference thread = bpe.thread();
            MethodExitRequest meReq = EnhancedDebugger.createMethodExitRequest(manager, thread);
            //メソッドの呼び出しが行われたことを検知するステップリクエストを作成
            //ステップイベントの通知タイミングで、今調査していた行が調べたい行だったかを確認
            StepRequest stepReq = EnhancedDebugger.createStepOverRequest(manager, thread);
            //目的の行であったかの判断は、メソッドに入った時の引数の値で確認する。
            MethodEntryRequest mEntryReq = EnhancedDebugger.createMethodEntryRequest(manager, thread);

            //一旦 resume して、内部ループで MethodExit／Step を待つ
            vm.resume();

            //直前に通知されたMethodEntryEventを保持

            boolean done = false;
            while (!done) {
                EventSet es = vm.eventQueue().remove();
                boolean doResume = true;
                for (Event ev : es) {
                    //あるメソッドに入った
                    if(ev instanceof MethodEntryEvent){
                        //entryしたメソッドが目的のcalleeメソッド
                        //かつ対象の引数が目的の値を取っている場合、目的の行実行であったとし探索終了
                        MethodEntryEvent mEntry = (MethodEntryEvent) ev;
                        //entryしたメソッドが目的のcalleeメソッドでない
                        if(mEntry.method().name().equals(calleeMethodName)) {
                            if (validateIsTargetExecution(mEntry, actualValue, argIndex)) {
                                done = true;
                                result.addAll(resultCandidate);
                                //vmをresumeしない
                                doResume = false;
                            }
                            else {
                                //ここに到達した時点で、今回の実行は目的の実行でなかった
                                done = true;
                                //vmをresumeしない
                                doResume = false;
                            }
                        }
                    }
                    //あるメソッドから抜けた
                    if (ev instanceof MethodExitEvent) {
                        callCount[0]++;
                        MethodExitEvent mee = (MethodExitEvent) ev;
                        StackFrame caller = null;
                        try {
                            //thread()がsuspendされていないと例外を投げる
                            //普通は成功するはず
                            //waitForThreadPreparation(mee.thread());
                            caller = mee.thread().frame(1);
                        } catch (IncompatibleThreadStateException e) {
                            throw new RuntimeException("Target thread must be suspended.");
                        }

                        //収集するのは指定した行で直接呼び出したメソッドのみ
                        if (mee.thread().equals(thread) && caller.location().method().equals(bpe.location().method())) {
                            if(callCount[0] >= targetCallCount) {
                                //targetMethodのみ収集
                                if (targetMethodName.contains(mee.method().name())) {
                                    CodeElementName invokedMethod = new CodeElementName(EnhancedDebugger.getFqmn(mee.method()));
                                    CodeElementName locateClass = new CodeElementName(invokedMethod.getFullyQualifiedClassName());
                                    int locateLine = mee.location().lineNumber();
                                    String actualValue = mee.returnValue().toString();
                                    SuspiciousReturnValue suspReturn = new SuspiciousReturnValue(
                                            this.failedTest,
                                            locateClass,
                                            locateLine,
                                            actualValue,
                                            invokedMethod
                                    );
                                    resultCandidate.add(suspReturn);
                                }
                            }
                        }
                    }
                    //調査対象の行の実行が終了
                    //ここに到達した時点で、今回の実行は目的の実行でなかった
                    if (ev instanceof StepEvent) {
                        done = true;
                        //vmをresumeしない
                        doResume = false;
                    }
                }
                if(doResume){
                    vm.resume();
                }
            }
            //動的に作ったリクエストを無効化
            meReq.disable();
            stepReq.disable();
            mEntryReq.disable();
        };

        //VMを実行し情報を収集
        eDbg.handleAtBreakPoint(this.locateClass.getFullyQualifiedClassName(), this.locateLine, handler);
        if(result.isEmpty()){
            throw new NoSuchElementException("Could not confirm [ " + calleeMethodName
                    + "(argment " + argIndex + ") == " + this.actualValue
                    + " ] on " + this.locateClass + " line:" + this.locateLine);
        }
        return result;
    }

    static private boolean validateIsTargetExecution(MethodEntryEvent mEntry, String actualValue, int argIndex){
        try {
            //対象の引数が目的の値を取っている
            List<Value> args = mEntry.thread().frame(0).getArgumentValues();
            return args.size() > argIndex && args.get(argIndex).toString().equals(actualValue);
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException("Target thread must be suspended.");
        }
    }

    //引数の静的解析により、return位置を調べたいmethod一覧を取得する
    private List<String> targetMethodName(){
        return this.expr.findAll(MethodCallExpr.class)
                .stream()
                .filter(mce -> mce.findAncestor(MethodCallExpr.class).isEmpty())
                .map(mce -> mce.getName().toString())
                .collect(Collectors.toList());
    }

    //対象の引数の演算の前に何回メソッド呼び出しが行われるかを計算する。
    //まず、stmtでのメソッド呼び出しをJava の実行時評価順でソートしたリストを取得
    //はじめのノードから順に探し、親にexprを持つものがあったら、その時のindexが求めたい値
    private int getCallCountBeforeTargetArgEval(){
        List<MethodCallExpr> calls = new ArrayList<>();
        Expression targetExpr = extractExpr(false);
        stmt.accept(new EvalOrderVisitor(), calls);
        for(MethodCallExpr call : calls){
            if(call == targetExpr || call.findAncestor(Node.class, anc -> anc == targetExpr).isPresent()){
                return calls.indexOf(call) + 1;
            }
        }
        throw new RuntimeException("Something is wrong.");
    }

    /**
     * Java の実行時評価順で MethodCallExpr を収集する Visitor
     */
    public static class EvalOrderVisitor extends VoidVisitorAdapter<List<MethodCallExpr>> {
        @Override
        public void visit(MethodCallExpr mce, List<MethodCallExpr> collector) {
            // 1) レシーバ（scope）があれば先に評価
            mce.getScope().ifPresent(scope -> scope.accept(this, collector));
            // 2) 引数を左から順に評価
            for (Expression arg : mce.getArguments()) {
                arg.accept(this, collector);
            }
            // 3) 最後に「呼び出しイベント」として自分自身を追加
            collector.add(mce);
        }
    }

    @Override
    public List<SuspiciousVariable> neighborSuspiciousVariables() {
        return List.of();
    }

    @Override

    protected Expression extractExpr() {
        return extractExpr(true);
    }
    protected Expression extractExpr(boolean deleteParentNode) {
        try {
            List<Expression> args = stmt.findFirst(MethodCallExpr.class).orElseThrow().getArguments();
            if(deleteParentNode){
                Expression result = args.get(argIndex).clone();
                //親ノードの情報を消す
                result.setParentNode(null);
                return result;
            }
            return args.get(argIndex);
        } catch (NoSuchElementException | IndexOutOfBoundsException e){
            throw new RuntimeException("Cannot extract expression from [" + locateClass + ":" + locateLine + "].");
        }
    }
}
