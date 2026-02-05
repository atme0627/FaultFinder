# ValueChangingLineFinder 切り替えによる BP 発火不安定問題

## 背景

`TargetVariableTracer` で使用するブレークポイント行決定ロジックを
`JavaParserTraceTargetLineFinder`（広い BP カバレッジ: ±2行）から
`ValueChangingLineFinder`（狭い BP カバレッジ: 変数変更行のみ）に切り替えたところ、
`ProbeTest` の scenario1 と scenario2 が不安定化した。
他のシナリオ（scenario3〜）は安定。

### 影響範囲（5回実行の結果）

| Run | scenario1 | scenario2 | その他 |
|-----|-----------|-----------|--------|
| 1 | **FAILED** | PASSED | PASSED |
| 2 | **FAILED** | PASSED | PASSED |
| 3 | PASSED | **FAILED** | PASSED |
| 4 | PASSED | **FAILED** | PASSED |
| 5 | **FAILED** | **FAILED** | PASSED |

### 対象シナリオのコード

#### scenario1

```java
// ProbeFixture.java
void scenario1_assignment_with_neighbors() {
    int a = 10;                  // line 17
    int b = 20;                  // line 18
    int result = a + b;          // line 19
    assertEquals(999, result);   // line 20
}
```

期待される原因木:
```
ASSIGN(result=30, line 19)
├── ASSIGN(a=10, line 17)
└── ASSIGN(b=20, line 18)
```

#### scenario2

```java
// ProbeFixture.java
void scenario2_method_with_variable_args() {
    int a = 10;                  // line 36
    int b = 20;                  // line 37
    int x = calc(a, b);         // line 38
    assertEquals(999, x);       // line 39
}

private int calc(int a, int b) {
    return a + b;               // line 43
}
```

### scenario2 の期待される原因木

```
ASSIGN(x=30, line 38)
└── RETURN(calc, line 43: return a + b)
      ├── ARGUMENT(a, line 38: calc(a,b) の第1引数) → ASSIGN(a=10, line 36)
      └── ARGUMENT(b, line 38: calc(a,b) の第2引数) → ASSIGN(b=20, line 37)
```

## 変更内容

### コード変更

```java
// 変更前 (TargetVariableTracer.java)
List<Integer> canSetLines;
if (target instanceof SuspiciousLocalVariable localVariable) {
    canSetLines = JavaParserTraceTargetLineFinder.traceTargetLineNumbers(localVariable);
} else {
    canSetLines = ValueChangingLineFinder.findBreakpointLines(target);
}

// 変更後
List<Integer> canSetLines = ValueChangingLineFinder.findBreakpointLines(target);
```

### 動作の違い

| 対象 | JavaParserTraceTargetLineFinder | ValueChangingLineFinder |
|------|-------------------------------|------------------------|
| ローカル変数 | 宣言行 ±2行 | 変数変更行のみ（VariableDeclarator, AssignExpr, UnaryExpr） |
| メソッド引数 | 宣言行付近の行 | `[]`（変更行なし） |
| フィールド | - | 変更行のみ |

**重要な影響**: メソッド引数（パラメータ）に対して `ValueChangingLineFinder` は `[]` を返す。
これにより `TargetVariableTracer.traceValuesOfTarget()` は BP なしで execute() を実行し、
空の tracedValues を返す。`CauseLineFinder` は Pattern 1（代入）をスキップし、
Pattern 2（引数由来）に進む。ロジック自体は正しいが、execute() の回数・内容が変わる。

## 不安定化の対策試行

| 対策 | 成功率 | 結果 |
|------|--------|------|
| 対策なし（コード変更のみ） | 4-6/10 | 不安定 |
| `Thread.sleep(10)` 追加 | 3/10 | 悪化 |
| `-Xint`（JIT 無効化） | 3/10 | 改善なし |
| `vm.suspend()`/`vm.resume()` で BP 設定をラップ | 3/10 | 改善なし |

