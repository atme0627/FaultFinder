# Probe 実装修正計画

## 概要

ProbeTest で確認された3つの問題について、実装を修正する計画です。

## 修正すべき問題

### 問題 1: ネストしたメソッド呼び出しの階層構造

**コード**: `int x = outer(inner(5));`

**現在の動作**:
```
ASSIGN(x=outer(inner(5)))
├── RETURN(inner)
└── RETURN(outer)
```

**期待する動作**:
```
ASSIGN(x=outer(inner(5)))
└── RETURN(outer)
      └── ARGUMENT(outer の引数 = inner(5))
            └── RETURN(inner)
```

**修正方針**:
`JDISearchSuspiciousReturnsAssignmentStrategy` を修正して、**最外のメソッド呼び出しの戻り値のみ**を収集するようにする。内側のメソッド呼び出しは、ARGUMENT からの追跡で取得される。

---

### 問題 2, 3: ループ内の同一変数の複数 actualValue 追跡

**コード**:
```java
for (int i = 0; i < 3; i++) {
    x = x + i;  // x は 0 → 1 → 3 と変化
}
```

**現在の動作**: 最終値のみ追跡

**期待する動作**: x=3, x=1, x=0 のそれぞれを別ノードとして追跡

**根本原因**:
`ValueAtSuspiciousExpressionTracer` のコメントに以下の仕様が明記されている:
> 複数回SuspiciousExpressionが実行されているときは、**最後に実行された時の値を使用する**

つまり、これは意図的な設計であり、ループ内の全ての値を追跡するには仕様変更が必要。

**修正方針**:
`JDITraceValueAtSuspiciousAssignmentStrategy` 等のトレース戦略を修正して、**全ての実行時の値**を返すようにする。

---

## 修正対象ファイル

### 問題 1 の修正

**ファイル**: `src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsAssignmentStrategy.java`

**修正内容**:
- StepIn/StepOut のロジックを変更し、**最外のメソッド呼び出しのみ**を追跡
- 現在は全ての直接呼び出しを追跡しているが、最初のメソッドに入った後は内部の呼び出しをスキップする

**具体的な変更**:
- `handleStepInCompleted` で、既にメソッドに入っている場合は更にネストせず、最初のメソッドの終了を待つ
- または、`collectAtCounts` のロジックを見直し、最外の呼び出しのみを収集対象とする

### 問題 2, 3 の修正

**ファイル**: `src/main/java/jisd/fl/infra/jdi/JDITraceValueAtSuspiciousAssignmentStrategy.java`

**修正内容**:
- 現在は「最後に実行された時の値」のみを返す仕様
- **全ての実行時の値**を返すように変更する
- BreakpointEvent ごとに全変数の値を収集し、リストに蓄積する

**ファイル**: `src/main/java/jisd/fl/core/domain/NeighborSuspiciousVariablesSearcher.java`

**修正内容**:
- `.distinct()` は SuspiciousLocalVariable の equals() を使用するので、actualValue が異なれば保持される（変更不要の可能性）
- tracer が複数の値を返せば、自然に動作するはず

---

## 実装順序

1. **問題 2, 3 の調査**: `ValueAtSuspiciousExpressionTracer.traceAll()` の実装を確認
2. **問題 2, 3 の修正**: tracer または searcher を修正
3. **問題 1 の調査**: `JDISearchSuspiciousReturnsAssignmentStrategy` の詳細な動作を確認
4. **問題 1 の修正**: 最外メソッドのみを追跡するよう修正
5. **テスト更新**: 修正後の動作に合わせて `ProbeTest.java` を更新
6. **回帰テスト**: 全テストの実行

---

## 検証方法

1. `./gradlew test --tests "jisd.fl.usecase.ProbeTest"` で全テスト実行
2. 各シナリオの木構造が期待通りか確認

---

## リスク

- 問題 1 の修正は、他のテストケースに影響を与える可能性がある
- 問題 2, 3 の修正は、無限ループ防止のロジックに影響を与える可能性がある（同じ行の同じ変数を何度も追跡しないための `investigatedVariables`）

---

## 確認が必要なファイル

調査のため以下のファイルを確認する必要があります:

1. `src/main/java/jisd/fl/core/domain/internal/ValueAtSuspiciousExpressionTracer.java`
   - traceAll() の実装を確認
2. `src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsAssignmentStrategy.java`
   - 最外メソッドのみ追跡するための修正箇所を特定

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

**未着手** - ユーザーの承認待ち