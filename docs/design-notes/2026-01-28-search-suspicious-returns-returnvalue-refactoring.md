# SearchSuspiciousReturnsReturnValueStrategy のリファクタリング

## 背景

`JDISearchSuspiciousReturnsReturnValueStrategy` は return 式内で呼ばれたメソッドの戻り値を収集する戦略。
Assignment Strategy と同様の問題があったためリファクタリングを実施:

1. `handleAtBreakPoint()` + ネストしたイベントループ構造
2. `MethodExitRequest` が全メソッド終了を処理（非効率）
3. エラーハンドリングが `System.out/err` を使用

## 実施した改善

### 1. execute() ベースへの変換

**変更前:**
```
handleAtBreakPoint() のイベントループ
    └─ BreakpointEvent
        └─ handler 内で独自のイベントループ
            ├─ MethodExitEvent を処理
            └─ StepEvent を処理
```

**変更後:**
```
execute() の統合イベントループ
    ├─ BreakpointEvent → handleBreakpoint()
    ├─ MethodExitEvent → handleMethodExit()
    └─ StepEvent → handleStep()
```

### 2. エラーハンドリングの改善

- SLF4J Logger を導入
- `System.out.println` → `logger.debug()`
- `System.err.println` → `logger.warn()`
- 英語メッセージを日本語に変更

### 3. StepIn/StepOut パターンへの変更

**変更前 (MethodExit + depth 比較):**
```
BreakpointEvent
    └─ MethodExitRequest + StepOutRequest を作成
        ├─ 全ての MethodExitEvent を受信
        ├─ depth == depthBeforeCall + 1 でフィルタ
        └─ StepEvent で行完了を検出、recentMethodExitEvent で検証
```

**変更後 (StepIn/StepOut + MethodExit):**
```
BreakpointEvent
    └─ MethodExitRequest + StepInRequest を作成
        ├─ StepEvent (StepIn完了) → 直接呼び出しを検出
        │   └─ StepOutRequest を作成
        │       └─ StepEvent (StepOut完了) → 次の StepIn
        ├─ MethodExitEvent (depth == depthAtBreakpoint + 1) → 戻り値を収集
        └─ MethodExitEvent (depth == depthAtBreakpoint) → 親メソッドの終了、検証
```

## 技術的な議論

### Assignment Strategy との違い

| 観点 | Assignment | ReturnValue |
|------|------------|-------------|
| 検証方法 | 代入先変数の値で検証 | return 文全体の戻り値で検証 |
| 親メソッドの MethodExitEvent | 不要 | 必要 |
| MethodExitRequest の有効期間 | 呼び出されたメソッドの終了まで | 行の処理が完了するまで |

### StepOut 完了時の位置

ReturnValue の対象は return 文なので、StepOut 完了時は必ず return 文の行にいる:

```java
return helper(10);  // 行 29
```

1. BreakpointEvent (行 29)
2. StepIn → helper に入る
3. MethodExitEvent (helper の戻り値を収集)
4. StepOut 完了 → **行 29 に戻る**（return 文の評価はまだ終わっていない）

helper から StepOut した直後は、まだ return 文の行にいる。return 文が完了してメソッドが終了するのはその後。

**結論**: `handleStepOutCompleted` で行を離れることはないので、その分岐は不要。

### 親メソッドの終了検出

return 文全体の戻り値は親メソッドの MethodExitEvent で取得する必要がある:

- `depth == depthAtBreakpoint + 1`: 直接呼び出したメソッドの終了 → 戻り値を収集
- `depth == depthAtBreakpoint`: 親メソッド（return 文を含むメソッド）の終了 → 検証

### MethodExitRequest のフィルタリング

JDI の MethodExitRequest でできるフィルタリングは限定的:
- `addClassFilter()` - クラス名でフィルタ
- `addThreadFilter()` - スレッドでフィルタ
- `addInstanceFilter()` - インスタンスでフィルタ

特定のメソッド名やスタックの深さでフィルタする機能はない。
`handleMethodExit` 内でスタックの深さを確認してフィルタする方法が一般的。

## テスト結果

**テストケース:**
| テスト名 | 状態 | 説明 |
|---------|------|------|
| single_method_call_collects_return_value | ✅ Pass | 単一メソッド呼び出しの戻り値収集 |
| multiple_method_calls_collects_all_return_values | ✅ Pass | 複数メソッド呼び出しの戻り値収集 |
| nested_method_call_collects_all_return_values | ✅ Pass | ネストした呼び出しで両方の戻り値を収集 |
| loop_identifies_first_execution | ✅ Pass | ループ内で actualValue により実行を特定 |
| loop_identifies_third_execution | ✅ Pass | ループ内で3回目の実行を特定 |
| no_method_call_returns_empty | ✅ Pass | メソッド呼び出しがない場合は空リスト |

## コミット履歴

1. `test: JDISearchSuspiciousReturnsReturnValueStrategy のテストを追加`
2. `refactor: JDISearchSuspiciousReturnsReturnValueStrategy を execute() ベースに変換`
3. `refactor: JDISearchSuspiciousReturnsReturnValueStrategy のエラーハンドリングを改善`
4. `refactor: JDISearchSuspiciousReturnsReturnValueStrategy を StepIn/StepOut パターンに変更`
5. `fix: JDISearchSuspiciousReturnsReturnValueStrategy の StepIn/StepOut パターンを修正`

## 関連ファイル

- `src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsReturnValueStrategy.java` - 本クラス
- `src/test/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsReturnValueStrategyTest.java` - テスト
- `src/test/resources/fixtures/exec/src/main/java/jisd/fl/fixture/SearchReturnsReturnValueFixture.java` - フィクスチャ
- `docs/design-notes/2026-01-28-search-suspicious-returns-assignment-refactoring.md` - Assignment Strategy のリファクタリング記録