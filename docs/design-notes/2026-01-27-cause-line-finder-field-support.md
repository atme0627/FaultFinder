# CauseLineFinder のフィールド変数サポート

## 概要

CauseLineFinder および TargetVariableTracer にフィールド変数のサポートを追加した。
同一インスタンス内でのフィールド変更の原因行を特定できるようになった。

**日付**: 2026-01-27
**対象ファイル**:
- `src/main/java/jisd/fl/infra/jdi/TargetVariableTracer.java`
- `src/test/java/jisd/fl/infra/jdi/TargetVariableTracerTest.java`
- `src/test/java/jisd/fl/core/domain/CauseLineFinderTest.java`
- `src/test/resources/fixtures/exec/src/main/java/jisd/fl/fixture/FieldTarget.java`（新規作成）
- `src/test/resources/fixtures/exec/src/main/java/jisd/fl/fixture/CauseLineFinderFixture.java`
- `src/test/resources/fixtures/exec/src/main/java/jisd/fl/fixture/TargetVariableTracerFixture.java`

**関連コミット**:
- `fdf3b78`: TargetVariableTracer にフィールドトレース機能を追加
- `b7e8ef8`: CauseLineFinder のフィールド統合テストを追加
- `d8391b8`: コード品質の改善

**関連 design-note**:
- `2026-01-27-cause-line-finder-refactoring.md`

---

## 背景

### 課題

CauseLineFinder は当初ローカル変数のみをサポートしており、フィールド変数は以下のコードでブロックされていた:

```java
// TargetVariableTracer.java (変更前)
if(target instanceof SuspiciousFieldVariable){
    throw new RuntimeException("SuspiciousFieldVariable is not supported.");
}
```

フィールド変数の cause line 特定は、障害局所化において重要なユースケースである:
- テスト対象クラスのフィールドが複数メソッドから変更される
- フィールドの不正な値がどのメソッドで設定されたかを特定する

### スコープの限定

ユーザーと議論した結果、以下のスコープに限定して実装を行った:

- **対象**: 同一インスタンス内でのフィールド変更のみ
- **対象外**:
  - 静的フィールド（部分的にサポート）
  - 複数インスタンス間でのフィールド変更の追跡
  - 継承階層をまたいだフィールド変更

---

## 実施した改善

### Phase 1: TargetVariableTracer のフィールドサポート

#### 変更内容

```java
// Before: フィールドは例外をスロー
if(target instanceof SuspiciousFieldVariable){
    throw new RuntimeException("SuspiciousFieldVariable is not supported.");
}
List<Integer> canSetLines = JavaParserTraceTargetLineFinder.traceTargetLineNumbers(target);

// After: ローカル/フィールドで分岐
List<Integer> canSetLines;
if (target instanceof SuspiciousLocalVariable localVariable) {
    // ローカル変数: メソッド内の変更行
    canSetLines = JavaParserTraceTargetLineFinder.traceTargetLineNumbers(localVariable);
} else {
    // フィールド: クラス全体の変更行
    canSetLines = ValueChangingLineFinder.findBreakpointLines(target);
}
```

#### watchVariableInLine の型変更

```java
// Before
private Optional<TracedValue> watchVariableInLine(
    StackFrame frame, SuspiciousLocalVariable sv, int locateLine, LocalDateTime watchedAt)

// After
private Optional<TracedValue> watchVariableInLine(
    StackFrame frame, SuspiciousVariable sv, int locateLine, LocalDateTime watchedAt)
```

#### 判断理由

**なぜ ValueChangingLineFinder.findBreakpointLines() を使うのか**:
- フィールドはクラス全体の任意のメソッドから変更可能
- JavaParserTraceTargetLineFinder はメソッド単位のため不適切
- ValueChangingLineFinder は既にフィールド対応済みだった

**なぜ型を SuspiciousVariable に変更したのか**:
- SuspiciousLocalVariable と SuspiciousFieldVariable の両方を扱うため
- 共通の親クラス SuspiciousVariable を使用
- メソッド内で instanceof による分岐で適切に処理

### Phase 2: テスト用 Fixture の作成

#### FieldTarget クラスの作成

ユーザーからのフィードバックに基づき、テスト対象となる別クラスを作成:

```java
package jisd.fl.fixture;

public class FieldTarget {
    int value;

    void initialize() { this.value = 0; }
    void setValue(int v) { this.value = v; }
    void increment() { this.value = this.value + 1; }
    void prepareAndSet(int v) { initialize(); setValue(v); }
}
```

#### 判断理由

**なぜ別クラスを作成したのか**:

ユーザーからの指摘:
> "testメソッドがfieldを持っていてそれがデバッグの対象になることは少ない気がします。
> 別のテスト対象のクラスがあってそのテストが失敗している、というシチュエーションを再現するようにしてください。"

実際のシナリオでは:
- テストクラス（`*Test.java`）がテスト対象クラスのメソッドを呼び出す
- テスト対象クラスのフィールドが複数メソッドから変更される
- フィールドの不正な値の原因行を特定したい

Fixture 内にフィールドを定義するとローカル変数のテストと本質的に変わらないため、
別クラスを作成してより現実的なシナリオを再現した。

### Phase 3: CauseLineFinder 統合テスト

#### テストケース

| テスト名 | シナリオ | 検証内容 |
|---------|---------|---------|
| field_modified_in_another_method | setValue(42) でフィールド変更 | setValue 内の代入行が cause line |
| field_modified_across_multiple_methods | initialize → setValue → increment → setValue | 各メソッド内の代入を追跡 |
| field_modified_in_nested_method_calls | prepareAndSet → initialize → setValue | ネストした呼び出しを追跡 |

### Phase 4: コード品質の改善

#### 変更内容

