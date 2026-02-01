# セッション注入ベンチマーク結果

## 背景

セッション注入（Static シングルトン方式）導入前後での JDI 探索処理の性能比較。
Before のデータは `2026-01-31-benchmark-baseline.md` のベースライン計測値。

## 計測環境

- **マシン**: macOS (Darwin 24.6.0)
- **JDK**: OpenJDK 25
- **計測方法**: JUnit 5 + `System.nanoTime()` + SLF4J Logger
- **実行コマンド**: `./gradlew test --tests "jisd.fl.benchmark.*" --no-daemon -i 2>&1 | grep BENCH`

## Strategy 単位

| Strategy | Before (ms) | After (ms) | 高速化 |
|---|---:|---:|---:|
| SearchReturns/Assignment (single) | 292 | 16 | 18x |
| SearchReturns/Assignment (multiple) | 300 | 23 | 13x |
| SearchReturns/ReturnValue (single) | 574 | 14 | 41x |
| SearchReturns/ReturnValue (multiple) | 605 | 42 | 14x |
| SearchReturns/Argument (single) | 286 | 17 | 17x |
| SearchReturns/Argument (multiple) | 315 | 29 | 11x |
| TraceValue/Assignment | 588 | 14 | 42x |
| TraceValue/ReturnValue | 286 | 14 | 20x |
| TraceValue/Argument | 373 | 287 | 1.3x |

## FaultFinderDemo シナリオ

| Phase | Before (ms) | After (ms) | 高速化 |
|---|---:|---:|---:|
| FaultFinder/init (coverage) | 3,029 | 2,922 | 1.0x |
| FaultFinder/probe (demo) | 37,634 | 6,103 | 6.2x |
| **合計** | **40,664** | **9,025** | **4.5x** |

## 所見

- Strategy 単位では **10〜40倍の高速化**。Before の 300-600ms の大部分が JVM 起動 + JDWP 接続コストだったことが裏付けられた。
- FaultFinder/probe は **37.6秒 → 6.1秒**（83% 短縮）。BFS 探索で Strategy が連鎖的に呼ばれるため、JVM 再利用の効果が大きい。
- init（JaCoCo カバレッジ計測）は変更なし。JDI とは独立した JVM プロセスを使用するため。

### TraceValue/Argument の遅延について

TraceValue/Argument のみ After=287ms と改善が小さく見えるが、これは **Strategy 間の性能差ではなく、JUnit テスト実行順序で最初に実行される Strategy の初回ウォームアップコスト** であった。

SharedJUnitDebugger.execute() の内訳をプロファイルした結果:

| 実行順 | eventLoop (ms) | 備考 |
|---:|---:|---|
| 1番目（TraceValue/Argument） | 262 ms | 初回ウォームアップ込み |
| 2番目 | 26 ms | |
| 3番目以降 | 9-25 ms | |

初回は debuggee JVM 側のクラスロード（fixture クラス + JUnit フレームワーク）や JDWP チャネルの初期化コストが含まれる。2回目以降は全て 10-26ms に収束しており、Strategy 間に本質的な性能差はない。