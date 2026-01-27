# CauseLineFinder の Post-State 観測対応

## 概要

TargetVariableTracer の post-state 観測変更に伴い、CauseLineFinder の valueChangedToActualLine() メソッドを修正し、post-state 前提のロジックに変更した。

**日付**: 2026-01-27
**関連ファイル**:
- `src/main/java/jisd/fl/core/domain/CauseLineFinder.java`
- `src/test/java/jisd/fl/core/domain/CauseLineFinderTest.java`
- `src/test/resources/fixtures/exec/src/main/java/jisd/fl/fixture/CauseLineFinderFixture.java`

**関連 design-note**:
- `2026-01-27-target-variable-tracer-post-state.md`

---

## 背景と課題

### 元々の問題

TargetVariableTracer が pre-state から post-state 観測に変更されたため、CauseLineFinder の valueChangedToActualLine() が以下の前提で動作しなくなった：

**Pre-state 前提のロジック**:
```java
for (int i = 0; i < tracedValues.size() - 1; i++) {
    TracedValue watchingLine = tracedValues.get(i);      // i番目: pre-state
    TracedValue afterAssignLine = tracedValues.get(i + 1); // i+1番目: 次の pre-state
    if (afterAssignLine.value.equals(actual)) {
        // watchingLine が原因行
    }
}
```

この前提：
- `tracedValues[i]` は i 行目の**実行前の値**（pre-state）
- `tracedValues[i+1]` は i+1 行目の**実行前の値**（= i 行目の実行後の値）
- 「i 行目の実行後に actual になった」を `tracedValues[i+1].value == actual` で判定

### Post-state 観測での正しい考え方

**Post-state では**:
- `tracedValues[i]` は i 行目の**実行後の値**（post-state）
- 「i 行目の実行後に actual になった」を直接 `tracedValues[i].value == actual` で判定可能
- `i+1` との比較は不要

---

## 解決策: Post-State 対応のロジック

### 設計の選択肢

#### 方針1: i+1 との比較を維持（不採用）
```java
for (int i = 0; i < tracedValues.size() - 1; i++) {
    TracedValue current = tracedValues.get(i);  // post-state
    TracedValue next = tracedValues.get(i + 1);  // 次の post-state
    // 何を比較すれば良い？ロジックが複雑化
}
```

**問題点**:
- post-state では「i 行目の実行後」と「i+1 行目の実行後」を比較することになり、意味が不明瞭
- 「値が変化した行」を特定するロジックが複雑化

#### 方針2: 直接値をチェック（採用）
```java
return tracedValues.stream()
        .filter(tv -> assignedLine.contains(tv.lineNumber))
        .filter(tv -> tv.value.equals(actual))
        .max(TracedValue::compareTo);
```

**メリット**:
- post-state の意味と完全に一致（各 TracedValue は既にその行の実行後の値）
- ロジックがシンプルで理解しやすい
- Stream API で宣言的に記述可能

---

## 実装の詳細

### 1. valueChangedToActualLine() の変更

**変更前（pre-state 前提）**:
```java
private List<TracedValue> valueChangedToActualLine(
        SuspiciousVariable target, List<TracedValue> tracedValues, String actual) {
    List<Integer> assignedLine = ValueChangingLineFinder.find(target);
    List<TracedValue> changedToActualLines = new ArrayList<>();
    for (int i = 0; i < tracedValues.size() - 1; i++) {
        TracedValue watchingLine = tracedValues.get(i);
        if (!assignedLine.contains(watchingLine.lineNumber)) continue;
        TracedValue afterAssignLine = tracedValues.get(i + 1);
        if (afterAssignLine.value.equals(actual))
            changedToActualLines.add(watchingLine);
    }
    changedToActualLines.sort(TracedValue::compareTo);
    return changedToActualLines;
}
```

**変更後（post-state 対応）**:
```java
private Optional<TracedValue> valueChangedToActualLine(
        SuspiciousVariable target, List<TracedValue> tracedValues, String actual) {
    List<Integer> assignedLine = ValueChangingLineFinder.find(target);
    return tracedValues.stream()
            .filter(tv -> assignedLine.contains(tv.lineNumber))
            .filter(tv -> tv.value.equals(actual))
            .max(TracedValue::compareTo);
}
```

### 2. 戻り値型の変更

**変更前**: `List<TracedValue>`
- 原因行候補を全て返す
- 呼び出し側で `.get(size - 1)` で最後を取得

**変更後**: `Optional<TracedValue>`
- 最後に実行された原因行を直接返す
- `max(TracedValue::compareTo)` で時系列的に最後のものを選択

### 3. 呼び出し側の変更

**変更前**:
```java
List<TracedValue> changeToActualLines = valueChangedToActualLine(...);
if (!changeToActualLines.isEmpty()) {
    TracedValue causeLine = changeToActualLines.get(changeToActualLines.size() - 1);
    ...
}
```

**変更後**:
```java
Optional<TracedValue> changeToActualLine = valueChangedToActualLine(...);
if (!changeToActualLine.isEmpty()) {
    TracedValue causeLine = changeToActualLine.get();
    ...
}
```

---

## テストケースの追加

### CauseLineFinderFixture.java

以下のパターンを網羅的にテスト（22パターン）：

#### Pattern 1a: 既存変数への代入（4パターン）
- 単純な代入
- 複数回の代入
- 条件分岐内での代入
- 計算式の代入

