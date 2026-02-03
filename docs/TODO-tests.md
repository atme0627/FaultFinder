# TODO: テストの再実装

外部プロジェクト依存のため一時削除されたテストの情報。
将来的に本プロジェクト内にテスト用リソースを用意して再実装する。

## 削除日

2026-02-03

---

## ✅ 再実装完了

### ProbeTest ✅

- **再実装ファイル**: `src/test/java/jisd/fl/usecase/ProbeTest.java`
- **フィクスチャ**: `src/test/resources/fixtures/exec/src/main/java/jisd/fl/fixture/ProbeFixture.java`
- **テスト内容**:
  - `scenario1_simple_assignment()` - 単純な代入追跡
  - `scenario1_assignment_with_neighbors()` - 隣接変数を持つ代入追跡
  - `scenario2_single_method_return()` - 単一メソッド戻り値追跡
  - `scenario2_method_with_variable_args()` - 変数引数を持つメソッド戻り値追跡
  - `scenario3_nested_method_calls()` - ネストしたメソッド呼び出し追跡
  - `scenario3_multi_level_nesting()` - 多段ネスト追跡
  - `scenario4_loop_variable_update()` - ループ内変数更新追跡
  - `scenario4_loop_with_method_call()` - ループ内メソッド呼び出し追跡
- **再実装日**: 2026-02-03

### SuspiciousExpressionTest (ポリモーフィズム部分) ✅

- **再実装ファイル**: `src/test/java/jisd/fl/infra/jdi/PolymorphismSearchReturnsTest.java`
- **フィクスチャ**: `src/test/resources/fixtures/exec/src/main/java/jisd/fl/fixture/PolymorphismFixture.java`
- **テスト内容**:
  - `polymorphism_single_call_collects_return_value()` - 単一ポリモーフィズム呼び出し
  - `polymorphism_loop_identifies_circle_execution()` - ループ内 Circle 実行の特定
  - `polymorphism_loop_identifies_rectangle_execution()` - ループ内 Rectangle 実行の特定
  - `polymorphism_nested_collects_all_return_values()` - ネストしたポリモーフィズム
  - `polymorphism_multiple_in_return_collects_all()` - 複数の Shape を組み合わせた return
- **本質的な検証**: `locateMethod()` が実装クラス（Circle, Rectangle）を返すことを確認
- **再実装日**: 2026-02-03

### 各戦略の単体テスト ✅

以下のテストは既に実装済み（外部依存なし）:

| テストファイル | テスト対象 |
|---------------|-----------|
| `JDITraceValueAtSuspiciousAssignmentStrategyTest.java` | 代入式の値トレース |
| `JDITraceValueAtSuspiciousReturnValueStrategyTest.java` | 戻り値の値トレース |
| `JDITraceValueAtSuspiciousArgumentStrategyTest.java` | 引数の値トレース |
| `JDISearchSuspiciousReturnsAssignmentStrategyTest.java` | 代入式からの疑わしい戻り値探索 |
| `JDISearchSuspiciousReturnsReturnValueStrategyTest.java` | 戻り値からの疑わしい戻り値探索 |
| `JDISearchSuspiciousReturnsArgumentStrategyTest.java` | 引数からの疑わしい戻り値探索 |

---

## 🔄 未実装（残タスク）

### 1. CoverageAnalyzerTest

- **元ファイル**: `src/test/java/jisd/fl/coverage/CoverageAnalyzerTest.java`
- **テスト対象**: `CoverageAnalyzer` - SBFL カバレッジ解析
- **テスト内容**:
  - Conditional/Loop/InnerClass のテストケースに対する LINE/METHOD/CLASS 粒度のカバレッジ計算
- **優先度**: 中

### 2. LineMethodCallWatcherTest

- **元ファイル**: `src/test/java/experiment/util/internal/finder/LineMethodCallWatcherTest.java`
- **テスト対象**: `LineMethodCallWatcher` - メソッド呼び出し行の監視機能
- **テスト内容**:
  - `simpleValueReturn()` - 単純な値を返すメソッドの戻り値追跡
  - `methodCallReturn()` - メソッド呼び出しの戻り値追跡
  - `nestedMethodCallReturn()` - ネストしたメソッド呼び出しの戻り値追跡
  - `callInArgument()` - 引数内のメソッド呼び出し追跡
  - `callStandardLibrary()` - 標準ライブラリ呼び出し時の追跡
- **優先度**: 低（内部ユーティリティ）

### 3. 厳しめのベンチマーク追加

`ProbeBenchmarkTest` に、tree の node 数が多いケースのベンチマークを追加する。

**目的**:
- 探索の計算量が多いケースでの性能評価
- 高速化施策の効果測定

**要件**:
- 本プロジェクト内で完結すること（外部依存なし）
- `src/test/resources/fixtures/exec/` 配下にテスト用のサンプルコードを追加
- ネストしたメソッド呼び出し、ループ内での複数回呼び出しなど、node 数が増えるケースを用意

**優先度**: 低

---

## 再実装の方針

1. `src/test/java` 内にテスト用のサンプルクラスを作成
2. 外部プロジェクト (Project4Test) への依存を排除
3. `.env` の `TEST_PROJECT_DIR` 設定への依存を排除
4. 本プロジェクト内で完結するテストとして再実装
