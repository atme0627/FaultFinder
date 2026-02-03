# Step 6-7: セッション注入 — Static シングルトン方式

## ステータス: 完了

## 設計

### コンセプト
`JDIDebugServerHandle` を static シングルトンとして管理する。
Probe が `startShared()` でライフサイクルを管理し、
Strategy は `JDIDebugServerHandle.createSharedDebugger(testMethod)` で直接デバッガを取得する。

中間層の変更は不要。コンストラクタ注入も不要。

### JDIDebugServerHandle の変更

```java
public class JDIDebugServerHandle implements Closeable {
    private static volatile JDIDebugServerHandle shared;

    /** Probe が呼ぶ。共有セッションを起動する。 */
    public static JDIDebugServerHandle startShared() throws IOException {
        if (shared != null) throw new IllegalStateException("shared session already exists");
        shared = start();
        return shared;
    }

    /** Strategy が呼ぶ。共有セッションから debugger を生成する。 */
    public static SharedJUnitDebugger createSharedDebugger(MethodElementName testMethod) {
        return new SharedJUnitDebugger(shared, testMethod);
    }

    // ... 既存メソッド ...
}
```

### Strategy の変更（8クラス共通パターン）

```java
// Before:
JUnitDebugger debugger = new JUnitDebugger(currentTarget.failedTest);

// After:
EnhancedDebugger debugger = JDIDebugServerHandle.createSharedDebugger(currentTarget.failedTest);
```

### Probe の変更

```java
public SuspiciousExprTreeNode run(int sleepTime) {
    try (JDIDebugServerHandle session = JDIDebugServerHandle.startShared()) {
        // ... 既存の BFS 探索ロジック（変更なし）...
    }
}
```

## 実装順序

### Phase 1: JDISuspiciousArgumentsSearcher リファクタリング (完了)
`handleAtMethodEntry()` を `execute()` ベースに書き換え。
他の Strategy と同じ `setBreakpoints()` + `registerEventHandler()` + `execute()` パターンに統一。

現在の処理:
1. `new JUnitDebugger()` → `handleAtMethodEntry()` (独自イベントループ)
2. ハンドラ内で `countMethodCallAfterTarget()` が直接 `vm.resume()` + `vm.eventQueue().remove()`

リファクタリング後:
1. デバッガ生成 → `registerEventHandler(MethodEntryEvent.class, ...)` → `execute(shouldStop)`
2. `countMethodCallAfterTarget()` もハンドラ内で完結

ファイル: `src/main/java/jisd/fl/infra/jdi/JDISuspiciousArgumentsSearcher.java`

### Phase 2: JDIDebugServerHandle に static 管理 + createSharedDebugger() 追加 (完了)

ファイル: `src/main/java/jisd/fl/infra/jdi/testexec/JDIDebugServerHandle.java`

- `private static volatile JDIDebugServerHandle shared`
- `startShared()` — 共有セッション起動、`close()` 時に `shared = null`
- `createSharedDebugger(testMethod)` — `new SharedJUnitDebugger(shared, testMethod)` を返す

### Phase 3: Strategy 8クラスのデバッガ生成箇所を変更 (完了)

各クラスの `new JUnitDebugger(...)` を `JDIDebugServerHandle.createSharedDebugger(...)` に差し替え。

| クラス | ファイル |
|--------|----------|
| TargetVariableTracer | `src/main/java/jisd/fl/infra/jdi/TargetVariableTracer.java` (行40) |
| JDISuspiciousArgumentsSearcher | `src/main/java/jisd/fl/infra/jdi/JDISuspiciousArgumentsSearcher.java` (行27) |
| JDITraceValueAtSuspiciousAssignmentStrategy | `src/main/java/jisd/fl/infra/jdi/JDITraceValueAtSuspiciousAssignmentStrategy.java` (行34) |
| JDITraceValueAtSuspiciousReturnValueStrategy | `src/main/java/jisd/fl/infra/jdi/JDITraceValueAtSuspiciousReturnValueStrategy.java` (行44) |
| JDITraceValueAtSuspiciousArgumentStrategy | `src/main/java/jisd/fl/infra/jdi/JDITraceValueAtSuspiciousArgumentStrategy.java` (行38) |
| JDISearchSuspiciousReturnsAssignmentStrategy | `src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsAssignmentStrategy.java` (行56) |
| JDISearchSuspiciousReturnsReturnValueStrategy | `src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsReturnValueStrategy.java` (行55) |
| JDISearchSuspiciousReturnsArgumentStrategy | `src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsArgumentStrategy.java` (行58) |

### Phase 4: Probe に try-with-resources 追加 (完了)

ファイル: `src/main/java/jisd/fl/usecase/Probe.java`

`run()` 内で `try (JDIDebugServerHandle session = JDIDebugServerHandle.startShared())` で囲む。

### Phase 5: テスト移行 + イベントループ改善 (完了)

7テストクラスに共有セッションのライフサイクル管理を追加:
- `@BeforeAll`: `JDIDebugServerHandle.startShared()`
- `@AfterAll`: `session.close()`
- `@BeforeEach`: `session.cleanupEventRequests()`

実装中に発見・修正した問題:

1. **`queue.remove()` の無限ブロック**: テスト完了後にイベントが来なくなると `shouldStop` チェックに到達できない。
   → `queue.remove(200)` に変更し、タイムアウト時に `shouldStop` をポーリング。

2. **SharedJUnitDebugger の MethodExitEvent 依存**: テスト失敗時（例外終了）に MethodExitEvent が発火しない。
   → CompletableFuture による TCP 応答非同期読み取りに変更。

3. **EventQueue の残存イベント干渉**: EventRequest 削除後もキューに入済みの StepEvent が残り、次の Strategy 実行時に NPE を引き起こす。
   → `SharedJUnitDebugger.execute()` 終了時に `drainEventQueue()` で残存イベントを読み捨て。

テスト結果: 77テスト全通過（0 failures, 2 skipped）、flaky なし。

## 設計の経緯

### なぜ中間層経由ではないか
- JaCoCo は `CoverageAnalyzer`（usecase層）で一括管理し、中間層を経由しない
- 中間層にセッションを渡すのはトランプデータ（中間層はセッションを使わず転送するだけ）
- core.domain 層が infra の `JDIDebugServerHandle` に依存するレイヤー違反

### なぜ Function ファクトリではないか
- `JDIDebugServerHandle` が attach を管理しているので、handle からデバッガを返すのが自然

### なぜ Static シングルトンか
- JVM は実質的にシングルトン（1つのセッションを共有する）
- Strategy も中間層もコンストラクタ変更不要
- 変更箇所が最小（Strategy の1行差し替え + Probe の try-with-resources + Handle の static 管理）
- 将来複数 JVM にしても対応可能