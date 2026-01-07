package jisd.fl.probe.info;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import jisd.debug.EnhancedDebugger;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.probe.record.TracedValue;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.probe.record.TracedValuesAtLine;
import jisd.fl.util.TestUtil;
import jisd.fl.core.entity.MethodElementName;

import java.util.*;
import java.util.stream.Collectors;
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "failedTest", "locateMethod", "locateLine", "stmt", "expr", "actualValue", "children" })

public class SuspiciousArgument extends SuspiciousExpression {
    //引数を与え実行しようとしているメソッド
    final MethodElementName calleeMethodName;
    //何番目の引数に与えられたexprかを指定
    final int argIndex;
    //その行の中で呼び出し元のメソッドの後に何回他のメソッドが呼ばれるか
    final int CallCountAfterTargetInLine;

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
        this.expr = extractExprArg();
    }
    
    @JsonCreator
    private SuspiciousArgument(
        @JsonProperty("failedTest") String failedTest,
        @JsonProperty("locateMethod") String locateMethod,
        @JsonProperty("locateLine") int locateLine,
        @JsonProperty("actualValue") String actualValue,
        @JsonProperty("children") List<SuspiciousExpression> children,
        @JsonProperty("calleeMethodName") String calleeMethodName,
        @JsonProperty("argIndex") int argIndex,
        @JsonProperty("CallCountAfterTargetInLine") int CallCountAfterTargetInLine
    ) {
        super(failedTest, locateMethod, locateLine, actualValue, children);
        this.calleeMethodName = new MethodElementName(calleeMethodName);
        this.argIndex = argIndex;
        this.CallCountAfterTargetInLine = CallCountAfterTargetInLine;
        this.expr = extractExprArg();
    }
    

    @Override
    //引数のindexを指定してその引数の評価の直前でsuspendするのは激ムズなのでやらない
    //引数を区別せず、引数の評価の際に呼ばれたすべてのメソッドについて情報を取得し
    //Expressionを静的解析してexpressionで直接呼ばれてるメソッドのみに絞る
    //ex.) expressionがx.f(y.g())の時、fのみとる。y.g()はfの探索の後行われるはず
    public List<SuspiciousReturnValue> searchSuspiciousReturns() throws NoSuchElementException{
        return JDISuspArg.searchSuspiciousReturns(this);
    }

    static private boolean validateIsTargetExecution(MethodEntryEvent mEntry, String actualValue, int argIndex){
        try {
            //対象の引数が目的の値を取っている
            List<Value> args = mEntry.thread().frame(0).getArgumentValues();
            return args.size() > argIndex && TmpJDIUtils.getValueString(args.get(argIndex)).equals(actualValue);
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException("Target thread must be suspended.");
        }
    }

    @Override
    protected TracedValueCollection traceAllValuesAtSuspExpr(int sleepTime, SuspiciousExpression thisSuspExpr){
        return JDISuspArg.traceAllValuesAtSuspExpr(sleepTime, (SuspiciousArgument) thisSuspExpr);
    }

    /**
     * Java の実行時評価順で MethodCallExpr を収集する Visitor
     */
    public static class EvalOrderVisitor extends VoidVisitorAdapter<List<Expression>> {
        @Override
        public void visit(MethodCallExpr mce, List<Expression> collector) {
            // 1) レシーバ（scope）があれば先に評価
            mce.getScope().ifPresent(scope -> scope.accept(this, collector));
            // 2) 引数を左から順に評価
            for (Expression arg : mce.getArguments()) {
                arg.accept(this, collector);
            }
            // 3) 最後に「呼び出しイベント」として自分自身を追加
            collector.add(mce);
        }

        @Override
        public void visit(ObjectCreationExpr oce, List<Expression> collector) {
            // 1) コンストラクタのスコープ（new Outer.Inner() の Outer など）がある場合は先に評価
            oce.getScope().ifPresent(scope -> scope.accept(this, collector));
            // 2) 引数を左から順に評価
            for (Expression arg : oce.getArguments()) {
                arg.accept(this, collector);
            }
            // 3) 匿名クラスボディ内にある式（必要なら追加）
            if (oce.getAnonymousClassBody().isPresent()) {
                oce.getAnonymousClassBody().get().forEach(body -> body.accept(this, collector));
            }

            // 3) 最後に「呼び出しイベント」として自分自身を追加
            collector.add(oce);
        }
    }


    protected Expression extractExprArg() {
        return extractExprArg(true, stmt, this.CallCountAfterTargetInLine, this.argIndex, this.calleeMethodName);
    }

    static protected Expression extractExprArg(boolean deleteParentNode, Statement stmt, int callCountAfterTargetInLine, int argIndex, MethodElementName calleeMethodName) {
        return JavaParserSuspArg.extractExprArg(deleteParentNode, stmt, callCountAfterTargetInLine, argIndex, calleeMethodName);
    }
    /**
     * ある変数がその値を取る原因が呼び出し元の引数のあると判明した場合に使用
     */
    static public Optional<SuspiciousArgument> searchSuspiciousArgument(MethodElementName calleeMethodName, SuspiciousVariable suspVar){
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
                LocalVariable topVar = topFrame.visibleVariableByName(suspVar.getSimpleVariableName());
                if(topVar == null) return;
                Value argValue = topFrame.getValue(topVar);
                if(!TmpJDIUtils.getValueString(argValue).equals(suspVar.getActualValue())) return;
                //対象の引数のインデックスを取得
                List<LocalVariable> args = mEntry.method().arguments();
                for(int idx = 0; idx < args.size(); idx++){
                    if(args.get(idx).name().equals(suspVar.getSimpleVariableName())){
                        argIndex[0] = idx;
                    }
                }
                //引数に含まれない場合
                if(argIndex[0] == -1){
                    return;
                }

                com.sun.jdi.Location callerLoc = callerFrame.location();
                locateMethod[0] = new MethodElementName(EnhancedDebugger.getFqmn(callerLoc.method()));
                locateLine[0] = callerLoc.lineNumber();

                //targetVarが呼び出し元でどのexprに対応するかを特定
                //複数回同じメソッドが呼ばれている場合も考慮
                //目的のmethodEntryの後、何回methodが呼ばれるかを解析
                mEntry.request().disable();
                //この段階でリクエストは全てdisabledになっている必要がある。
                //今は呼び出されたメソッドの中にいる。
                callCountAfterTarget[0] = countMethodCallAfterTarget(vm, mEntry);

            } catch (AbsentInformationException e) {
                throw new RuntimeException(e);
            }
        };

        eDbg.handleAtMethodEntry(calleeMethodName.getFullyQualifiedMethodName(), handler);

        //nullチェック
        if(locateMethod[0] == null || locateLine[0] == 0 || argIndex[0] == -1){
            System.err.println("Cannot find target argument of caller method. (may not be argument)\n" + suspVar);
            return Optional.empty();
        }
        return Optional.of(new SuspiciousArgument(
                suspVar.getFailedTest(),
                locateMethod[0],
                locateLine[0],
                suspVar.getActualValue(),
                calleeMethodName,
                argIndex[0],
                callCountAfterTarget[0]
        ));
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
        int depthBeforeCall = TmpJDIUtils.getCallStackDepth(thisThread);

        //この行の終わりを検知するためstepOverRequestを設置
        StepRequest stepOverReq = EnhancedDebugger.createStepOverRequest(manager, thisThread);
        //その後のメソッド呼び出し回数をカウントするためのMethodExitRequest
        MethodExitRequest mExit = EnhancedDebugger.createMethodExitRequest(manager, thisThread);

        //一旦resumeして、内部ループでMethodEntry / stepOverを待つ
        vm.resume();
        //イベントループ
        boolean done = false;
        while(!done) {
            EventSet es = vm.eventQueue().remove();
            for (Event ev : es) {
                //メソッド呼び出し回数をカウント
                if (ev instanceof MethodExitEvent) {
                    MethodExitEvent mee = (MethodExitEvent) ev;

                    //meeのthreadは抜ける直前のもののため+1が必要
                    if(TmpJDIUtils.getCallStackDepth(mee.thread()) == depthBeforeCall + 1) result++;
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
        mExit.disable();
        return result;
    }




    @Override
    public String toString(){
        final String BG_GREEN = "\u001B[42m";
        final String RESET    = "\u001B[0m";

        LexicalPreservingPrinter.setup(stmt);
        extractExprArg(false, stmt, this.CallCountAfterTargetInLine, this.argIndex, this.calleeMethodName).getTokenRange().ifPresent(tokenRange -> {
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

    //Jackson シリアライズ用メソッド
    @JsonProperty("calleeMethodName")
    public String getCalleeMethodName() {
        return calleeMethodName.toString();
    }

    @JsonProperty("argIndex")
    public int getArgIndex(){
        return argIndex;
    }

    @JsonProperty("CallCountAfterTargetInLine")
    public int geCallCountAfterTargetInLine(){
        return CallCountAfterTargetInLine;
    }
}
