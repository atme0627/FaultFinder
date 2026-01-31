# JDI 探索処理ベンチマーク: ベースライン計測

## 背景

JDI 関連処理のリファクタリング（StepIn/StepOut パターン導入、collectAtCounts 方式への redesign 等）を経て、
探索時間の増加が懸念されたため、高速化の効果測定用ベンチマークを作成した。

## 計測環境

- **マシン**: macOS (Darwin 24.6.0)
- **JDK**: OpenJDK 25
- **計測方法**: JUnit 5 + `System.nanoTime()` + SLF4J Logger
- **実行コマンド**: `./gradlew test --tests "jisd.fl.benchmark.*" --no-daemon -i 2>&1 | grep BENCH`

## ベースライン結果 (2026-01-31)

### Strategy 単位

| Strategy | テストケース | 時間 (ms) |
|---|---|---:|
| SearchReturns/Assignment | single (helper(10)) | 292 |
| SearchReturns/Assignment | multiple (add(5) + multiply(3)) | 300 |
| SearchReturns/ReturnValue | single (helper(10)) | 574 |
| SearchReturns/ReturnValue | multiple (add(5) + multiply(3)) | 605 |
| SearchReturns/Argument | single (target(helper(10))) | 286 |
| SearchReturns/Argument | multiple (target2(add(5) + multiply(3))) | 315 |
| TraceValue/Assignment | loop (x + 1) | 588 |
| TraceValue/ReturnValue | method_call (doubleValue(y)) | 286 |
| TraceValue/Argument | simple (helper(x)) | 373 |

### FaultFinderDemo シナリオ (demo.SampleTest#sampleTest)

| Phase | 時間 (ms) |
|---|---:|
| FaultFinder init (coverage) | 3,029 |
| FaultFinder probe | 37,634 |
| **合計** | **40,664** |

### 所見

- 各 Strategy は 300-600 ms 程度。JVM 起動 + JDWP 接続のオーバーヘッドが大部分を占めると推測。
- FaultFinderDemo の probe は約 38 秒。BFS 探索で複数の Strategy が連鎖的に呼ばれるため。
- ReturnValue 系が Assignment/Argument 系より若干遅い傾向。

## 高速化検証: MethodEntry/Exit へのクラスフィルタ追加

### 仮説

MethodEntryRequest / MethodExitRequest にクラスフィルタがないため、
JDK 標準クラス（`java.*`, `sun.*` 等）のメソッド entry/exit でも
イベントが発火し、不要な suspend/resume と JDWP 通信が発生している。
`addClassExclusionFilter` で JDK クラスを除外すれば高速化できるはず。

### 実施内容

- `EnhancedDebugger.createMethodExitRequest` / `createMethodEntryRequest` に
  `addClassExclusionFilter("java.*")` 等を追加
- TraceValue/ReturnValue の MethodExit は対象クラスが既知のため
  `addClassFilter(className)` で正のフィルタを適用

### 結果

| ベンチマーク | Before (ms) | After (ms) | 差 (ms) |
|---|---:|---:|---:|
| SearchReturns/Assignment (single) | 292 | 288 | -4 |
| SearchReturns/Assignment (multiple) | 300 | 290 | -10 |
| SearchReturns/ReturnValue (single) | 574 | 566 | -8 |
| SearchReturns/ReturnValue (multiple) | 605 | 589 | -16 |
| SearchReturns/Argument (single) | 286 | 294 | +8 |
| SearchReturns/Argument (multiple) | 315 | 298 | -17 |
| TraceValue/Assignment | 588 | 575 | -13 |
| TraceValue/ReturnValue | 286 | 281 | -5 |
| TraceValue/Argument | 373 | 371 | -2 |
| FaultFinder/probe (demo) | 37,634 | 37,588 | -46 |

### 判断

**効果なし（測定誤差の範囲）。変更は revert した。**

fixture やデモのテスト対象が小規模で JDK クラスのメソッド呼び出しイベント数が少ないため。
ボトルネックは MethodEntry/Exit のイベント数ではなく、JVM 起動 + JDWP 接続、
および Strategy 1回あたりの JVM プロセス生成にあると推測される。

## ベンチマークファイル

- `src/test/java/jisd/fl/benchmark/StrategyBenchmarkTest.java` - Strategy 単位の計測
- `src/test/java/jisd/fl/benchmark/ProbeBenchmarkTest.java` - Probe.run() / FaultFinder.probe() の計測
