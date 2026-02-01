# JVM セッション再利用: debuggee を1つの JVM で使い回す

## 概要

現在、各 Strategy が毎回 JVM を起動→JDWP接続→テスト実行→破棄している。
既存の JaCoCo サーバーパターン（TCP でテスト実行を指示し、JVM を使い回す）を活用し、
JDWP 付きのテスト実行サーバーを作ることで JVM 起動コストを排除する。

## アーキテクチャ

```
FaultFinder (親プロセス)
  │
  ├── [TCP]  ──→  JDIDebugServer JVM
  ├── [JDWP] ──→  (同じ JVM)
  │
  │  1. JDWP でアタッチ + ブレークポイント設定
  │  2. TCP で RUN 送信 → テスト実行開始
  │  3. JDWP でブレークポイントヒット → ステップ制御 → 情報収集
  │  4. テストメソッドの MethodExitEvent で完了検知
  │  5. ブレークポイント/リクエスト クリーンアップ → 2に戻る
  │
  └── handle.close() → サーバー終了
```

## 依存チェーン（セッション注入経路）

```
Probe
 ├── CauseLineFinder
 │    ├── TargetVariableTracer              ← new JUnitDebugger() (1箇所)
 │    └── JDISuspiciousArgumentsSearcher    ← new JUnitDebugger() (1箇所, handleAtMethodEntry API)
 ├── NeighborSuspiciousVariablesSearcher
 │    └── ValueAtSuspiciousExpressionTracer
 │         ├── JDITraceValueAtSuspiciousAssignmentStrategy   ← new JUnitDebugger() (1箇所)
 │         ├── JDITraceValueAtSuspiciousReturnValueStrategy  ← new JUnitDebugger() (1箇所)
 │         └── JDITraceValueAtSuspiciousArgumentStrategy     ← new JUnitDebugger() (1箇所)
 └── SuspiciousReturnsSearcher
      ├── JDISearchSuspiciousReturnsAssignmentStrategy       ← new JUnitDebugger() (1箇所)
      ├── JDISearchSuspiciousReturnsReturnValueStrategy      ← new JUnitDebugger() (1箇所)
      └── JDISearchSuspiciousReturnsArgumentStrategy         ← new JUnitDebugger() (1箇所)
```

計8箇所の `new JUnitDebugger()` を共有セッション経由に切り替える。

## 実装ステップ

### Step 1: JDIDebugServerMain（新規）

`src/main/java/jisd/fl/infra/jdi/JDIDebugServerMain.java`

JaCoCo サーバーと同じパターンの TCP サーバー。JaCoCo agent は不要。

- プロトコル: `RUN <test_fqmn>` → `JUnitTestRunner.runSingleTest()` → `OK <passed>` / `ERROR <msg>`
- `QUIT` → `BYE` → 終了
- `--port`, `--ppid`（親プロセス監視）対応

### Step 2: JDIDebugServerLaunchSpecFactory（新規）

`src/main/java/jisd/fl/infra/jvm/JDIDebugServerLaunchSpecFactory.java`

- メインクラス: `JDIDebugServerMain`
- JVM 引数: `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=<jdwpAddress>`
  - **`suspend=n`**: サーバーが先に TCP listen を開始する必要があるため
- クラスパス: JUnitLaunchSpecFactory と同じ（tool main + target/test bins + junit deps）
- プログラム引数: `--port <tcp_port> --ppid <parent_pid>`

### Step 3: DebugServerSession（新規・ライフサイクル管理）

`src/main/java/jisd/fl/infra/jdi/DebugServerSession.java`

```java
public class DebugServerSession implements Closeable {
    private final JVMProcess process;
    private final VirtualMachine vm;
    // TCP 接続フィールド

    public static DebugServerSession start() { ... }
    // 1. 空きポート確保（TCP + JDWP）
    // 2. JDIDebugServerLaunchSpecFactory + JVMLauncher で起動
    // 3. TCP 接続可能になるまでリトライ待機
    // 4. JDWP SocketAttach でアタッチ

    public VirtualMachine vm() { ... }
    public void runTest(MethodElementName test) { ... }  // TCP で RUN 送信
    public void cleanupEventRequests() { ... }           // 全リクエスト削除
    public void close() { ... }                          // QUIT + VM dispose + プロセス終了
}
```

### Step 4: EnhancedDebugger のリファクタリング

`src/main/java/jisd/fl/infra/jdi/EnhancedDebugger.java`

現在の `execute()` を分割:

1. **`setupBreakpointsAndRequests()`** — ブレークポイント設定 + ClassPrepareRequest 作成
2. **`runEventLoop(Supplier<Boolean> shouldStop)`** — 純粋なイベントループ（close しない）
3. **`execute(Supplier<Boolean> shouldStop)`** — 既存互換: setup + runEventLoop + close

追加:
- **`protected EnhancedDebugger(VirtualMachine vm)`** — 外部 VM を受け取るコンストラクタ（p = null）
- **`resetState()`** — handlers, breakpointLines, targetClass をクリア

### Step 5: SharedJUnitDebugger（新規）

`src/main/java/jisd/fl/infra/junit/SharedJUnitDebugger.java`

```java
public class SharedJUnitDebugger extends EnhancedDebugger {
    private final DebugServerSession session;
    private final MethodElementName testMethod;

    public SharedJUnitDebugger(DebugServerSession session, MethodElementName testMethod) {
        super(session.vm());  // JVM プロセスは持たない
        this.session = session;
        this.testMethod = testMethod;
    }

    @Override
    public void execute(Supplier<Boolean> shouldStop) {
        // 1. テストメソッドの MethodExitEvent で完了検知用リクエスト設定
        // 2. setupBreakpointsAndRequests()
        // 3. session.runTest(testMethod) — TCP で RUN 送信
        // 4. runEventLoop(combinedStop) — shouldStop OR testCompleted
        // 5. session.cleanupEventRequests() + resetState()
    }

    @Override
    public void close() { /* NO-OP: session がライフサイクルを管理 */ }
}
```

