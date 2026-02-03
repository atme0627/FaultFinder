# ValueAtSuspiciousExpressionTracer 改善課題

## 概要

`ValueAtSuspiciousExpressionTracer` および関連する戦略クラスの改善課題をまとめる。

---

## 課題一覧

### 高優先度

（現時点ではなし）

### 中優先度

#### 1. 複数回実行時の識別問題

**対象**: 全戦略クラス

**問題**:
同一行が複数回実行され、同じ `actualValue` が複数回出現する場合、どの実行かを区別できない。

```java
for (int i = 0; i < 3; i++) {
    x = compute(i);  // actualValue = "5" が複数回発生しうる
}
```

**現状の挙動**: 最初に `actualValue` と一致した実行を採用

**改善案**:
- objectId での区別
- 実行回数のカウント
- コールスタックの深さや呼び出し元での識別

**備考**: エッジケースのため優先度は中

---

### 低優先度

#### 2. 参照型（Reference Type）の対応

**対象**: `JDITraceValueAtSuspiciousAssignmentStrategy.validateIsTargetExecution`

**現状**:
```java
if (!assignTarget.isPrimitive())
    throw new RuntimeException("Reference type has not been supported yet.");
```

**課題**:
- 参照型の「同一性」をどう判断するか
- `toString()` の結果？オブジェクトID？
- 同じ値を持つ別インスタンスとの区別

---

#### 3. 配列型の対応

**対象**: `JDITraceValueAtSuspiciousAssignmentStrategy.validateIsTargetExecution`

**現状**:
```java
if (assignTarget.isArray())
    throw new RuntimeException("Array type has not been supported yet.");
```

**課題**:
- `arr[i] = value` でインデックスが動的な場合の追跡
- 配列全体 vs 変更された要素のみの保持

**備考**: `JDIUtils.watchAllVariablesInLine` では配列の `[0]` のみ観測している（暫定実装）

---

## 設計改善（処理が固まってから検討）

#### 4. validateIsTargetExecution の配置統一

**現状**:
- `JDITraceValueAtSuspiciousAssignmentStrategy` 内: `validateIsTargetExecution(StepEvent, SuspiciousVariable)`
- `JDIUtils` 内: `validateIsTargetExecution(MethodExitEvent, String)`
- `JDIUtils` 内: `validateIsTargetExecutionArg(MethodEntryEvent, String, int)`

**課題**: 同名メソッドが異なるクラスに存在し、一貫性がない

**改善案**:
- 全て JDIUtils に移動
- または各戦略クラス内に留める（カプセル化重視）

---

#### 5. 型安全性の改善

**対象**: `TraceValueAtSuspiciousExpressionStrategy` インターフェース

**現状**: 各戦略でキャストが必要
```java
SuspiciousAssignment suspAssign = (SuspiciousAssignment) suspExpr;
```

**改善案**:
- ジェネリクス導入
- 各タイプ専用のインターフェース

---

#### 6. 重複コードの削減

**対象**: 3つの戦略クラス

**共通処理**:
- Debugger生成
- result リストの初期化
- 「既に情報取得済みなら return」チェック
- 周辺変数の観測
- イベントループのパターン

**改善案**: テンプレートメソッドパターンの適用

---

## 更新履歴

- 2026-01-27: 初版作成（Assignment戦略のレビューから）
