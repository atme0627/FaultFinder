# Probe 実装修正計画の完了報告

## 概要

ProbeTest で確認された 5 つの問題について、すべての対応が完了した。

## 問題一覧と対応結果

### 問題 1: ネストしたメソッド呼び出しの階層構造 ✅ 完了

**コミット**: 0828d62
**詳細**: `docs/design-notes/2026-02-04-nested-method-call-hierarchy.md`

**修正内容**:
- `SuspiciousAssignment` / `SuspiciousReturnValue` に `targetReturnCallPositions` フィールド追加
- `JDISearchSuspiciousReturnsAssignmentStrategy` / `ReturnValueStrategy` に `MethodEntryEvent` + `callCount` フィルタ追加

---

### 問題 2, 3: ループ内の変数追跡 ✅ 基本完了（残課題あり）

**コミット**: ddb7e6d
**詳細**: `docs/design-notes/2026-02-05-loop-variable-tracking.md`

**修正内容**:
- `getStatementByLine` から BlockStmt を完全除外
- ForStmt ハンドラを init 式に変更（JDI の earliest code index BP 制約に対応）

**残課題**: `x = x + i` の右辺 `x` が追跡されない
- 詳細: `docs/issues/2026-02-05-loop-x-tracking-investigation.md`

---

### 問題 4: 複数行にまたがる式の解析で原因行が見つからない ✅ 完了

**コミット**: 190451b
**詳細**: `docs/design-notes/2026-02-06-multiline-expression-fix.md`

**修正内容**:
- `ValueChangingLineFinder.collectMutationRanges()` で `VariableDeclarator` の全範囲を使用
- E2E テスト（scenario5）追加

---

### 問題 5: 内部クラス（static nested class）のメソッド追跡 ✅ 検証完了（問題なし）

**検証日**: 2026-02-06

**当初の報告**:
- 内部クラスのメソッド呼び出しで `[Probe For STATEMENT] Cause line not found.` エラーが発生するとされていた

**検証結果**:
E2E テスト `scenario6_inner_class_method` を作成して検証した結果、**問題なく動作することが確認された**。

```java
static class Calculator {
    int add(int a, int b) {
        return a + b;
    }
}

@Test
void scenario6_inner_class_method() {
    Calculator calc = new Calculator();
    int x = calc.add(10, 20);  // ← 正常に追跡できた
    assertEquals(999, x);
}
```

**確認事項**:
1. JDI でのクラス名解決: `jisd.fixture.ProbeFixture$Calculator` で正しく解決
2. メソッドエントリ/イグジット: `MethodEntryEventImpl at jisd.fixture.ProbeFixture$Calculator:107 method=add` で正常に検出
3. return 文の追跡: `return a + b` が `SuspiciousReturnValue` として正しく追跡
4. 引数の追跡: `a=10`, `b=20` が `SuspiciousArgument` として正しく追跡

**結論**:
当初の問題報告は、別の原因（例: ベンチマーク作成時の一時的な問題、または別パッケージのクラス参照の問題）によるものだった可能性がある。現在の実装では内部クラスのメソッド追跡は正常に動作する。

---

## 最終ステータス

| 問題 | 状態 | コミット |
|------|------|----------|
| 問題 1: ネスト追跡 | ✅ 完了 | 0828d62 |
| 問題 2, 3: ループ追跡 | ✅ 基本完了 | ddb7e6d |
| 問題 4: マルチライン式 | ✅ 完了 | 190451b |
| 問題 5: 内部クラス | ✅ 問題なし | - |

## 残課題

- ループ内の右辺変数追跡（`x = x + i` の右辺 `x`）
  - 詳細: `docs/issues/2026-02-05-loop-x-tracking-investigation.md`

## 関連ファイル

- `src/test/java/jisd/fl/usecase/ProbeTest.java`: シナリオ 1〜6 の E2E テスト
- `src/test/resources/fixtures/exec/src/main/java/jisd/fixture/ProbeFixture.java`: テストフィクスチャ
