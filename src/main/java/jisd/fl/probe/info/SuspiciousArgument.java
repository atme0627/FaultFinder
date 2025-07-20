package jisd.fl.probe.info;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import jisd.debug.EnhancedDebugger;
import jisd.fl.util.TestUtil;
import jisd.fl.util.analyze.MethodElementName;

import java.util.*;
import java.util.stream.Collectors;

public class SuspiciousArgument extends SuspiciousExpression {
    //引数を与え実行しようとしているメソッド
    private final MethodElementName calleeMethodName;
    //何番目の引数に与えられたexprかを指定
    private final int argIndex;
    //その行の中で呼び出し元のメソッドの後に何回他のメソッドが呼ばれるか
    private final int CallCountAfterTargetInLine;

    protected SuspiciousArgument(MethodElementName failedTest,
                                 MethodElementName locateMethod,
                                 int locateLine,
                                 String actualValue,
                                 MethodElementName calleeMethodName,
                                 int argIndex,
                                 int CallCountAfterTargetInLine) {
        super(failedTest, locateMethod, locateLine, actualValue);
        this.argIndex = argIndex;
        this.calleeMethodName = calleeMethodName;
        this.CallCountAfterTargetInLine = CallCountAfterTargetInLine;
        this.expr = extractExpr();
    }

