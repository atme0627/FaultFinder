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

## コミット履歴

1. `test: JDISearchSuspiciousReturnsArgumentStrategy のテストを追加`
2. `refactor: JDISearchSuspiciousReturnsArgumentStrategy を execute() ベースに変換`
3. `refactor: JDISearchSuspiciousReturnsArgumentStrategy のエラーハンドリングを改善`
4. `refactor: JDISearchSuspiciousReturnsArgumentStrategy を StepIn/StepOut パターンに変更`

## 関連ファイル

- `src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsArgumentStrategy.java` - 本クラス
- `src/test/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsArgumentStrategyTest.java` - テスト
- `src/test/resources/fixtures/exec/src/main/java/jisd/fl/fixture/SearchReturnsArgumentFixture.java` - フィクスチャ
- `docs/design-notes/2026-01-28-search-suspicious-returns-assignment-refactoring.md` - Assignment の設計記録
- `docs/design-notes/2026-01-28-search-suspicious-returns-returnvalue-refactoring.md` - ReturnValue の設計記録