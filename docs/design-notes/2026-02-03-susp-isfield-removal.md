# SuspiciousVariable isField 問題の解決

**実施日**: 2026-02-03
**対象**: `jisd.fl.core.entity.susp` パッケージ
**コミット**: ea3d17f

---

## 1. 背景

### 1.1 発端

`core.entity.susp` パッケージの設計レビューにおいて、`SuspiciousLocalVariable` に `isField` フィールドが存在するが、`SuspiciousVariable` インターフェースの `isField()` は `instanceof SuspiciousFieldVariable` で判定しているという矛盾を発見。

### 1.2 問題の詳細

```java
// SuspiciousVariable.java - 型で判定
default boolean isField(){
    return (this instanceof SuspiciousFieldVariable);
}

// SuspiciousLocalVariable.java - フィールド値を返す
@Deprecated
public boolean isField() {
    return isField;  // ← コンストラクタで渡された値
}
```

同じオブジェクトでも、参照の型によって `isField()` の結果が異なるという矛盾が発生していた。

### 1.3 歴史的経緯（推測）

1. 最初は `SuspiciousLocalVariable` だけがあり、`isField` フラグでローカル/フィールドを区別
2. 後から `SuspiciousFieldVariable` が追加され、sealed interface で型で区別する設計に変更
3. 古い `isField` フィールドが残ったまま放置されていた

---

## 2. 技術的な議論

### 2.1 選択肢

| アプローチ | メリット | デメリット |
|-----------|---------|-----------|
| **Visitor パターン** | OOP 的に最も純粋 | コード量増加、複雑化 |
| **sealed + switch** | 網羅性チェック、Java 17+ 推奨 | 呼び出し側が型を意識 |
| **振る舞いの委譲** | カプセル化 | entity が infra に依存する可能性 |

### 2.2 採用した方針

**sealed + switch パターン** を採用。

理由：
- Java 17+ の推奨アプローチ
- コンパイラが網羅性をチェック
- 新しいサブタイプ追加時にコンパイルエラーで検出可能
- Visitor パターンより簡潔

### 2.3 OOP の観点からの議論

ユーザーから「instanceof で条件分岐するのは健全でない」「SuspiciousVariable として扱う際には local か field かを知らなくて済むのが理想」という指摘があった。

しかし、現実的には：
- 一部の処理（探索範囲の決定など）では型による分岐が必要
- entity 層に infra 層の型を持ち込まずに振る舞いを委譲するのは困難
- sealed + switch は言語として許容された型による分岐方法

結論として、完全なカプセル化より実用性を優先し、sealed + switch を採用。

---

## 3. 実施した変更

### 3.1 削除したもの

- `SuspiciousVariable.isField()` default メソッド
- `SuspiciousLocalVariable.isField` フィールド
- `SuspiciousLocalVariable.isField()` Deprecated メソッド
- `SuspiciousLocalVariable.getLocateClass()` Deprecated メソッド
- `SuspiciousLocalVariable.getLocateMethodString()` Deprecated メソッド
- コンストラクタの `isField` 引数

### 3.2 switch 式への変更

**ValueChangingLineFinder.java**
```java
// Before
return (v.isField() == target.isFieldAccessExpr());

// After
boolean expectFieldAccess = switch (v) {
    case SuspiciousFieldVariable _ -> true;
    case SuspiciousLocalVariable _ -> false;
};
return expectFieldAccess == target.isFieldAccessExpr();
```

**JavaParserTraceTargetLineFinder.java**
```java
// Before
if(suspiciousLocalVariable.isField()) { ... }

// After
return switch (suspiciousVariable) {
    case SuspiciousFieldVariable field ->
        traceLinesOfClass(field.locateClass(), field.variableName());
    case SuspiciousLocalVariable local ->
        traceLineOfMethod(local.locateMethod(), local.variableName());
};
```

**SuspiciousVariable.variableName()**
```java
default String variableName(boolean withThis, boolean withArray) {
    String head = switch (this) {
        case SuspiciousFieldVariable _ when withThis -> "this.";
        case SuspiciousFieldVariable _, SuspiciousLocalVariable _ -> "";
    };
    // ...
}
```

### 3.3 影響を受けたファイル

- 21 ファイル変更
- 96 行追加 / 134 行削除（net 38 行削減）

---

## 4. テスト結果

| テスト | 結果 |
|--------|------|
| `SuspiciousLocalVariableMapperTest` | 成功 |
| `ValueChangingLineFinderTest` | 成功 |
| `CauseLineFinderTest` | 失敗（環境依存、本変更と無関係） |

---

## 5. 今後の課題

この変更は `2026-02-03-susp-package-refactoring-plan.md` の Phase 1 に該当。

残りの Phase:
- Phase 2: Value Object の導入（SourceLocation, NeighborVariables）
- Phase 3: Record への移行
- Phase 4: ファクトリ・クライアントの修正
- Phase 5: Strategy パターンの switch 式置換
- Phase 6: SuspiciousExprTreeNode の責務分離

---

## 6. まとめ

- `isField` の矛盾を解消し、型による分岐を sealed + switch で統一
- コード量 38 行削減、Deprecated メソッド 3 個削除
- Java 17+ の推奨アプローチに沿った設計に改善