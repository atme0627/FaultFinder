# SearchSuspiciousReturnsArgumentStrategy のリファクタリング

## 背景

`JDISearchSuspiciousReturnsArgumentStrategy` は引数式内で呼ばれたメソッドの戻り値を収集する戦略。
Assignment/ReturnValue と同様の問題があったためリファクタリングを実施。

## 実施した改善

### 1. テストの作成

`SearchReturnsArgumentFixture` と `JDISearchSuspiciousReturnsArgumentStrategyTest` を作成。

**テストケース:**
| テスト名 | 説明 |
|---------|------|
| single_method_call | target(helper(10)) で helper の戻り値収集 |
| multiple_method_calls | target2(add(5) + multiply(3)) で両方の戻り値収集 |
| loop (1st/3rd) | ループ内で actualValue により実行を特定 |
| no_method_call | メソッド呼び出しがない場合は空リスト |
| same_method_twice_in_arg | twice(3) + twice(5) で同じメソッド2回 |
| same_method_nested | doubler(doubler(3)) でネスト呼び出し |
| nested_callee | target8(helper2(target8(3))) - 既知の問題 (@Disabled) |

### 2. execute() ベースへの変換

- `handleAtBreakPoint()` + ネストしたイベントループを排除
- `registerEventHandler()` で各イベントハンドラを登録
- 状態をフィールドに移動

### 3. エラーハンドリングの改善

- SLF4J Logger を導入
- `System.err.println` → `logger.warn()`
- `System.out.println` → `logger.debug()`
- メッセージを日本語に変更

### 4. StepIn/StepOut パターンへの変更

**変更前 (StepOver + caller メソッド名比較):**
```
BreakpointEvent
    └─ MethodExitRequest + StepOverRequest + MethodEntryRequest を作成
        ├─ MethodExitEvent → caller の StackFrame を取得し、メソッド名で直接呼び出しを判定
        ├─ MethodEntryEvent → callee メソッドかを確認、引数で検証
        └─ StepEvent → 行完了、リクエスト無効化
```

**変更後 (StepIn/StepOut + depth チェック):**
```
BreakpointEvent
    └─ MethodExitRequest + StepInRequest + MethodEntryRequest を作成、depthAtBreakpoint 記録
        ├─ StepEvent (StepIn完了) → メソッドに入った場合 callCount++、StepOut
        │   └─ StepEvent (StepOut完了) → 次の StepIn
        ├─ MethodExitEvent (depth == depthAtBreakpoint + 1) → 戻り値を収集
        └─ MethodEntryEvent → callee メソッドかを確認、引数で検証、disableRequests
```

### 改善点

- `IncompatibleThreadStateException` の処理が不要に（StackFrame 取得を廃止）
- depth チェックにより直接呼び出しの判定がシンプルに
- callCount を StepIn 完了時にインクリメント（直接呼び出しのみカウント）

## 技術的な議論

### callCount / targetCallCount の必要性

StepIn/StepOut パターンでは直接呼び出しを1つずつ辿るため、一見 callCount は不要に見える。
しかし、以下のようなケースで必要:

```java
target(helper1(10)) + target(helper2(10));  // 同一行に target が2回
```

2つ目の target の引数を調べたい場合、targetCallCount で区別する。

**今後の課題**: 現在の callCount は直接呼び出しメソッド全体をカウントしているが、
StringBuilder のように暗黙に呼ばれるメソッドがあると破綻する。
target メソッドの呼び出し回数のみカウントする方式に変更すべきだが、
SuspiciousArgument 自体の変更が必要なため、将来の課題とする。

### nested_callee の既知の問題

`target8(helper2(target8(3)))` のように callee メソッドがネストしている場合:

1. 内側の `target8(3)` の MethodEntryEvent で callee チェックが通る
2. 引数 `3 != actualValue 8` で検証失敗
3. `disableRequests()` で全リクエストが無効化される
4. 外側の `target8(8)` の検証が行われない

これは元のコードにも存在する問題。@Disabled テストとして記録。

### Assignment/ReturnValue との比較