→ タイミング問題ではなく、ロジック上の原因がある可能性が高い。

## 調査で判明した事実

### 1. 非ランダムな決定的挙動

個別テスト実行で確認した結果、**テスト間依存ではなく、BFS の探索順序に依存する決定的な失敗**であることが判明。

具体的には、BFS で ARGUMENT(a) と ARGUMENT(b) を処理する順序が結果を決める:

| 処理順序 | 結果 |
|---------|------|
| ARGUMENT(a) → ARGUMENT(b) | **成功**（両方の BP が発火） |
| ARGUMENT(b) → ARGUMENT(a) | **失敗**（ARGUMENT(a) の BP が発火しない） |

処理順序は `JDIUtils.watchAllVariablesInLine()` 内の `HashMap` イテレーション順に依存する。

### 2. BP は設定されるが発火しない

診断ログにより確認:

```
# ARGUMENT(a) のトレース（失敗ケース）
[SETUP-DEBUG] targetClass=...ProbeFixture breakpointLines=[38] loadedClasses=1
[BP-DEBUG] SET line 38 at ...ProbeFixture (locations=1)     ← BP は正常に設定
[SESSION-DEBUG] before eventLoop: testCompleted=false
[TCP-DEBUG] sendRunCommand: RUN ... at T1
[TCP-DEBUG] readRunResult: response="OK 0" waitMs=4 at T1+4  ← テストが4msで完了（BP未発火）
[SESSION-DEBUG] after eventLoop: testCompleted=true shouldStop=false
```

- `createBreakpointRequest` は成功（locations=1）
- テストは line 38 を通過するはず（`int x = calc(a, b)` は必ず実行される）
- しかし `BreakpointEvent` がイベントループに到達しない
- テストが BP なしの場合と同じ速度（4ms）で完了する

### 3. 静的解析は正しい

以下を確認済み:

- `JavaParserSuspiciousExpressionFactory` はステートレス（共有可変状態なし）
- `SuspiciousArgument` は `List.copyOf()` でイミュータブル
- ARGUMENT(a) と ARGUMENT(b) のフィールド値は正しい:
  - 両方とも `locateLine = 38`, `invokeMethodName = calc`
  - ARGUMENT(a): `argIndex = 0`, `actualValue = "10"`
  - ARGUMENT(b): `argIndex = 1`, `actualValue = "20"`
- `directNeighborVariableNames` も正しく計算されている

### 4. TCP プロトコルは正常

- 各 execute() は 1 RUN → 1 応答を送受信
- `tcpResult.join()` で応答の読み取り完了を保証
- ストリームにスタートデータはない

### 5. `vm.resume()` の余分な呼び出しは無害

`SharedJUnitDebugger.execute()` のイベントループ後に `vm.resume()` を呼ぶが、
JDWP 仕様上、実行中の VM に対する resume は no-op（suspend count は負にならない）。

## 有力仮説: StepOver 着地行と BP 発火の干渉

### scenario1 の分析

scenario1 では `TargetVariableTracer` が隣接変数 a, b を順にトレースする。

**失敗パターン [a, b] 順:**
```
find(a) → BP@17 → HIT → StepOver → line 18 に着地 ★
find(b) → BP@18 → SET されるが HIT しない ✗
```

**成功パターン [b, a] 順:**
```
find(b) → BP@18 → HIT → StepOver → line 19 に着地（line 17 に無関係）
find(a) → BP@17 → HIT ✓
```

ログ証拠（失敗ケース）:
```
[BP-DEBUG] SET  line 17 at jisd.fixture.ProbeFixture (locations=1)
[BP-DEBUG] HIT line 17 for a
[EVENT-LOOP] event: StepEventImpl at jisd.fixture.ProbeFixture:18   ← StepOver が line 18 に着地
[BP-DEBUG] canSetLines for b: [18]
[BP-DEBUG] SET  line 18 at jisd.fixture.ProbeFixture (locations=1)  ← BP は設定成功
                                                                     ← しかし HIT なし
```

