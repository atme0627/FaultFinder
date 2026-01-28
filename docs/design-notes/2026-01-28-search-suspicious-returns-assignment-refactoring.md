# SearchSuspiciousReturnsAssignmentStrategy のリファクタリング

## 背景

`JDISearchSuspiciousReturnsAssignmentStrategy` は代入式の右辺で呼ばれたメソッドの戻り値を収集する戦略。
以下の問題があったためリファクタリングを実施:

1. `handleAtBreakPoint()` + ネストしたイベントループ構造
2. `MethodExitRequest` が全メソッド終了を処理（非効率）
3. エラーハンドリングが `System.out/err` を使用
4. `validateIsTargetExecution` が長大で分割されていない

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
- 曖昧な例外メッセージ (`"Something is wrong."`) を具体的な日本語メッセージに変更
- 例外に原因 (cause) を含めるように変更

### 3. validateIsTargetExecution の分割

**変更前:** 1つの大きなメソッド (50行以上)

**変更後:** 3つのメソッドに分割
- `validateIsTargetExecution()` - バリデーションと分岐のみ
- `getFieldValue()` - フィールド変数の値取得
- `getLocalVariableValue()` - ローカル変数の値取得

### 4. StepIn/StepOut パターンへの変更

**変更前 (MethodExit + depth 比較):**
```
BreakpointEvent
    └─ MethodExitRequest + StepOverRequest を作成
        ├─ 全ての MethodExitEvent を受信
        ├─ depth == depthBeforeCall + 1 でフィルタ
        └─ StepEvent で行完了を検出
```

問題: 全メソッド終了イベントを処理するため非効率

**変更後 (StepIn/StepOut):**
```
BreakpointEvent
    └─ StepInRequest を作成
        ├─ StepEvent (StepIn完了) → 直接呼び出しを検出
        │   └─ MethodExitRequest + StepOutRequest を作成
        │       ├─ MethodExitEvent → 戻り値を収集、Request を無効化
        │       └─ StepEvent (StepOut完了) → 次の StepIn or 行完了
        └─ (メソッドに入らなかった場合) → 行完了を検証
```

効果:
- 直接呼び出しを検出した時のみ MethodExitRequest を有効化
- MethodExitRequest の有効期間を最小化
- 処理するイベント数を大幅に削減

## テスト結果

**作成したテスト:**
- `JDISearchSuspiciousReturnsAssignmentStrategyTest` (7テスト)
- `SearchReturnsAssignmentFixture` (テスト用フィクスチャ)

**テストケース:**
| テスト名 | 状態 | 説明 |
|---------|------|------|
| single_method_call_collects_return_value | ✅ Pass | 単一メソッド呼び出しの戻り値収集 |
| multiple_method_calls_collects_all_return_values | ✅ Pass | 複数メソッド呼び出しの戻り値収集 |
| nested_method_call_collects_all_return_values | ✅ Pass | ネストした呼び出しで両方の戻り値を収集 |
| loop_identifies_first_execution | ✅ Pass | ループ内で actualValue により実行を特定 |
| loop_identifies_third_execution | ✅ Pass | ループ内で3回目の実行を特定 |
| no_method_call_returns_empty | ✅ Pass | メソッド呼び出しがない場合は空リスト |
| chained_method_calls_collects_return_values | ⏸ Disabled | factory が内部クラスに未対応 |

## 技術的な議論

### ネストした呼び出しの扱い

`x = outer(inner(5))` のような場合:
- **当初の想定**: outer のみ収集（inner は「間接呼び出し」）
- **決定**: inner と outer の両方を収集し、フィルタリングは呼び出し側で行う

理由:
- どちらも行から直接呼ばれている（評価順の違いのみ）
- 呼び出し側の用途に応じてフィルタできる方が柔軟

### StepIn/StepOut vs MethodExit + depth

| 観点 | MethodExit + depth | StepIn/StepOut |
|------|-------------------|----------------|
| イベント数 | 全メソッド終了を処理 | 直接呼び出しのみ |
| 実装複雑度 | シンプル | やや複雑（状態管理） |
| パフォーマンス | 低（深いコールスタックで悪化） | 高（必要なイベントのみ） |
| 正確性 | 深さ比較で判定 | 呼び出し元位置で判定 |

## 今後の課題

### 内部クラス対応

`chained_method_calls` テストが失敗する原因:
```
SuspiciousReturnValue の作成に失敗: Cannot extract expression from [{
    return value;
}]. (method=...SearchReturnsAssignmentFixture$ChainHelper#getValue(), line=136)
```

`JavaParserSuspiciousExpressionFactory.createReturnValue()` が `Outer$Inner` 形式の
FQCN を持つ内部クラスのメソッドからソースコードを抽出できない。

**対応箇所**: `JavaParserUtils` または `JavaParserSuspiciousExpressionFactory`

## コミット履歴

1. `test: JDISearchSuspiciousReturnsAssignmentStrategy のテストを追加`
2. `refactor: JDISearchSuspiciousReturnsAssignmentStrategy を execute() ベースに変換`
3. `refactor: JDISearchSuspiciousReturnsAssignmentStrategy のエラーハンドリングを改善`
4. `refactor: validateIsTargetExecution をフィールド/ローカル変数処理に分割`
5. `refactor: JDISearchSuspiciousReturnsAssignmentStrategy を StepIn/StepOut パターンに変更`
6. `test: nested_method_call テストを全戻り値収集に変更`

## 関連ファイル

- `src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsAssignmentStrategy.java` - 本クラス
- `src/test/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsAssignmentStrategyTest.java` - テスト
- `src/test/resources/fixtures/exec/src/main/java/jisd/fl/fixture/SearchReturnsAssignmentFixture.java` - フィクスチャ