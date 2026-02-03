# TODO: テストの再実装

外部プロジェクト依存のため一時削除されたテストの情報。
将来的に本プロジェクト内にテスト用リソースを用意して再実装する。

## 削除日

2026-02-03

## Project4Test 依存のテスト

### 1. LineMethodCallWatcherTest

- **元ファイル**: `src/test/java/experiment/util/internal/finder/LineMethodCallWatcherTest.java`
- **テスト対象**: `LineMethodCallWatcher` - メソッド呼び出し行の監視機能
- **テスト内容**:
  - `simpleValueReturn()` - 単純な値を返すメソッドの戻り値追跡
  - `methodCallReturn()` - メソッド呼び出しの戻り値追跡
  - `nestedMethodCallReturn()` - ネストしたメソッド呼び出しの戻り値追跡
  - `callInArgument()` - 引数内のメソッド呼び出し追跡
  - `callStandardLibrary()` - 標準ライブラリ呼び出し時の追跡
- **依存リソース**: `experiment.util.internal.finder.LineMethodCallWatcherTarget` (Project4Test)

### 2. CoverageAnalyzerTest

- **元ファイル**: `src/test/java/jisd/fl/coverage/CoverageAnalyzerTest.java`
- **テスト対象**: `CoverageAnalyzer` - SBFL カバレッジ解析
- **テスト内容**:
  - Conditional/Loop/InnerClass のテストケースに対する LINE/METHOD/CLASS 粒度のカバレッジ計算
- **依存リソース**: `org.sample.coverage.ConditionalTest`, `org.sample.coverage.LoopTest`, `org.sample.coverage.InnerClassTest` (Project4Test)

### 3. ProbeTest

- **元ファイル**: `src/test/java/jisd/fl/probe/ProbeTest.java`
- **テスト対象**: `Probe` - 疑わしい式の探索
- **テスト内容**:
  - `MinimumTest` - JUnit テスト実行と変数観測の最小確認
  - `CalcTest` - メソッド呼び出しを含む Probe 実行
  - `ConditionalTest` - 条件分岐を含む Probe 実行と JSON 出力・読み込み
  - `LoopTest` - ループを含む Probe 実行
  - `runFromJsonTest()` - JSON からの Probe ターゲット読み込みと実行 (Defects4j 依存も含む)
- **依存リソース**: `org.sample.MinimumTest`, `org.sample.CalcTest`, `org.sample.coverage.*` (Project4Test)

### 4. SuspiciousExpressionTest

- **元ファイル**: `src/test/java/jisd/fl/probe/info/SuspiciousExpressionTest.java`
- **テスト対象**: `SuspiciousReturnsSearcher` - 疑わしい戻り値の探索
- **テスト内容**:
  - `polymorphism()` - ポリモーフィズムを含むメソッド呼び出しの戻り値追跡
  - `polymorphismLoop()` - ループ内でのポリモーフィズム
  - `polymorphismLoopReturn()` - ループ内での戻り値追跡
  - `polymorphismLoopArgument()` - ループ内での引数追跡
  - 空のテストメソッド多数 (chaining, nestedCalling, etc.)
- **依存リソース**: `org.sample.MethodCallingTest`, `org.sample.shape.*` (Project4Test)

### 5. SuspiciousArgumentTest

- **元ファイル**: `src/test/java/jisd/fl/probe/info/SuspiciousArgumentTest.java`
- **テスト対象**: `JDISuspiciousArgumentsSearcher` - 疑わしい引数の探索
- **テスト内容**:
  - `searchSuspiciousArgument()` - メソッド引数の追跡
- **依存リソース**: `org.sample.CalcTest`, `org.sample.util.Calc` (Project4Test)

## 再実装の方針

1. `src/test/java` 内にテスト用のサンプルクラスを作成
2. 外部プロジェクト (Project4Test) への依存を排除
3. `.env` の `TEST_PROJECT_DIR` 設定への依存を排除
4. 本プロジェクト内で完結するテストとして再実装

## 優先度

- **高**: ProbeTest, SuspiciousExpressionTest (コア機能のテスト)
- **中**: CoverageAnalyzerTest, SuspiciousArgumentTest
- **低**: LineMethodCallWatcherTest (内部ユーティリティ)
- **低**: 厳しめのベンチマーク追加 (下記参照)

## 追加予定: 厳しめのベンチマーク

`ProbeBenchmarkTest` に、tree の node 数が多いケースのベンチマークを追加する。

### 目的

- 探索の計算量が多いケースでの性能評価
- 高速化施策の効果測定

### 要件

- 本プロジェクト内で完結すること（外部依存なし）
- `src/test/resources/fixtures/exec/` 配下にテスト用のサンプルコードを追加
- ネストしたメソッド呼び出し、ループ内での複数回呼び出しなど、node 数が増えるケースを用意