### scenario2 の BFS 全フロー追跡

scenario2 の BFS を全 execute() 呼び出しとともに追跡した:

```
[BFS初期化]
  execute #1: TargetVariableTracer(x=30) → BP at 38 → 発火 ✓

[BFS Step 1: ASSIGN(x, line 38)]
  execute #2: neighborSearcher(ASSIGN) → assignmentStrategy → BP at 38 → 発火 ✓
  execute #3: SuspiciousReturnsSearcher → returnValueStrategy → BP at 43 → 発火 ✓

[BFS Step 2: RETURN(calc, line 43)]
  execute #4: neighborSearcher(RETURN) → returnValueStrategy → BP at 43 → 発火 ✓
  → neighbor変数: param_a(calc内), param_b(calc内)

  [param処理: 順序は HashMap イテレーション順に依存]
  ---- b→a の場合（失敗パターン）----
  execute #5: traceValuesOfTarget(param_b) → canSetLines=[] → BP なし
  execute #6: searchSuspiciousArgument(param_b, calc) → MethodEntry → 成功
  execute #7: traceValuesOfTarget(param_a) → canSetLines=[] → BP なし
  execute #8: searchSuspiciousArgument(param_a, calc) → MethodEntry → 成功
  → children: [ARGUMENT(b), ARGUMENT(a)]

[BFS Step 3: ARGUMENT(b)]
  execute #9: neighborSearcher(ARGUMENT(b))
    → JDITraceValueAtSuspiciousArgumentStrategy → BP at 38 → 発火 ✓ (3回目)
  → neighbor: SuspiciousLocalVariable(b=20, method=scenario2)

  execute #10: traceValuesOfTarget(b=20) → BP at 37 → 発火
    → StepOver → ★ line 38 に着地 ★        ← 注目ポイント
  execute #11: (CauseLineFinder → ASSIGN(b, line 37))

[BFS Step 4: ARGUMENT(a)]
  execute #12: neighborSearcher(ARGUMENT(a))
    → JDITraceValueAtSuspiciousArgumentStrategy → BP at 38 → ✗ 発火しない (4回目)
```

### a→b の場合（成功パターン）

```
[BFS Step 3: ARGUMENT(a)]
  execute #9: BP at 38 → 発火 ✓ (3回目)
  → neighbor: SuspiciousLocalVariable(a=10, method=scenario2)

  execute #10: traceValuesOfTarget(a=10) → BP at 36 → 発火
    → StepOver → line 37 に着地               ← line 38 には触れない
  execute #11: (CauseLineFinder → ASSIGN(a, line 36))

[BFS Step 4: ARGUMENT(b)]
  execute #12: BP at 38 → 発火 ✓ (4回目)
```

### 決定的な差異

| | b→a（失敗） | a→b（成功） |
|---|---|---|
| execute #10 の BP 行 | 37 (`int b = 20;`) | 36 (`int a = 10;`) |
| StepOver 着地行 | **38** (`int x = calc(a, b);`) | 37 (`int b = 20;`) |
| execute #12 の BP at 38 | ✗ 発火しない | ✓ 発火する |

**唯一の違い**: b→a では execute #10 の StepOver が **line 38 に着地する**。
a→b では StepOver が line 37 に着地し、**line 38 には触れない**。

### 両シナリオに共通する一般則

| | scenario1（失敗） | scenario1（成功） | scenario2（失敗） | scenario2（成功） |
|---|---|---|---|---|
| 処理順序 | a→b | b→a | b→a | a→b |
| StepOver 着地行 | **18** | 19 | **38** | 37 |
| 次の BP 行 | **18** | 17 | **38** | 38 |
| BP 発火 | ✗ | ✓ | ✗ | ✓ |

**一般則:**