**テスト完了検知**: テストメソッドの `MethodExitEvent`（クラスフィルタ付き）で検知。
Strategy 側は関知しない。

**注意**: `handleAtMethodEntry()` を使う `JDISuspiciousArgumentsSearcher` には対応が必要。
この旧 API も同様に SharedJUnitDebugger で動くよう、`handleAtMethodEntry()` 内の
イベントループも `runEventLoop` ベースに統一するか、
あるいは SharedJUnitDebugger 側で `handleAtMethodEntry()` をオーバーライドする。

### Step 6: Strategy へのセッション注入

各クラスにコンストラクタで `DebugServerSession` を受け取れるようにする。

**変更対象（コンストラクタ追加 + `new JUnitDebugger()` → `new SharedJUnitDebugger()` 切替）:**

| クラス | ファイル |
|--------|----------|
| TargetVariableTracer | `infra/jdi/TargetVariableTracer.java` |
| JDISuspiciousArgumentsSearcher | `infra/jdi/JDISuspiciousArgumentsSearcher.java` |
| JDITraceValueAtSuspiciousAssignmentStrategy | `infra/jdi/JDITraceValueAtSuspiciousAssignmentStrategy.java` |
| JDITraceValueAtSuspiciousReturnValueStrategy | `infra/jdi/JDITraceValueAtSuspiciousReturnValueStrategy.java` |
| JDITraceValueAtSuspiciousArgumentStrategy | `infra/jdi/JDITraceValueAtSuspiciousArgumentStrategy.java` |
| JDISearchSuspiciousReturnsAssignmentStrategy | `infra/jdi/JDISearchSuspiciousReturnsAssignmentStrategy.java` |
| JDISearchSuspiciousReturnsReturnValueStrategy | `infra/jdi/JDISearchSuspiciousReturnsReturnValueStrategy.java` |
| JDISearchSuspiciousReturnsArgumentStrategy | `infra/jdi/JDISearchSuspiciousReturnsArgumentStrategy.java` |

**中間層（セッションの受け渡し）:**

| クラス | ファイル |
|--------|----------|
| CauseLineFinder | `core/domain/CauseLineFinder.java` |
| ValueAtSuspiciousExpressionTracer | `core/domain/internal/ValueAtSuspiciousExpressionTracer.java` |
| SuspiciousReturnsSearcher | `core/domain/SuspiciousReturnsSearcher.java` |
| NeighborSuspiciousVariablesSearcher | `core/domain/NeighborSuspiciousVariablesSearcher.java` |

**エントリポイント:**

| クラス | ファイル |
|--------|----------|
| Probe | `usecase/Probe.java` |
| FaultFinder | `FaultFinder.java` |

### Step 7: Probe でのセッション管理

```java
// Probe.run() 内
try (DebugServerSession session = DebugServerSession.start()) {
    this.causeLineFinder = new CauseLineFinder(session);
    this.neighborSearcher = new NeighborSuspiciousVariablesSearcher(session);
    this.searcher = new SuspiciousReturnsSearcher(session);
    // ... BFS 探索 ...
}
```

## 注意点

### JDISuspiciousArgumentsSearcher の特殊性
- `handleAtMethodEntry()` という旧 API を使用（独自イベントループあり）
- `countMethodCallAfterTarget()` 内で `vm.resume()` + `vm.eventQueue().remove()` を直接呼ぶ
- 共有セッション化の際、この旧 API も対応が必要
- 方針: `handleAtMethodEntry()` を `execute()` ベースにリファクタするか、SharedJUnitDebugger でオーバーライド

### イベントキューの汚染防止
- Strategy 完了後、`cleanupEventRequests()` で全リクエスト削除
- テストが完了するまでイベントループを回し続ける（途中で抜けない）
- これにより次の Strategy 実行時にキューが空の状態を保証

### 後方互換性
- `JUnitDebugger`（既存）はそのまま残す
- 各 Strategy のデフォルトコンストラクタも残す（単体テスト用）
- `session == null` の場合は従来通り `new JUnitDebugger()` を使うフォールバックも可能

## 開発ルール

- **何かを新しく実装した場合は必ずテストを書く。**
- **リファクタをする場合は、まずテストを生成して逐一回帰テストを行う。**
- **step や phase が終了するたびにユーザーに確認を取る。**
- **step ごと（必要に応じて step 内でも複数回）にコミットする。**

## 検証

```bash
# 1. 既存テストが壊れていないこと
./gradlew test --tests "jisd.fl.infra.jdi.*" --no-daemon

# 2. Strategy ベンチマーク（個別 Strategy の動作確認）
./gradlew test --tests "jisd.fl.benchmark.StrategyBenchmarkTest" --no-daemon -i 2>&1 | grep BENCH

# 3. Probe ベンチマーク（セッション再利用の効果測定）
./gradlew test --tests "jisd.fl.benchmark.ProbeBenchmarkTest" --no-daemon -i 2>&1 | grep BENCH

# 4. FaultFinderDemo ベンチマーク（Before/After 比較）
./gradlew test --tests "jisd.fl.benchmark.ProbeBenchmarkTest.FaultFinderDemoBench" --no-daemon -i 2>&1 | grep BENCH
```