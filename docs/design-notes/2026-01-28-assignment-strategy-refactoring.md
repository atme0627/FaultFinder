# Assignment 戦略の execute() ベースへのリファクタリング

## 背景

`JDITraceValueAtSuspiciousAssignmentStrategy` のイベントループ処理を改善するため、`EnhancedDebugger.execute()` の統合イベントループを使用するようにリファクタリングする。

## 現状の問題

現在の Assignment 戦略は `handleAtBreakPoint()` を使用し、さらにその内部で独自のイベントループを持っている（ネストしたイベントループ）。

```
handleAtBreakPoint() のイベントループ
    └─ BreakpointEvent
        └─ handler 内で独自のイベントループ  ← 問題
            └─ StepEvent を待つ
```

問題点:
- イベント処理の一貫性がない
- 他のイベント（ClassPrepare等）が内部ループで無視される
- タイムアウト処理がない
- 例外発生時のクリーンアップが不完全

## 完了した作業

### 1. JDIEventHandler の型安全化

**変更前:**
```java
public interface JDIEventHandler<T extends Event> {
    void handle(VirtualMachine vm, Event event);
}
```

**変更後:**
```java
public interface JDIEventHandler<T extends Event> {
    void handle(VirtualMachine vm, T event);
}
```

### 2. EnhancedDebugger.execute() への終了条件追加

**追加したオーバーロード:**
```java
public void execute(Supplier<Boolean> shouldStop) {
    // ...
    while((eventSet = queue.remove()) != null) {
        // 終了条件チェック
        if (shouldStop != null && shouldStop.get()) {
            break;
        }
        // ...
    }
}

// 後方互換
public void execute() {
    execute(null);
}
```

### 3. import 追加
- `java.util.function.Supplier` を EnhancedDebugger に追加

## 次のステップ（未着手）

### 1. テストケースの作成

Assignment 戦略のリファクタリング前に、現状の動作を保証するテストを作成する。

テスト観点:
- 単純な代入文 (`x = 1`)
- 式を含む代入 (`x = a + b`)
- メソッド呼び出しを含む代入 (`x = compute(y)`)
- ローカル変数への代入
- フィールドへの代入
- 複数回実行される代入（ループ内）

### 2. Assignment 戦略の書き換え

**目指す構造:**
```java
public class JDITraceValueAtSuspiciousAssignmentStrategy implements TraceValueAtSuspiciousExpressionStrategy {

    // 状態フィールド
    private List<TracedValue> result;
    private List<TracedValue> resultCandidate;
    private SuspiciousAssignment currentTarget;
    private StepRequest activeStepRequest;
    private boolean done;

    public List<TracedValue> traceAllValuesAtSuspExpr(SuspiciousExpression suspExpr) {
        // 初期化
        SuspiciousAssignment suspAssign = (SuspiciousAssignment) suspExpr;
        this.result = new ArrayList<>();
        this.resultCandidate = null;
        this.currentTarget = suspAssign;
        this.activeStepRequest = null;
        this.done = false;

        JUnitDebugger debugger = new JUnitDebugger(suspAssign.failedTest);

        // ハンドラ登録
        debugger.registerEventHandler(BreakpointEvent.class, this::handleBreakpoint);
        debugger.registerEventHandler(StepEvent.class, this::handleStep);

        // ブレークポイント設定
        debugger.setBreakpoints(
            suspAssign.locateMethod.fullyQualifiedClassName(),
            List.of(suspAssign.locateLine)
        );

        // 実行（終了条件付き）
        debugger.execute(() -> done);

        return result;
    }

    private void handleBreakpoint(VirtualMachine vm, BreakpointEvent bpe) {
        if (done) return;

        // 周辺変数を観測
        try {
            StackFrame frame = bpe.thread().frame(0);
            resultCandidate = JDIUtils.watchAllVariablesInLine(frame, currentTarget.locateLine);
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException(e);
        }

        // StepRequest 作成
        EventRequestManager manager = vm.eventRequestManager();
        activeStepRequest = EnhancedDebugger.createStepOverRequest(manager, bpe.thread());
    }

    private void handleStep(VirtualMachine vm, StepEvent se) {
        if (done) return;

        // 検証
        if (validateIsTargetExecution(se, currentTarget.assignTarget)) {
            result.addAll(resultCandidate);
            done = true;
        }

        // StepRequest 無効化
        if (activeStepRequest != null) {
            activeStepRequest.disable();
            activeStepRequest = null;
        }

        // 候補をクリア（次のブレークポイント用）
        resultCandidate = null;
    }

    // validateIsTargetExecution は現状のまま
}
```

### 3. ReturnValue / Argument 戦略の対応

Assignment 戦略が完了後、同様のリファクタリングを行う。

## 関連ファイル

- `src/main/java/jisd/fl/infra/jdi/JDIEventHandler.java` - 変更済み
- `src/main/java/jisd/fl/infra/jdi/EnhancedDebugger.java` - 変更済み
- `src/main/java/jisd/fl/infra/jdi/JDITraceValueAtSuspiciousAssignmentStrategy.java` - 次に変更予定
- `src/main/java/jisd/fl/core/domain/internal/ValueAtSuspiciousExpressionTracer.java` - Facade クラス

## 議論で決まったこと

1. **状態管理**: フィールドで管理（コンテキストオブジェクトではなく）
2. **終了条件**: `Supplier<Boolean>` で制御
3. **他戦略の対応**: Assignment 完了後に実施
4. **観測タイミング**: 代入**前**の変数値を観測するのが正しい（設計意図通り）
5. **複数回実行時の挙動**: エッジケースのため優先度低、TODO に残す

## 参照ドキュメント

- `docs/design-notes/TODO-value-at-suspicious-expression-tracer.md` - 今後の課題一覧
