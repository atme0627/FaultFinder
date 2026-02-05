# ValueChangingLineFinder 切り替えによる BP 発火不安定問題

## 背景

`TargetVariableTracer` で使用するブレークポイント行決定ロジックを
`JavaParserTraceTargetLineFinder`（広い BP カバレッジ: ±2行）から
`ValueChangingLineFinder`（狭い BP カバレッジ: 変数変更行のみ）に切り替えたところ、
`ProbeTest.scenario2_method_with_variable_args` が不安定化した（10回中4回程度しか成功しない）。

### 対象シナリオのコード

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

### 期待される原因木

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

### BFS 全フローの追跡

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

### 仮説の説明

`TargetVariableTracer` の StepOver が line 38 に着地する
（`int b = 20;` の次の行が `int x = calc(a, b);` であるため）ことで、
JDWP エージェント内部の line 38 に関する何らかの状態が変化し、
直後の execute() で同じ line 38 に設定した BreakpointRequest が発火しなくなる。

この仮説は観測されたすべての事実と整合する:

1. **決定的である**: StepOver の着地行はソースコードの行番号で決まる
2. **順序依存である**: b→a のみ StepOver が line 38 に着地する
3. **BP は設定される**: `createBreakpointRequest` は成功するが、発火しない
4. **タイミング対策が効かない**: `-Xint`, `Thread.sleep`, `vm.suspend()/resume()` は無効

## 検証方法（未実施）

1. `TargetVariableTracer` の StepEvent 着地行をログ出力し、相関を確認
2. 人為的に StepEvent を line 38 に発生させた後、BP at 38 の発火を確認
3. StepOver を使わない代替観測方法の検討

## 未解決の疑問

- JDWP エージェントの内部で StepEvent と BreakpointRequest がどう相互作用するか
- HotSpot の BP 実装（bytecode opcode 置換）と single-stepping の干渉の可能性
- `EventSet.resume()` と `VirtualMachine.resume()` の微妙な違いの影響
  - 現在 `EnhancedDebugger.runEventLoop()` は `vm.resume()` を使用（line 147）

## 現在のコード状態

- `TargetVariableTracer`: `ValueChangingLineFinder` に統一済み
- `JavaParserTraceTargetLineFinder.java`: 削除済み
- 各所に診断ログ追加済み（`[BP-DEBUG]`, `[SETUP-DEBUG]`, `[EVENT-LOOP]`, `[TCP-DEBUG]`, `[SESSION-DEBUG]`, `[DRAIN-DEBUG]`, `[ARG-TRACE]`, `[PROBE-DEBUG]`, `[NEIGHBOR-DEBUG]`）
- `build.gradle`: `showStandardStreams = true` 追加済み
