# SearchSuspiciousReturnsArgumentStrategy リファクタリング準備

## 現状分析

`JDISearchSuspiciousReturnsArgumentStrategy` は引数式内で呼ばれたメソッドの戻り値を収集する戦略。
Assignment/ReturnValue と同様の問題がある。

### 現在の構造

```java
public List<SuspiciousExpression> search(SuspiciousExpression suspExpr) {
    // ...
    EnhancedDebugger.BreakpointHandler handler = (vm, bpe) -> {
        // MethodExitRequest, StepOverRequest, MethodEntryRequest を作成
        vm.resume();

        while (!done) {
            EventSet es = vm.eventQueue().remove();
            for (Event ev : es) {
                if (ev instanceof MethodEntryEvent) { ... }
                if (ev instanceof MethodExitEvent) { ... }
                if (ev instanceof StepEvent) { ... }
            }
            if (doResume) vm.resume();
        }
    };

    debugger.handleAtBreakPoint(..., handler);
}
```

### 改善すべき点

1. **handleAtBreakPoint() → execute() ベース**
   - ネストしたイベントループを排除
   - `registerEventHandler()` でハンドラを登録

2. **エラーハンドリング**
   - `System.out.println` → `logger.debug()`
   - `System.err.println` → `logger.warn()`
   - 英語メッセージ → 日本語メッセージ

3. **StepIn/StepOut パターン** (検討が必要)
   - Assignment/ReturnValue では StepIn/StepOut パターンで効率化
   - Argument では MethodEntryEvent を使用して検証するため、異なるアプローチが必要かもしれない

## Assignment/ReturnValue との違い

| 観点 | Assignment | ReturnValue | Argument |
|------|------------|-------------|----------|
| 検証方法 | 代入先変数の値 | return 文全体の戻り値 | calleeメソッドへの引数の値 |
| 検証タイミング | StepEvent (行を離れた時) | MethodExitEvent (親メソッド終了時) | MethodEntryEvent (calleeメソッドに入った時) |
| フィルタ | 直接呼び出しのみ | 直接呼び出しのみ | targetMethodName リスト + targetCallCount |

## Argument 特有の複雑さ

### コメント (26-29行目)
```
//引数のindexを指定してその引数の評価の直前でsuspendするのは激ムズなのでやらない
//引数を区別せず、引数の評価の際に呼ばれたすべてのメソッドについて情報を取得し
//Expressionを静的解析してexpressionで直接呼ばれてるメソッドのみに絞る
//ex.) expressionがx.f(y.g())の時、fのみとる。y.g()はfの探索の後行われるはず
```

### MethodEntryEvent での検証
- calleeメソッドに入った時に引数の値をチェック
- コンストラクタの場合は `declaringType().name()` で比較
- 通常メソッドの場合は `name()` で比較

### targetCallCount
- 引数内の最初のmethodCallが文中で何番目か
- この回数に達するまで MethodExitEvent をスキップ

## 推奨するリファクタリング手順

### Step 1: テストの作成
- `JDISearchSuspiciousReturnsArgumentStrategyTest` を作成
- `SearchReturnsArgumentFixture` を作成
- 既存の動作を確認

### Step 2: execute() ベースへの変換
- `handleAtBreakPoint()` → `execute()` + `registerEventHandler()`
- ネストしたイベントループを排除
- 状態をフィールドに移動

### Step 3: エラーハンドリングの改善
- Logger 導入
- 日本語メッセージに変更

### Step 4: StepIn/StepOut パターン (検討)
- MethodEntryEvent を使用するため、単純な StepIn/StepOut では対応できない可能性
- 現在のアプローチを維持するか、別のアプローチを検討

## 関連ファイル

- `src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsArgumentStrategy.java` - 対象クラス
- `src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsAssignmentStrategy.java` - 参考 (Assignment)
- `src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsReturnValueStrategy.java` - 参考 (ReturnValue)
- `docs/design-notes/2026-01-28-search-suspicious-returns-assignment-refactoring.md` - Assignment の設計記録
- `docs/design-notes/2026-01-28-search-suspicious-returns-returnvalue-refactoring.md` - ReturnValue の設計記録

## 次回開始時のコマンド

```bash
# テストを実行して現状確認
./gradlew test --tests "jisd.fl.infra.jdi.*" --no-daemon -q

# Argument Strategy のコードを確認
cat src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsArgumentStrategy.java
```