#### Pattern 1b: 宣言時の初期化（3パターン）
- 単純な初期化
- 複雑な式での初期化
- メソッド呼び出し結果での初期化

#### Pattern 2-1: 引数由来・直接渡す（4パターン）
- リテラル引数
- 変数引数
- 計算式引数
- 三項演算子引数

#### Pattern 2-2: 引数由来・汚染された変数を渡す（5パターン）
- 汚染された変数を引数として渡す
- 複数回汚染された変数を引数として渡す
- メソッド呼び出し結果を直接引数として渡す
- 配列要素（汚染済み）を引数として渡す
- フィールド（汚染済み）を引数として渡す

#### Field Pattern: フィールド変数への代入（6パターン）
- 同じクラス内でのフィールド代入
- 別メソッドでフィールドを変更
- static フィールドへの代入
- 複数回のフィールド代入
- 条件分岐内でのフィールド代入
- 汚染された変数をフィールドに代入

### CauseLineFinderTest.java

統合テスト（8テスト）を作成：
- Pattern 1a: 3テスト
- Pattern 1b: 2テスト
- Pattern 2: 3テスト

---

## 遭遇した問題と解決

### 問題1: pattern1b_complex_expression_initialization の失敗

**原因**:
```java
// Fixture
int x = a * 2 + b;  // 10 * 2 + 16 = 36

// Test
SuspiciousLocalVariable sv = new SuspiciousLocalVariable(..., "x", "42", ...);
```

計算結果が 36 なのに actualValue が "42" になっていた。

**解決**:
```java
SuspiciousLocalVariable sv = new SuspiciousLocalVariable(..., "x", "36", ...);
```

### 問題2: Pattern 2 系テスト全ての失敗

**原因**:
SuspiciousLocalVariable のコンストラクタ引数順序が逆

```java
// コンストラクタ定義
public SuspiciousLocalVariable(
    MethodElementName failedTest,     // 失敗したテストメソッド
    String locateMethod,              // suspicious variable がある場所
    ...
)

// 誤った呼び出し
SuspiciousLocalVariable sv = new SuspiciousLocalVariable(
    callee,           // calleeMethod(int) - 本来は locateMethod
    caller.toString() // pattern2_1_literal_argument() - 本来は failedTest
    ...
);
```

**解決**:
```java
SuspiciousLocalVariable sv = new SuspiciousLocalVariable(
    caller,           // pattern2_1_literal_argument() - failedTest
    callee.toString() // calleeMethod(int) - locateMethod
    ...
);
```

---

## テスト結果

### 最終結果: 全8テスト成功

✅ pattern1a_simple_assignment
✅ pattern1a_multiple_assignments
✅ pattern1a_conditional_assignment
✅ pattern1b_declaration_with_initialization
✅ pattern1b_complex_expression_initialization
✅ pattern2_1_literal_argument
✅ pattern2_1_variable_argument
✅ pattern2_2_contaminated_variable_as_argument

### テストの進捗

| 修正段階 | 成功/全体 | 内容 |
|---------|----------|------|
| 初期状態 | 0/8 | 全テスト失敗（予想通り） |
| valueChangedToActualLine() 修正後 | 4/8 | Pattern 1a, 1b-simple 成功 |
| pattern1b_complex 修正後 | 5/8 | Pattern 1b-complex 成功 |
| Pattern 2 修正後 | 8/8 | **全テスト成功** |

---

## 今後の課題

### 1. 残りの Fixture パターンのテスト追加

現在 Fixture には 22パターンあるが、Test では 8パターンのみ実装。
残り 14パターンのテストケースを追加することで、より網羅的なテストが可能。

### 2. Field Pattern のテスト

現在の Test では Field Pattern をカバーしていない。
SuspiciousFieldVariable を使用したテストケースの追加が必要。

### 3. エッジケースのテスト

- ループ内での複数回代入
- ネストしたメソッド呼び出し
- 例外が発生するケース

---

## 関連する議論と判断

### なぜ Optional<TracedValue> を返すのか

**選択肢1**: `List<TracedValue>` を返し続ける
- 全ての候補を返す
- 呼び出し側で選択

**選択肢2**: `Optional<TracedValue>` を返す（採用）
- 最後に実行された原因行を直接返す
- シンプルなAPI
- post-state では「最後に actual になった行」が明確に決まる

### なぜ Stream API を使うのか

- 宣言的で意図が明確
- `max(TracedValue::compareTo)` で「時系列的に最後」が一目瞭然
- フィルタ条件の追加が容易

### テストファースト開発のアプローチ

1. TargetVariableTracer の post-state 対応が完了
2. **先に CauseLineFinderTest と Fixture を作成**（全テスト失敗を確認）
3. CauseLineFinder.valueChangedToActualLine() を修正
4. テストを修正して全テスト成功

このアプローチにより：
- 期待される動作が明確になった
- リファクタリングの安全性が確保された
- デグレードの検出が容易になった

---

## まとめ

TargetVariableTracer の post-state 観測変更に伴い、CauseLineFinder を適切に修正した。

**変更のポイント**:
1. i+1 との比較を廃止し、直接値をチェック
2. 戻り値を `List` から `Optional` に変更
3. Stream API でシンプルに記述

**テスト戦略**:
- テストファースト開発で安全に移行
- 22パターンの Fixture で網羅的にカバー
- 8つの統合テストで主要パターンを検証

この修正により、post-state 観測の利点（最後の実行行の結果を確実に取得）を CauseLineFinder でも活用できるようになった。