> **execute() #K の StepOver が line N に着地した場合、直後の execute() #K+1 で設定した BP@line N が発火しない。**

### 仮説の説明

`TargetVariableTracer` の StepOver がある行に着地することで、
JDWP エージェント内部のその行に関する何らかの状態が変化し、
直後の execute() で同じ行に設定した BreakpointRequest が発火しなくなる。

この仮説は **scenario1 と scenario2 の両方で**観測されたすべての事実と整合する:

1. **決定的である**: StepOver の着地行はソースコードの行番号で決まる
2. **順序依存である**: HashMap のイテレーション順が StepOver の着地行を決める
3. **BP は設定される**: `createBreakpointRequest` は成功するが、発火しない
4. **タイミング対策が効かない**: `-Xint`, `Thread.sleep`, `vm.suspend()/resume()` は無効
5. **2つの独立したシナリオで再現**: scenario1（代入のみ）と scenario2（メソッド呼び出し経由）で同一パターン

## 調査プロセス

### Phase 1: 非 JDI 層の調査

JDI の問題をいきなり疑わず、まず非 JDI 層を調査して正常性を確認した。

確認項目:
- **BP ライフサイクル管理**: `SharedJUnitDebugger.execute()` のクリーンアップチェーン
  （`cleanupEventRequests()` → `vm.resume()` → `tcpResult.join()` → `drainEventQueue()` → `cleanupEventRequests()` → `resetState()`）
  は完全。execute() 間で状態がリークする余地なし。
- **BFS ロジック**: `Probe.run()` の BFS キューと `CauseLineFinder.find()` のパターンマッチは正しい。
  順序依存は `JDIUtils.watchAllVariablesInLine()` 内の `frame.getValues()` が返す `HashMap` のイテレーション順のみ。
- **静的解析**: `JavaParserSuspiciousExpressionFactory` はステートレス、`SuspiciousArgument` はイミュータブル。
  ARGUMENT(a) と ARGUMENT(b) のフィールド値は正しい。
- **TCP プロトコル**: 各 execute() は 1 RUN → 1 応答を送受信。`tcpResult.join()` で応答の読み取り完了を保証。
- **debuggee スレッドモデル**: すべてのテストは同一スレッド（TCP ハンドラスレッド）で同期実行される。

**結論**: 非 JDI 層は 100% 正常。問題は JDI/JDWP 層にある。

### Phase 2: JDI 層の調査

#### アーキテクチャ確認
- **環境**: ARM64 (Apple Silicon) + OpenJDK 25 (EA)
- **debuggee**: 全テストが同一 TCP ハンドラスレッドで実行される

#### 仮説の精緻化

JDWP エージェントはスレッドごとにステッピング状態（single-step モード、最終ステップ位置等）を管理している。
`TargetVariableTracer` が StepOver → 着地 → StepRequest 削除 → vm.resume() という流れを実行すると、
JDWP エージェント内部で「このスレッドは line N で最後にステップした」という状態が残る。
次の execute() で同じ行に BP を設定しても、この残存状態がイベント発火を抑制する。

これはタイミング問題ではなく、JDWP エージェントの状態管理に起因する決定的な問題である。

## 実験による検証

### 実験 1: 追加 StepOver（TargetVariableTracer の StepEvent ハンドラ）

**目的**: 観測後に追加の StepOver を実行し、着地行を問題の行からずらせば解消するか確認。

**変更内容**:
```java
// StepEvent ハンドラ内、観測後に追加
EnhancedDebugger.createStepOverRequest(vm.eventRequestManager(), stepEvent.thread());
// → 次の StepEvent で着地行を記録
```

**結果**: 追加 StepOver が問題を**逆方向に引き起こした**。

例えば scenario2 で a→b 順（本来は成功パターン）の場合:
```
execute #10: traceValuesOfTarget(a=10) → BP at 36 → StepOver → line 37 → 追加 StepOver → line 38 ★
execute #12: BP at 38 → ✗ 発火しない
```