    @Override
    //引数のindexを指定してその引数の評価の直前でsuspendするのは激ムズなのでやらない
    //引数を区別せず、引数の評価の際に呼ばれたすべてのメソッドについて情報を取得し
    //Expressionを静的解析してexpressionで直接呼ばれてるメソッドのみに絞る
    //ex.) expressionがx.f(y.g())の時、fのみとる。y.g()はfの探索の後行われるはず
    public List<SuspiciousReturnValue> searchSuspiciousReturns() throws NoSuchElementException{
        final List<SuspiciousReturnValue> result = new ArrayList<>();
        if(!hasMethodCalling()) return result;

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
                        if(mEntry.method().name().equals(calleeMethodName.getShortMethodName())) {
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
                                    MethodElementName invokedMethod = new MethodElementName(EnhancedDebugger.getFqmn(mee.method()));
                                    int locateLine = mee.location().lineNumber();
                                    String actualValue = mee.returnValue().toString();
                                    SuspiciousReturnValue suspReturn = new SuspiciousReturnValue(
                                            this.failedTest,
                                            invokedMethod,
                                            locateLine,
                                            actualValue
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
        eDbg.handleAtBreakPoint(this.locateMethod.getFullyQualifiedClassName(), this.locateLine, handler);
        if(result.isEmpty()){
            throw new NoSuchElementException("Could not confirm [ " + calleeMethodName
                    + "(argment " + argIndex + ") == " + this.actualValue
                    + " ] on " + this.locateMethod + " line:" + this.locateLine);
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


    protected Expression extractExpr() {
        return extractExpr(true);
    }

    protected Expression extractExpr(boolean deleteParentNode) {
        int methodCallCount = stmt.findAll(MethodCallExpr.class).size();
        int nthCallInLine = methodCallCount - this.CallCountAfterTargetInLine;
        if(nthCallInLine <= 0) throw new RuntimeException("something is wrong");

        List<MethodCallExpr> calls = new ArrayList<>();
        stmt.accept(new EvalOrderVisitor(), calls);
        if(!calls.get(nthCallInLine - 1).getNameAsString().equals(this.calleeMethodName.getShortMethodName())) {
            throw new RuntimeException("something is wrong");
        }

        Expression result = calls.get(nthCallInLine - 1).getArgument(argIndex);
        if(deleteParentNode) {
            result = result.clone();
            //親ノードの情報を消す
            result.setParentNode(null);
            return result;
        }
        return result;
    }

    /**
     * ある変数がその値を取る原因が呼び出し元の引数のあると判明した場合に使用
     */
    static public SuspiciousArgument searchSuspiciousArgument(MethodElementName calleeMethodName, SuspiciousVariable suspVar){
        //Debugger生成
        String main = TestUtil.getJVMMain(suspVar.getFailedTest());
        String options = TestUtil.getJVMOption();
        EnhancedDebugger eDbg = new EnhancedDebugger(main, options);

        //探索によって求める値
        MethodElementName[] locateMethod = new MethodElementName[1];
        int[] locateLine = new int[1];
        int[] argIndex = new int[1];
        int[] callCountAfterTarget = new int[]{0};

        //調査対象の行実行に到達した時に行う処理を定義
        EnhancedDebugger.MethodEntryHandler handler = (vm, mEntry) -> {
            try {
                //呼び出しメソッドを取得
                ThreadReference thread = mEntry.thread();
                StackFrame topFrame = null;
                StackFrame callerFrame = null;
                try {
                    topFrame = thread.frame(0);
                    callerFrame = thread.frame(1);
                } catch (IncompatibleThreadStateException e) {
                    throw new RuntimeException(e);
                }


                //調査対象の変数がactualValueをとっているか確認
                Value argValue = topFrame.getValue(topFrame.visibleVariableByName(suspVar.getSimpleVariableName()));
                if(!argValue.toString().equals(suspVar.getActualValue())) return;

                //対象の引数のインデックスを取得
                List<LocalVariable> args = mEntry.method().arguments();
                for(int idx = 0; idx < args.size(); idx++){
                    if(args.get(idx).name().equals(suspVar.getSimpleVariableName())){
                        argIndex[0] = idx;
                    }
                }

                com.sun.jdi.Location callerLoc = callerFrame.location();
                locateMethod[0] = new MethodElementName(EnhancedDebugger.getFqmn(callerLoc.method()));
                locateLine[0] = callerLoc.lineNumber();

                //targetVarが呼び出し元でどのexprに対応するかを特定
                //複数回同じメソッドが呼ばれている場合も考慮
                //目的のmethodEntryの後、何回methodが呼ばれるかを解析
                mEntry.request().disable();
                callCountAfterTarget[0] = countMethodCallAfterTarget(vm, mEntry);

            } catch (AbsentInformationException e) {
                throw new RuntimeException(e);
            }
        };

        eDbg.enableOutput();
        eDbg.handleAtMethodEntry(calleeMethodName.getFullyQualifiedMethodName(), handler);

        //nullチェック
        if(locateMethod[0] == null || locateLine[0] == 0 || argIndex[0] == -1){
            throw new RuntimeException("Cannot find target argument of caller method. \n" + suspVar);
        }
        return new SuspiciousArgument(
                suspVar.getFailedTest(),
                locateMethod[0],
                locateLine[0],
                suspVar.getActualValue(),
                calleeMethodName,
                argIndex[0],
                callCountAfterTarget[0]
        );
    }

    static private int countMethodCallAfterTarget(VirtualMachine vm, MethodEntryEvent mEntry) throws InterruptedException {
        int result = 0;
        EventRequestManager manager = vm.eventRequestManager();
        StepRequest stepOutReq = EnhancedDebugger.createStepOutRequest(manager, mEntry.thread());

        //一旦resumeして、内部ループでstep outを待つ
        vm.resume();

        //Methodがreturnし呼び出し元の行についた時点でsuspend
        //必ずMethodExitEventのみのはず
        Optional<Event> oev = vm.eventQueue().remove().stream().findFirst();
        if (oev.isEmpty() || !(oev.get() instanceof StepEvent)){
            throw new RuntimeException("something is wrong");
        }
        stepOutReq.disable();
        ThreadReference thisThread = ((StepEvent) oev.get()).thread();
        // ブレークポイント地点でのコールスタックの深さを取得
        // 呼び出しメソッドの取得条件を 深さ == depthBeforeCall + 1　にすることで
        // 再帰呼び出し含め、その行で直接呼ばれたメソッドの呼び出し回数をカウントするため
        int depthBeforeCall = getCallStackDepth(thisThread);

        //この行の終わりを検知するためstepOverRequestを設置
        StepRequest stepOverReq = EnhancedDebugger.createStepOverRequest(manager, thisThread);
        //その後のメソッド呼び出し回数をカウントするためのMethodEntryRequest
        MethodEntryRequest mEntry2 = EnhancedDebugger.createMethodEntryRequest(manager, thisThread);

        //一旦resumeして、内部ループでMethodEntry / stepOverを待つ
        vm.resume();
        //イベントループ
        boolean done = false;
        while(!done) {
            EventSet es = vm.eventQueue().remove();
            for (Event ev : es) {
                //メソッド呼び出し回数をカウント
                if (ev instanceof MethodEntryEvent) {
                    MethodEntryEvent mee = (MethodEntryEvent) ev;
                    if(getCallStackDepth(mee.thread()) == depthBeforeCall + 1) result++;
                    vm.resume();
                    continue;
                }
                //調査対象の行の実行が終了
                if (ev instanceof StepEvent) {
                    done = true;
                    //vmをresumeしない
                }
            }
        }

        stepOverReq.disable();
        mEntry2.disable();
        return result;
    }

    @Override
    public String toString(){
        final String BG_GREEN = "\u001B[42m";
        final String RESET    = "\u001B[0m";

        LexicalPreservingPrinter.setup(stmt);
        extractExpr(false).getTokenRange().ifPresent(tokenRange -> {
            // 子ノードに属するすべてのトークンに色付け
            tokenRange.forEach(token -> {
                String original = token.getText();
                // ANSI エスケープシーケンスで背景黄色
                token.setText(BG_GREEN + original + RESET);
            });
            }
        );

        return "[ SUSPICIOUS ARGUMENT ]\n" + "    " + locateMethod.methodSignature + "{\n       ...\n" + LexicalPreservingPrinter.print(stmt) + "\n       ...\n    }";
    }

}
