# Argument 戦略リファクタリング

## 背景

JDITraceValueAtSuspiciousArgumentStrategy のリファクタリングを実施。
以下の課題があった：

1. **ネストしたイベントループ**: `handleAtBreakPoint` 内でネストしたイベントループを使用しており、他の戦略（Assignment, ReturnValue）と一貫性がなかった
2. **MethodEntryRequest の非効率性**: 全メソッド呼び出しでイベントが発火し、間接的に呼ばれるメソッド（ライブラリ、ヘルパー等）全てで `method.name()` チェックが走るため、深いコールスタックがあると非常に遅い
3. **同一行の複数メソッド呼び出し**: `helper(a) + helper(b)` のような同一行の2回目の呼び出しを検出できなかった

## 実施した改善

### 1. execute() ベースへの変換

```
Before: handleAtBreakPoint() + ネストしたイベントループ
After:  registerEventHandler() + execute()
```

- 状態をフィールドに抽出（result, resultCandidate, currentTarget, activeStepRequest, steppingOut）
- BreakpointEvent と StepEvent のハンドラを登録
- execute() の統一イベントループを使用

### 2. MethodEntry → StepIn/StepOut パターンへの変更

```
Before: BreakpointEvent → MethodEntryRequest → 全メソッドをフィルタリング
After:  BreakpointEvent → StepIn → チェック → StepOut → StepIn → ...
```

**フロー**:
1. BreakpointEvent で対象行に到達、変数を観測
2. StepIn で直接呼び出されたメソッドに入る
3. 呼び出し元の位置（メソッド+行番号）を確認
4. 対象メソッドなら引数をチェック
   - 一致 → 完了
   - 不一致 → StepOut して次のメソッド呼び出しを探す
5. 対象メソッドでない → StepOut して次のメソッド呼び出しを探す

### 3. メソッド分割

```
handleStep
├─ handleStepOutCompleted()  // 呼び出し元に戻った後
└─ handleStepInCompleted()   // メソッドに入った後
    └─ isTargetMethod()      // 対象メソッド判定
```

ヘルパーメソッド:
- `isCalledFromTargetLocation()`: 呼び出し元の位置（クラス名+メソッド名+行番号）を確認
- `isTargetMethod()`: 対象の callee メソッドか判定
- `validateArgumentValue()`: 引数の値を検証

### 4. エラーハンドリング改善

- ロガーを追加
- `RuntimeException` → `IllegalStateException` に変更
- 日本語のエラーメッセージで対象の情報を含める

## 技術的な議論

### StepRequest の DuplicateRequestException 問題

**問題**: JDI は同一スレッドに複数の StepRequest を許可しない。`disable()` ではリクエストは無効化されるが EventRequestManager からは削除されない。

**解決策**: `disable()` ではなく `deleteEventRequest()` を使用して、新しいリクエストを作成する前に古いリクエストを完全に削除する。

```java
// 現在の StepRequest を削除（同一スレッドに複数の StepRequest は作成できない）
manager.deleteEventRequest(activeStepRequest);
activeStepRequest = null;
```

### 呼び出し元の位置確認

**問題**: StepIn 後の行番号だけでは、たまたま一致する可能性がある。

**解決策**: コールスタックから呼び出し元の情報を取得し、メソッド名と行番号の両方を確認。

```java
private boolean isCalledFromTargetLocation(ThreadReference thread) {
    StackFrame callerFrame = thread.frame(1);  // frame(0)は現在、frame(1)は呼び出し元
    Location callerLocation = callerFrame.location();

    String callerClassName = callerLocation.declaringType().name();
    String callerMethodName = callerLocation.method().name();
    int callerLine = callerLocation.lineNumber();

    return callerClassName.equals(currentTarget.locateMethod.fullyQualifiedClassName())
            && callerMethodName.equals(currentTarget.locateMethod.shortMethodName())
            && callerLine == currentTarget.locateLine;
}
```

### StepIn でメソッドに入れない場合

**問題**: 行に呼び出すメソッドがない場合、StepIn は STEP_OVER と同じ動作になり次の行に進む。

**解決策**: `isCalledFromTargetLocation()` で `frameCount() < 2` の場合は false を返し、次の BreakpointEvent を待つ。

## テスト結果

全 10 テストケースが成功：
- simple_argument_observes_variables
- multiple_arguments_observes_variables
- loop_calling_method_identifies_first_execution
- loop_calling_method_identifies_third_execution
- conditional_argument_true_path
- conditional_argument_false_path
- expression_argument_observes_variables
- constructor_argument_observes_variables
- multiple_calls_same_line_first
- multiple_calls_same_line_second

## 今後の課題

1. **JDIUtils.validateIsTargetExecutionArg の扱い**: このメソッドは Argument 関連の2クラス（JDITraceValueAtSuspiciousArgumentStrategy, JDISearchSuspiciousReturnsArgumentStrategy）で使用されている。共通のユーティリティとして残すか、各クラスにインラインするか検討が必要。

2. **TraceValue の使い方の統一**: TargetVariableTracer と JDITraceValueAtSuspicious*Strategy で TraceValue の使い方が異なる。将来的に統一を検討。

## まとめ

- **パフォーマンス改善**: MethodEntryRequest から StepIn/StepOut に変更し、間接呼び出しのフィルタリングが不要に
- **同一行対応**: 同一行の複数メソッド呼び出しを正しく検出可能に
- **コード品質**: メソッド分割、エラーハンドリング改善、他の戦略との一貫性向上
- **学び**: JDI の StepRequest は disable() だけでなく deleteEventRequest() で削除が必要