追加 StepOver が line 38 に着地することで、元の問題と同じパターンが発生。
**仮説の追加裏付け**: StepOver の着地行が次の BP 発火を抑制するという因果関係が確認された。

### 実験 2: StepOut への変更

**目的**: StepOver の代わりに StepOut を使えば、テストメソッドの外に出るため着地行が問題にならないか確認。

**変更内容**: `TargetVariableTracer` の BP ヒット時に `createStepOverRequest` → `createStepOutRequest` に変更。

**結果**:
| シナリオ | 成功率（10回） |
|---------|------------|
| scenario1 | **10/10 SUCCESS** |
| scenario2 | **10/10 SUCCESS** |
| scenario4（ループ） | **0/10 FAILED** |

**分析**:
- scenario1, scenario2: StepOut によりテストメソッドの外に出るため、問題の行に着地しない → 完全に安定化
- scenario4: StepOut がループ外に出てしまい、ループ内の2回目以降の BP ヒットが不可能に → 破壊的

**結論**: StepOut は仮説の正しさを立証するが、プロダクション修正としては使えない（ループシナリオが壊れる）。

### JDK バージョン別テスト

**目的**: JDK バージョンを下げれば問題が解消するか確認。

| JDK | 成功率（scenario1, 10回, `--rerun`） | 備考 |
|-----|--------------------------------------|------|
| OpenJDK 25 (EA) | 7/10 | 現在のバージョン |
| Corretto 22.0.2 | 5/10 | 同じ不安定さ |
| Corretto 21.0.4 | N/A（コンパイルエラー） | 無名変数 `_` が JDK 22+ |

全 ProbeTest 実行（JDK 22, `--rerun`, 5回）:
| Run | 結果 |
|-----|------|
| 1 | FAILED (scenario1) |
| 2 | FAILED (scenario2) |
| 3 | FAILED (scenario1) |
| 4 | FAILED (scenario2) |
| 5 | FAILED (scenario1 + scenario2) |

**結論**: JDK バージョンを下げても問題は解消しない。JDK 22 でも JDK 25 と同様の不安定さが再現する。
これは JDWP エージェントの根本的な動作に起因する問題であり、特定バージョンのリグレッションではない。

## 根本原因（確定）: JDWP Co-Located Event (CLE) ポリシー

> **JDWP エージェントはスレッドごとのステッピング状態を維持しており、StepRequest の削除や
> VM resume 後もこの状態が残存する。StepEvent が line N で発火した後、同じスレッドに対して
> line N に新たに設定した BreakpointRequest は発火しない。**

### 原因の正体: OpenJDK JDWP ネイティブエージェントの CLE ポリシー

OpenJDK `src/jdk.jdwp.agent/share/native/libjdwp/eventHandler.c` に実装されている
**Co-Located Event (CLE) ポリシー**が原因。JDWP エージェントは `BREAKPOINT`、`SINGLE_STEP`、
`METHOD_ENTRY` を「co-locatable」なイベントとして扱い、同一位置での重複発火を抑制する。

#### CLE ポリシーの 2 つのフェーズ

**Phase 1**: StepEvent が発火した行に**既に** BP が設定されている場合
→ 両イベントを 1 つの `EventSet` にまとめて配信（通常の IDE デバッグのケース）

**Phase 2**: StepEvent が発火した行に BP が**ない**場合
→ ステップ位置をスレッドの `CoLocatedEventInfo` 構造体に保存。
次のイベントが同じ位置の BreakpointEvent なら**抑制**（FaultFinder が踏むケース）

#### 関連するネイティブコード

```c
// eventHandler.c - deferEventReport()
// Phase 2: BP がない行で StepEvent が発火した場合、位置を保存
case EI_SINGLE_STEP:
    deferring = isBreakpointSet(clazz, method, location);
    if (!deferring) {
        threadControl_saveCLEInfo(env, thread, ei, clazz, method, location);
    }
    break;
```