| 観点 | Assignment | ReturnValue | Argument |
|------|------------|-------------|----------|
| 検証方法 | 代入先変数の値 | return 文全体の戻り値 | callee の引数の値 |
| 検証タイミング | StepEvent (行を離れた時) | MethodExitEvent (親メソッド終了時) | MethodEntryEvent (callee に入った時) |
| MethodEntryRequest | 不要 | 不要 | 必要 |
| callCount | 不要 | 不要 | 必要 (targetCallCount) |

## 次のステップ: SuspiciousArgument の設計変更

### 問題

nested_callee の問題と、暗黙メソッド呼び出し（文字列結合、オートボクシング等）による
callCount のずれを根本的に解決するため、SuspiciousArgument の設計変更が必要。

### 現状の問題点

1. **targetCallCount**: 「文中の全直接呼び出しの中で、引数式内の最初のメソッド呼び出しが何番目か」
   - 暗黙メソッド呼び出しがあるとカウントがずれる
2. **targetMethodNames**: 「収集すべきメソッドの名前リスト」
   - 同名メソッドがネストしている場合に区別できない（例: `target(dummy(dummy(20)), dummy(10))`）
3. **calleeMethodName**: callee メソッドの名前一致で判定
   - nested_callee（`target(helper2(target(3)))`）で内側の callee に誤マッチ

### 解決方針: 呼び出し順の歯抜けリスト + invokeCallCount

targetMethodNames（名前リスト）と targetCallCount（開始位置）を廃止し、
**収集すべき各メソッド呼び出しの callCount をリストで保持**する。

```java
// 変更前
public final int targetCallCount;          // 引数式の開始位置
final List<String> targetMethodNames;      // 収集すべきメソッド名

// 変更後
public final List<Integer> collectAtCounts; // 収集すべき callCount のリスト
public final int invokeCallCount;           // invoke メソッドの callCount
```

**例: `target(dummy(dummy(20)), dummy(10))`**
- 評価順: 1. dummy(20)内側, 2. dummy(helper1相当), 3. dummy(10), 4. target
- collectAtCounts = [2, 3] (収集すべき位置)
- invokeCallCount = 4 (invoke メソッドの位置)

1. callCount=1 → リストにない → 収集しない ✓
2. callCount=2 → リストにある → 収集 ✓
3. callCount=3 → リストにある → 収集 ✓
4. callCount=4 → invokeCallCount 一致 → 検証

### リネーム

- `calleeMethodName` → `invokeMethodName`（callee は分かりにくいため）

### depth チェックの限界

`parent(target(helper(10)))` のようにメソッド呼び出しがさらに別のメソッドの引数内にある場合、
target や helper の depth は depthAtBreakpoint + 1 ではなくなるため、depth チェックが破綻する。

現状（StepIn/StepOut 変換前の caller メソッド名比較）でも同じ問題がある。
バイトコードの `location.codeIndex()` を使えば位置ベースの判定が可能だが、
実装が複雑になるため現時点では見送り。

### callCount をどこでインクリメントするか

MethodEntryEvent と StepEvent の発火順序は JDI の仕様で保証されていない。
そのため callCount のインクリメントを MethodEntryEvent に移すと、
handleMethodExit の `callCount < targetCallCount` フィルタとの整合性が取れない可能性がある。

最終的な方針: MethodEntryEvent で depth チェック付きで callCount++ し、
同じ handleMethodEntry 内で `callCount == invokeCallCount` を判定する。
handleMethodExit では `collectAtCounts.contains(callCount)` で判定する。
これにより StepEvent との順序依存がなくなる。

### targetMethodNames vs collectAtCounts

targetMethodNames（名前リスト）では同名メソッドのネストを区別できない。

例: `target(dummy(dummy(20)), dummy(10))`
- targetMethodNames = ["dummy"] → 内側の dummy(20) も収集されてしまう
- collectAtCounts = [2, 3] → callCount で区別できる

### start (targetCallCount) は廃止可能か

start の目的は「引数式より前の MethodExit を収集しない」こと。
collectAtCounts で収集すべき位置を明示的に指定するため、start は不要になる。

dummy(10) + target(helper(10)) の場合:
- collectAtCounts = [2] (helper が2番目)
- callCount=1 (dummy) → リストにない → 収集しない
- callCount=2 (helper) → リストにある → 収集する

