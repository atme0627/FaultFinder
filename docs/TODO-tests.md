# TODO: テストの再実装

外部プロジェクト依存のため一時削除されたテストの情報。
将来的に本プロジェクト内にテスト用リソースを用意して再実装する。

## 削除日

2026-02-03

---

## ✅ 再実装完了

### ProbeTest ✅

- **再実装ファイル**: `src/test/java/jisd/fl/usecase/ProbeTest.java`
- **フィクスチャ**: `src/test/resources/fixtures/exec/src/main/java/jisd/fixture/ProbeFixture.java`
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
- **フィクスチャ**: `src/test/resources/fixtures/exec/src/main/java/jisd/fixture/PolymorphismFixture.java`
- **テスト内容**:
  - `polymorphism_single_call_collects_return_value()` - 単一ポリモーフィズム呼び出し
  - `polymorphism_loop_identifies_circle_execution()` - ループ内 Circle 実行の特定
  - `polymorphism_loop_identifies_rectangle_execution()` - ループ内 Rectangle 実行の特定
  - `polymorphism_nested_collects_all_return_values()` - ネストしたポリモーフィズム
  - `polymorphism_multiple_in_return_collects_all()` - 複数の Shape を組み合わせた return
- **本質的な検証**: `locateMethod()` が実装クラス（Circle, Rectangle）を返すことを確認
- **再実装日**: 2026-02-03

### CoverageAnalyzerTest ✅

- **再実装ファイル**: `src/test/java/jisd/fl/usecase/CoverageAnalyzerTest.java`
- **フィクスチャ**: `src/test/resources/fixtures/exec/src/main/java/jisd/fixture/CoverageFixture.java`
- **テスト内容**:
  - `analyze_collects_coverage_for_all_tests()` - カバレッジ収集の基本動作
  - `analyze_counts_passed_and_failed_tests_correctly()` - 成功/失敗テストの ep/ef カウント
  - `analyze_line_coverage_has_correct_ep_ef_ratio()` - LINE カバレッジの ep/ef 比率
  - `analyze_covers_conditional_branches()` - 条件分岐カバレッジ
  - `analyze_sum_method_loop_coverage()` - ループカバレッジ
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

### ProbeBenchmarkTest ✅

- **再実装ファイル**: `src/test/java/jisd/fl/benchmark/ProbeBenchmarkTest.java`
- **フィクスチャ**: `src/test/resources/fixtures/exec/src/main/java/jisd/fixture/ProbeBenchmarkFixture.java`
- **テスト内容**:
  - `bench_depth_extreme()` - 深さ極端: 20段のネスト
  - `bench_repetition_extreme()` - 繰り返し極端: ループで同一メソッド100回
  - `bench_branch_extreme()` - 分岐極端: 2^10 = 1024 nodes
  - `bench_polymorphism_extreme()` - 動的解決極端: 50種類の実装
  - `bench_realistic_multi_class()` - 現実的ケース: 複数メソッドチェーン
- **ベンチマーク結果**: `docs/design-notes/2026-02-04-probe-benchmark-results.md`
- **再実装日**: 2026-02-04

---

## 🔄 未実装（残タスク）

なし（全て完了）

---

## 再実装の方針

1. `src/test/java` 内にテスト用のサンプルクラスを作成
2. 外部プロジェクト (Project4Test) への依存を排除
3. `.env` の `TEST_PROJECT_DIR` 設定への依存を排除
4. 本プロジェクト内で完結するテストとして再実装