```c
// eventHandler.c - skipEventReport()
// 保存された位置と一致する BreakpointEvent を抑制
if (ei == EI_BREAKPOINT) {
    if (threadControl_cmpCLEInfo(env, thread, clazz, method, location)) {
        skipping = JNI_TRUE;  // ★ BP イベントが抑制される
    }
}
threadControl_clearCLEInfo(env, thread);  // チェック後にクリア
```

```c
// threadControl.c - threadControl_saveCLEInfo()
// スレッドごとの ThreadNode に CLE 情報を保存
void threadControl_saveCLEInfo(JNIEnv *env, jthread thread, EventIndex ei,
                               jclass clazz, jmethodID method, jlocation location) {
    ThreadNode *node = findRunningThread(thread);
    if (node != NULL) {
        node->cleInfo.ei = ei;
        saveGlobalRef(env, clazz, &(node->cleInfo.clazz));
        node->cleInfo.method = method;
        node->cleInfo.location = location;
    }
}
```

#### FaultFinder で起きていること

```
1. execute() #K:
   StepOver が line 38 で発火
   → isBreakpointSet() = false（BP は削除済み or 別セッション）
   → saveCLEInfo(thread, SINGLE_STEP, class, method, location_38)

2. execute() #K の終了処理:
   cleanupEventRequests() → vm.resume() → drainEventQueue()
   → いずれも ThreadNode.cleInfo をクリアしない
   （clearCLEInfo は skipEventReport 内でしか呼ばれない）

3. execute() #K+1:
   BP@line 38 を新規設定 → テスト実行

4. debuggee スレッドが line 38 に到達
   → cbBreakpoint コールバック発火
   → reportEvents() → skipEventReport()
   → cmpCLEInfo: 保存された位置と一致!
   → skipping = JNI_TRUE → BP イベント抑制 ★
   → clearCLEInfo() → 以降は正常に戻る
```

**核心**: `cleanupEventRequests()`、`vm.resume()`、`drainEventQueue()` の**いずれも**ネイティブエージェントの
`ThreadNode.cleInfo` をクリアしない。クリアは `skipEventReport()` 内でしか行われず、
これは次のイベントのコールバック内でのみ呼ばれる。execute() 間で対象スレッドにイベントが発火しなければ、
CLE info は無期限に残存する。

### 証拠のまとめ

| 証拠 | 仮説との整合性 |
|------|--------------|
| StepOver 着地行 = 次の BP 行のとき失敗 | ✓ 完全一致 |
| StepOver 着地行 ≠ 次の BP 行のとき成功 | ✓ 完全一致 |
| 追加 StepOver で問題が逆転 | ✓ 因果関係を確認 |
| StepOut で scenario1/2 安定化 | ✓ 着地行がメソッド外なら干渉なし |
| Thread.sleep, -Xint, vm.suspend()/resume() が無効 | ✓ タイミング問題ではなく状態問題 |
| JDK 22 でも再現 | ✓ JDWP の根本動作 |
| テスト間依存なし（個別実行でも再現） | ✓ HashMap 順序のみに依存 |
| BP は正常に設定される（locations=1） | ✓ 設定は成功、発火のみ抑制 |

### 影響条件

この問題が発生する条件:
1. 同一スレッドで複数回 execute() を実行する
2. execute() #K で StepOver を使用する
3. StepOver の着地行が line N である
4. execute() #K+1 で line N に BP を設定する

## 著名 IDE はなぜこの問題に遭遇しないのか

### IDE と FaultFinder のセッションモデルの違い

| | IntelliJ / VSCode / Eclipse | FaultFinder |
|---|---|---|
| セッションモデル | 1回のデバッグ = 1セッション | 同一 JVM で複数 execute() を逐次実行 |
| BP 設定タイミング | ステップ**前**に設定済み | 前回のステップ**後**に新規設定 |
| スレッド再利用 | ユーザ操作ごとに同一スレッド | 全 execute() が同一スレッド |
| CLE info の寿命 | 次のイベントで即クリア | execute() 境界を越えて残存 |

