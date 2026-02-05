# Probe 実装修正計画

## 概要

ProbeTest で確認された3つの問題について、実装を修正する計画です。

## 修正すべき問題

### 問題 1: ネストしたメソッド呼び出しの階層構造 ✅ 完了

**コミット**: 0828d62
**設計記録**: `docs/design-notes/2026-02-04-nested-method-call-hierarchy.md`

**修正内容**:
- `SuspiciousAssignment` / `SuspiciousReturnValue` に `targetReturnCallPositions` フィールド追加
- `SuspiciousArgument` の `collectAtCounts` を `targetReturnCallPositions` にリネーム
- `JDISearchSuspiciousReturnsAssignmentStrategy` / `ReturnValueStrategy` に `MethodEntryEvent` + `callCount` フィルタ追加
- ファクトリの `createArgument` で AST クローン済みノードを使用していたバグも修正

---

### 問題 2, 3: ループ内の変数追跡 ✅ 基本完了（残課題あり）

**コミット**: ddb7e6d
**設計記録**: `docs/design-notes/2026-02-05-loop-variable-tracking.md`

**コード**:
```java
for (int i = 0; i < 3; i++) {
    x = x + i;  // x は 0 → 1 → 3 と変化
}
```

**修正内容**:
- `getStatementByLine` から BlockStmt を完全除外（不正な末端ノードの解消）
- ForStmt ハンドラを init 式に変更（JDI の earliest code index BP 制約に対応）
- `scenario4_loop_variable_update` テストを `assertTreeEquals` による厳密検証に変更

**修正後のツリー**:
```
ASSIGN(x=3, line:71) x = x + i;
└── ASSIGN(x=1, line:71) x = x + i;
    └── ASSIGN(x=0, line:71) x = x + i;
        └── ASSIGN(i=0, line:70) for-loop init (leaf)
```

**残課題**: `x = x + i` の右辺 `x` が追跡されない
- 調査完了、原因は2つの問題の重畳（`investigatedVariables` の dedup + `valueChangedToActualLine` の最新優先）
- 詳細: `docs/issues/2026-02-05-loop-x-tracking-investigation.md`

---

## 次の作業

問題 4, 5 の修正に進む。

---

## 追加の問題（2026-02-03 ベンチマーク作成時に発見）

### 問題 4: 複数行にまたがる式の解析で原因行が見つからない

**コード**:
```java
int x = d1(d2(d3(d4(d5(
        d6(d7(d8(d9(d10(1))))))))));  // 2行にまたがる式
```

**現在の動作**: `[Probe For STATEMENT] Cause line not found.` エラー

**期待する動作**: 最初の行を原因行として特定できる

**回避策**: 1行に収めると動作する

**修正方針**:
- `ValueChangingLineFinder` または JavaParser の解析で、複数行にまたがる式の開始行を正しく特定する
- 宣言文の行番号は最初の行を使用する

---

### 問題 5: 内部クラス（static nested class）のメソッド追跡でエラー

**コード**:
```java
static class OrderService {
    String processOrder(int itemId, int quantity) { ... }
}

@Test
void test() {
    OrderService service = new OrderService();
    String result = service.processOrder(100, 5);  // ← この行が追跡できない
}
```

**現在の動作**: `[Probe For STATEMENT] Cause line not found.` エラー

**期待する動作**: 内部クラスのメソッド呼び出しも正しく追跡できる

**原因の仮説**:
- JavaParser でのソースファイル解析時に、内部クラスのメソッドが正しく解決できていない可能性
- または、JDI でのクラス名の解決（`ProbeBenchmarkFixture$OrderService`）が問題

**修正方針**:
- `ValueChangingLineFinder` が内部クラスのメソッドを正しく見つけられるか確認
- 必要に応じて、クラス名のマッピングロジックを修正

---

## ステータス

- **問題 1**: ✅ 完了（2026-02-04, コミット 0828d62）
- **問題 2, 3**: ✅ 基本完了（2026-02-05, コミット ddb7e6d）、残課題は `docs/issues/` に記録
- **問題 4**: 未着手
- **問題 5**: 未着手