### 暗黙メソッド呼び出しの検出

callCount が invokeCallCount に達した時に invoke メソッド名と一致しなければ、
暗黙メソッド呼び出しの可能性がある → warn ログを出して打ち切り。

暗黙メソッド呼び出しが発生するケース:
- 文字列結合 `"a" + "b"` → StringBuilder 系（ただしコンパイル時定数畳み込みの場合あり）
- オートボクシング `Integer x = 10` → `Integer.valueOf(10)`
- アンボクシング `int x = integerObj` → `integerObj.intValue()`

疑わしい行の引数式内でこれらが発生する頻度は低いため、
検出してログ出力 + 打ち切りで十分。

### 影響範囲

- `SuspiciousArgument` - フィールド変更
- `JavaParserSuspiciousExpressionFactory` - collectAtCounts, invokeCallCount の算出
- `JDISearchSuspiciousReturnsArgumentStrategy` - 新しいフィールドを使った判定
- `JDITraceValueAtSuspiciousArgumentStrategy` - calleeMethodName の参照
- `SuspiciousExpressionFactory` - createArgument のシグネチャ
- `SuspiciousArgumentsSearcher` - calleeMethodName の参照
- `JDISuspiciousArgumentsSearcher` - calleeMethodName の参照
- テストクラス - 新しいフィールドに対応

### 議論の流れ

1. StepIn/StepOut パターン適用後、nested_callee (`target8(helper2(target8(3)))`) の @Disabled テストを解決しようとした
2. 最初の案: MethodEntry の depth チェック → 内側の target8 も depth D+1 なので区別不可
3. 次の案: 検証失敗時に disableRequests しない → ループで2回実行されるケースが弾けなくなる
4. callCount == targetCallCount で判定する案 → targetCallCount は callee の位置ではなく引数式の開始位置
5. MethodEntry で callCount をインクリメントする案 → StepEvent との発火順序が保証されない問題
6. callee の呼び出し回数のみカウントする案 → start フィルタの代替が必要
7. start フィルタ不要論 → targetMethodNames で十分では？ → 同名メソッドネストで破綻
8. collectAtCounts（歯抜けリスト）方式に到達 → 全問題を解決

### 次回開始時の手順

```bash
# テストを実行して現状確認（7 pass, 1 skip）
./gradlew test --tests "jisd.fl.infra.jdi.JDISearchSuspiciousReturnsArgumentStrategyTest" --no-daemon -q

# 変更対象ファイルの確認
# 1. SuspiciousArgument のフィールド変更
cat src/main/java/jisd/fl/core/entity/susp/SuspiciousArgument.java
# 2. 静的解析で collectAtCounts, invokeCallCount を算出
cat src/main/java/jisd/fl/infra/javaparser/JavaParserSuspiciousExpressionFactory.java
# 3. Strategy の判定ロジック変更
cat src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsArgumentStrategy.java
```

### 実装の順序（案）

1. SuspiciousArgument のフィールド変更 + リネーム (calleeMethodName → invokeMethodName)
2. JavaParserSuspiciousExpressionFactory で collectAtCounts, invokeCallCount を算出
3. JDISearchSuspiciousReturnsArgumentStrategy の判定ロジック変更
4. テスト修正 + nested_callee テストの @Disabled 解除
5. 他の参照箇所の修正 (JDITraceValueAtSuspiciousArgumentStrategy 等)
6. 全テスト実行で確認

## 実施結果: SuspiciousArgument 設計変更 (2026-01-31)

### 変更内容

上記「次のステップ」の方針に基づき、以下を実施:

1. **SuspiciousArgument のフィールド変更**
   - `calleeMethodName` → `invokeMethodName`
   - `targetCallCount` (int) → `invokeCallCount` (int)
   - `targetMethodNames` (List\<String\>) → `collectAtCounts` (List\<Integer\>)
   - `targetMethodNames()` アクセサを削除