### IDE のステップ/BP 相互作用パターン

3大 IDE（IntelliJ Community、VSCode Java Debug、Eclipse JDT Debug）はすべて同じパターン:

1. ユーザがデバッグ開始**前**に BP を設定する
2. ステップ実行中に BP 行に着地した場合、CLE ポリシーの **Phase 1** が適用される
   → `isBreakpointSet()` = true → `saveCLEInfo()` は呼ばれない
   → StepEvent と BreakpointEvent が同一 `EventSet` にまとめて配信される
3. BP が「ステップ後に新規設定される」ケースがそもそも発生しない

### 各 IDE の実装詳細

**IntelliJ IDEA Community Edition**
（[DebugProcessImpl.java](https://github.com/JetBrains/intellij-community)）
- 各 Debug 実行で新規 JVM プロセスを起動（`-agentlib:jdwp=...`）
- ステップ中に BP ヒット → ステップをキャンセルし BP を優先
- JVM セッションの逐次再利用は行わない

**VSCode Java Debug**
（[microsoft/java-debug](https://github.com/microsoft/java-debug)）
- `StepRequest` に `addCountFilter(1)` を設定（1回で自動無効化）
- ステップ中に BP ヒット → `pendingStepRequest` を削除
- [Issue #120](https://github.com/Microsoft/java-debug/issues/120): ステップ中の BP ヒットで
  "Only one step request allowed per thread" エラーが発生した事例あり（修正済み）

**Eclipse JDT Debug**
（[eclipse.jdt.debug](https://github.com/eclipse-jdt/eclipse.jdt.debug) `JDIThread.java`）
- `StepHandler.abortStep()` でステップ中の BP ヒットを処理
- `deleteStepRequest()` + `setPendingStepHandler(null)` でクリーンアップ

### FaultFinder が踏む Phase 2 のパターン

FaultFinder は BP を「前回のステップ完了後に新規設定」する。
この時点で前回の BP は削除済みなので、StepEvent 発火時に `isBreakpointSet()` = false となり、
CLE ポリシーの **Phase 2** が適用される。

Phase 2 では `saveCLEInfo()` が呼ばれ、CLE info が `ThreadNode` に保存される。
execute() 間の cleanup（`cleanupEventRequests()`, `vm.resume()`, `drainEventQueue()`）は
ネイティブエージェントの `ThreadNode.cleInfo` に触れないため、CLE info が次の execute() まで残存し、
同じ位置に設定された新しい BP の発火を抑制する。

**IDE はこの Phase 2 パターンを踏まないため、問題に遭遇しない。**

## 対策の方向性

### 短期（ワークアラウンド）

CLE ポリシーの Phase 2 を回避または CLE info をクリアする方法:

1. **CLE info 強制クリア（ダミーイベント）**: execute() 間で、問題の行と**異なる**行に
   ダミー BP を設定して発火させる。`skipEventReport()` が呼ばれ `clearCLEInfo()` が実行される。
   CLE info は「次のイベントのコールバック」でしかクリアされないため、
   何らかのイベントを対象スレッドで発火させる必要がある。
2. **StepOut + ループ検出の組み合わせ**: 通常は StepOut を使い、ループ内の場合のみ StepOver を使う。
   StepOut は着地行がメソッド外になるため CLE info が問題にならない。
3. **BP を先に設定**: execute() #K のステップ前に、#K+1 で必要な BP を設定しておく。
   → Phase 1 が適用され `saveCLEInfo()` が呼ばれない。
   ただし BFS の探索順序が事前に分からないため実装が複雑。

### 中期

4. **WatchpointEvent への移行**: 変数の値変化を BP+StepOver ではなく WatchpointEvent
   （JDWP の ModificationWatchpoint）で観測する。
   WatchpointEvent は co-locatable イベントに含まれないため CLE ポリシーの影響を受けない。
5. **MethodExitEvent + 値フィルタリング**: メソッドの出口で全変数を観測する方式に変更。

### 長期

6. **OpenJDK JDWP エージェントへのバグ報告**: StepRequest 削除後も CLE info が残存する問題を報告。
   `cleanupEventRequests()` 相当の操作で CLE info もクリアされるべきという主張が可能。
7. **Java Agent（JVMTI ネイティブ）への移行**: JDWP を介さず直接 JVMTI を使い、
   CLE ポリシーの影響を完全に回避する。

## 残存する疑問

- `EventSet.resume()` と `VirtualMachine.resume()` の違いが CLE info のクリアに影響するか
- ダミーイベント方式で CLE info を確実にクリアできるか（実験が必要）
- JVMTI の `SetEventNotificationMode` で `SINGLE_STEP` を toggle すると CLE info がリセットされるか

## 現在のコード状態

- `TargetVariableTracer`: `ValueChangingLineFinder` に統一済み（実験コードはすべて revert 済み）
- `JavaParserTraceTargetLineFinder.java`: 削除済み
- 各所に診断ログ追加済み（`[BP-DEBUG]`, `[SETUP-DEBUG]`, `[EVENT-LOOP]`, `[TCP-DEBUG]`, `[SESSION-DEBUG]`, `[DRAIN-DEBUG]`, `[ARG-TRACE]`, `[PROBE-DEBUG]`, `[NEIGHBOR-DEBUG]`）
- `build.gradle`: `showStandardStreams = true` 追加済み

## まとめ

- **根本原因**: OpenJDK JDWP ネイティブエージェントの **Co-Located Event (CLE) ポリシー**
  （`eventHandler.c` の `skipEventReport()`）が、StepEvent 着地行と同じ行に設定された次の BP の発火を抑制する
- **メカニズム**: StepEvent 発火時に `saveCLEInfo()` で位置が保存され、`clearCLEInfo()` は次のイベントの
  コールバック内でしか呼ばれないため、execute() 境界を越えて残存する
- **IDE が遭遇しない理由**: IDE は BP をステップ前に設定するため CLE Phase 1（co-located 配信）が適用され、
  `saveCLEInfo()` が呼ばれない。FaultFinder のような逐次 execute() 再利用パターンは IDE では発生しない
- **JDK バージョン非依存**: JDK 22, 25 で同一の問題を確認。CLE ポリシーは JDWP エージェントの基本設計
- **実験で検証済み**: StepOut 実験で因果関係を確認、追加 StepOver 実験で問題の逆転を確認
- **有力な対策**: ダミーイベントで CLE info を強制クリア / WatchpointEvent への移行 / JVMTI 直接利用

## 参考資料

- [OpenJDK eventHandler.c](https://github.com/openjdk/jdk/blob/master/src/jdk.jdwp.agent/share/native/libjdwp/eventHandler.c) — CLE ポリシー実装
- [OpenJDK threadControl.c](https://github.com/openjdk/jdk/blob/master/src/jdk.jdwp.agent/share/native/libjdwp/threadControl.c) — `ThreadNode.cleInfo` の保存・比較・クリア
- [JDI EventSet 仕様](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jdi/com/sun/jdi/event/EventSet.html) — co-located イベントの配信規則
- [IntelliJ Community DebugProcessImpl.java](https://github.com/JetBrains/intellij-community) — ステップ/BP 相互作用の実装
- [microsoft/java-debug Issue #120](https://github.com/Microsoft/java-debug/issues/120) — ステップ中 BP ヒットの既知問題
- [Eclipse JDT Debug JDIThread.java](https://github.com/eclipse-jdt/eclipse.jdt.debug) — ステップ/BP 相互作用の実装
