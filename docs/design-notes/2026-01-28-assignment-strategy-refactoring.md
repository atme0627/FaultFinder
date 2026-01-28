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

## 完了した追加作業（2026-01-28）

### 4. テストケースの作成 ✅

Assignment 戦略のリファクタリング前に、現状の動作を保証するテストを作成。

**作成したファイル:**
- `src/test/java/jisd/fl/infra/jdi/JDITraceValueAtSuspiciousAssignmentStrategyTest.java`
- `src/test/resources/fixtures/exec/src/main/java/jisd/fl/fixture/AssignmentStrategyFixture.java`

**テストケース（10件）:**
- ループ内の代入（actualValue による実行特定）
- 条件分岐による代入パス
- メソッド呼び出しを含む代入
- フィールドへの複数回代入

### 5. Assignment 戦略の execute() ベースへの移行 ✅

段階的にリファクタリングを実施:

1. **状態をフィールドに抽出**: result, resultCandidate, currentTarget, activeStepRequest
2. **BreakpointHandler を別メソッドに抽出**: handleBreakpoint()
3. **StepEvent 処理を別メソッドに抽出**: handleStep()
4. **registerEventHandler + execute() に切り替え**: handleAtBreakPoint() を廃止
5. **内部イベントループを削除**: 統合イベントループを使用

**最終的な構造:**
```
execute() の統合イベントループ
    ├─ BreakpointEvent → handleBreakpoint()
    └─ StepEvent → handleStep()
```

### 6. validateIsTargetExecution のリファクタリング ✅

- メソッド分割: getAssignedValue, getFieldValue, getLocalVariableValue
- 値取得を `JDIUtils.getValueString()` に統一
- frame 取得の重複を削除
- エラーメッセージを具体的に改善（原因チェーン追加）
- Javadoc を追加

## 次のステップ（未着手）

### ReturnValue / Argument 戦略の対応

Assignment 戦略と同様のリファクタリングを行う:
- execute() ベースへの移行
- ハンドラの分離
- validateIsTargetExecution の統一

## 関連ファイル

- `src/main/java/jisd/fl/infra/jdi/JDIEventHandler.java` - 変更済み
- `src/main/java/jisd/fl/infra/jdi/EnhancedDebugger.java` - 変更済み
- `src/main/java/jisd/fl/infra/jdi/JDITraceValueAtSuspiciousAssignmentStrategy.java` - **リファクタリング完了**
- `src/main/java/jisd/fl/core/domain/internal/ValueAtSuspiciousExpressionTracer.java` - Facade クラス

## 議論で決まったこと

1. **状態管理**: フィールドで管理（コンテキストオブジェクトではなく）
2. **終了条件**: `Supplier<Boolean>` で制御
3. **他戦略の対応**: Assignment 完了後に実施
4. **観測タイミング**: 代入**前**の変数値を観測するのが正しい（設計意図通り）
5. **複数回実行時の挙動**: エッジケースのため優先度低、TODO に残す

## 参照ドキュメント

- `docs/design-notes/TODO-value-at-suspicious-expression-tracer.md` - 今後の課題一覧