2. **JavaParserSuspiciousExpressionFactory の変更**
   - `extractArgTargetMethodNames()` と `getCallCountBeforeTargetArgEval()` を廃止
   - `getEvalOrder()`: StatementEvalOrderVisitor で文中の全メソッド呼び出しを評価順に取得
   - `getCollectAtCounts()`: 引数式内の直接呼び出し（親 MethodCallExpr が式内にない）の評価順位置を返す
   - `getInvokeCallCount()`: invoke メソッド（引数式の親 MethodCallExpr）の評価順位置を返す

3. **JDISearchSuspiciousReturnsArgumentStrategy の変更**
   - `handleMethodEntry`: depth チェック → callCount++ → `callCount == invokeCallCount` で検証
   - `handleMethodExit`: `collectAtCounts.contains(callCount)` で収集判定
   - 暗黙メソッド呼び出し検出: invokeCallCount 到達時に invoke メソッド名が不一致なら warn ログ + 打ち切り
   - callCount のインクリメントを handleStepInCompleted から handleMethodEntry に移動

4. **nested_callee テスト (@Disabled 解除)**
   - `target8(helper2(target8(3)))`: collectAtCounts=[1,2], invokeCallCount=3
   - 内側 target8 は callCount=1（invokeCallCount=3 と不一致）→ スキップされる
   - 外側 target8 が callCount=3 で正しく検証される

### テスト結果

全8テスト通過（nested_callee 含む）:

| テスト名 | collectAtCounts | invokeCallCount | 結果 |
|---------|----------------|-----------------|------|
| single_method_call | [1] | 2 | PASS |
| multiple_method_calls | [1, 2] | 3 | PASS |
| loop (1st) | [1] | 2 | PASS |
| loop (3rd) | [1] | 2 | PASS |
| no_method_call | [] | 1 | PASS |
| same_method_twice_in_arg | [1, 2] | 3 | PASS |
| same_method_nested | [1, 2] | 3 | PASS |
| nested_callee | [1, 2] | 3 | PASS |

### 今後の課題

- **暗黙メソッド呼び出し**: 文字列結合、オートボクシング等で callCount がずれた場合は warn ログ + 打ち切り。テストでのカバーは未実施。
- **depth チェックの限界**: `parent(target(helper(10)))` のように呼び出しがネストしている場合、depth チェックが機能しない問題は未解決。

## コミット履歴

1. `test: JDISearchSuspiciousReturnsArgumentStrategy のテストを追加`
2. `refactor: JDISearchSuspiciousReturnsArgumentStrategy を execute() ベースに変換`
3. `refactor: JDISearchSuspiciousReturnsArgumentStrategy のエラーハンドリングを改善`
4. `refactor: JDISearchSuspiciousReturnsArgumentStrategy を StepIn/StepOut パターンに変更`
5. `docs: SuspiciousArgument 設計変更の議論と方針を記録`
6. `refactor: SuspiciousArgument を collectAtCounts/invokeCallCount 方式に redesign`

## 関連ファイル

- `src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsArgumentStrategy.java` - 本クラス
- `src/test/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsArgumentStrategyTest.java` - テスト
- `src/test/resources/fixtures/exec/src/main/java/jisd/fl/fixture/SearchReturnsArgumentFixture.java` - フィクスチャ
- `src/main/java/jisd/fl/core/entity/susp/SuspiciousArgument.java` - エンティティ（変更対象）
- `src/main/java/jisd/fl/infra/javaparser/JavaParserSuspiciousExpressionFactory.java` - 静的解析（変更対象）
- `src/main/java/jisd/fl/core/domain/port/SuspiciousExpressionFactory.java` - ファクトリインタフェース
- `src/main/java/jisd/fl/core/domain/port/SuspiciousArgumentsSearcher.java` - サーチャーインタフェース
- `src/main/java/jisd/fl/infra/jdi/JDISuspiciousArgumentsSearcher.java` - サーチャー実装
- `src/main/java/jisd/fl/infra/jdi/JDITraceValueAtSuspiciousArgumentStrategy.java` - トレース戦略
- `docs/design-notes/2026-01-28-search-suspicious-returns-assignment-refactoring.md` - Assignment の設計記録
- `docs/design-notes/2026-01-28-search-suspicious-returns-returnvalue-refactoring.md` - ReturnValue の設計記録