1. **未使用インポートの削除** (TargetVariableTracer.java)
   - `MethodElementName`, `SuspiciousFieldVariable`, `JavaParserUtils`

2. **コメントスタイルの統一**
   - `//コメント` → `// コメント` （スペース追加）

3. **静的フィールドの名前表記修正**
   ```java
   // Before
   "this." + f.name()

   // After
   rt.name() + "." + f.name()
   ```

4. **重複ヘルパーメソッドの削除**
   - `findFieldAssignLine` と `findAssignLine` が同一内容だったため統合
   - `findFieldAssignLineInClass` を削除し `findAssignLine` を再利用

---

## 技術的な議論

### 議論1: Fixture の設計

**初期案**: テストメソッド内にフィールドを定義
```java
@Test
void field_test() {
    int fieldVar = 42;  // これはローカル変数と変わらない
}
```

**問題点**:
- ローカル変数のテストと本質的に同じ
- フィールド特有の「複数メソッドから変更される」性質をテストできない

**採用案**: 別クラス FieldTarget を作成
```java
// FieldTarget.java
public class FieldTarget {
    int value;
    void setValue(int v) { this.value = v; }
}

// Test
@Test
void field_test() {
    FieldTarget target = new FieldTarget();
    target.setValue(42);  // 別メソッドでフィールドが変更される
}
```

**メリット**:
- 実際の障害局所化シナリオに近い
- 複数メソッドからのフィールド変更をテスト可能
- ネストしたメソッド呼び出しもテスト可能

### 議論2: 既存 Fixture の再利用

**質問**: TargetVariableTracerFixture と CauseLineFinderFixture で FieldTarget を共有すべきか？

**回答**: 共有した
- 同じシナリオをテストするため、重複を避ける
- FieldTarget は汎用的なテスト対象クラスとして設計
- 両方の Fixture から import して使用

### 議論3: 不要なテストケースの削除

**ユーザーからの指摘**:
> "Pattern 2-2e これがそもそもいらない気がします。localの時とほとんど変わらないです。"

`pattern2_2_contaminated_field_as_argument` は、フィールドを引数として渡すパターンだが、
ローカル変数を渡す場合と本質的に変わらないため削除した。

関連する未使用コードも削除:
- `staticFieldVar`
- `modifyField()`
- `fieldVar`

---

## テスト結果

### TargetVariableTracerTest

| テスト | 結果 |
|-------|------|
| observes_post_state_at_assignment_lines | ✅ |
| multiple_statements_in_one_line_observes_post_state | ✅ |
| exception_stops_tracing_at_last_executed_line | ✅ |
| early_return_observes_executed_lines_only | ✅ |
| loop_observes_same_line_multiple_times | ✅ |
| conditional_branch_observes_taken_path_only | ✅ |
| method_call_in_assignment_observes_post_state | ✅ |
| field_modified_in_another_method | ✅ (新規) |
| field_modified_across_multiple_methods | ✅ (新規) |
| field_modified_in_nested_method_calls | ✅ (新規) |

**計 10 テスト全て成功**

### CauseLineFinderTest

| テスト | 結果 |
|-------|------|
| pattern1a_simple_assignment | ✅ |
| pattern1a_multiple_assignments | ✅ |
| pattern1a_conditional_assignment | ✅ |
| pattern1b_declaration_with_initialization | ✅ |
| pattern1b_complex_expression_initialization | ✅ |
| pattern2_1_literal_argument | ✅ |
| pattern2_1_variable_argument | ✅ |
| pattern2_2_contaminated_variable_as_argument | ✅ |
| field_pattern_modified_in_another_method | ✅ (新規) |
| field_pattern_nested_method_calls | ✅ (新規) |

**計 10 テスト全て成功**

---

## 今後の課題

### 1. 複数インスタンスのフィールド追跡

**現状**: 同一インスタンス内の変更のみサポート

**課題**:
- 複数のインスタンスが同時に存在する場合、どのインスタンスのフィールドかを識別する必要がある
- JDI の ObjectReference を使った追跡が必要

### 2. 継承階層をまたいだフィールド

**現状**: 自クラスで定義されたフィールドのみ

**課題**:
- 親クラスのフィールドへのアクセス
- オーバーライドされたメソッドからの変更

### 3. 静的フィールドの完全サポート

**現状**: 静的フィールドの値は取得できるが、名前表記に改善の余地あり

**課題**:
- 複数クラスにまたがる静的フィールドの追跡
- クラスローディングのタイミングとの整合性

---

## まとめ

### 変更のポイント

1. **TargetVariableTracer の拡張**: ローカル/フィールドで分岐し、適切な行検出を実施
2. **現実的なテスト設計**: 別クラス FieldTarget により実際のシナリオを再現
3. **コード品質**: 重複削除、スタイル統一

### メリット

- **フィールドの cause line 特定が可能に**: 複数メソッドから変更されるフィールドの原因行を特定
- **現実的なテスト**: 実際の障害局所化シナリオに近いテストケース
- **保守性向上**: 重複コードの削除、スタイルの統一

### 影響範囲

- **変更ファイル**: 6 ファイル（新規 1、変更 5）
- **テスト**: 20 テスト成功（新規 5）
- **後方互換性**: ローカル変数の処理に影響なし

### 学び

- **TDD の有効性**: テストを先に書くことで仕様が明確になった
- **ユーザーフィードバックの重要性**: Fixture 設計の改善につながった
- **スコープの限定**: 同一インスタンスに限定することで実装がシンプルに

---

## コミット履歴

```
d8391b8 refactor: CauseLineFinder 関連コードの品質を改善
b7e8ef8 test: CauseLineFinder のフィールド統合テストを追加
fdf3b78 feat: TargetVariableTracer にフィールド変数のトレース機能を追加
```