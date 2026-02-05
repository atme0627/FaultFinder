# ループ内 `x = x + i` で x の値が追跡されない問題の調査

## 背景

前回の修正（BlockStmt 除外、ForStmt init ハンドラ）により、ループ内変数追跡の因果ツリーが
以下のように改善された:

```
ASSIGN(x=3, line:71) x = x + i;
└── ASSIGN(x=1, line:71) x = x + i;
    └── ASSIGN(x=0, line:71) x = x + i;
        └── ASSIGN(i=0, line:70) for-loop init (leaf)
```

しかし、`x = x + i` の右辺の `x` が追跡されていない。
理想的には ASSIGN(x=0, line:71) の子ノードに `int x = 0;` (line:69) が存在すべき。

```
ASSIGN(x=3, line:71) x = x + i;
└── ASSIGN(x=1, line:71) x = x + i;
    └── ASSIGN(x=0, line:71) x = x + i;
        ├── ASSIGN(x=0, line:69) int x = 0;   ← MISSING
        └── ASSIGN(i=0, line:70) for-loop init
```

## テスト対象のフィクスチャ

```java
// ProbeFixture.java
void scenario4_loop_variable_update() {
    int x = 0;                    // line 69
    for (int i = 0; i < 3; i++) { // line 70
        x = x + i;                // line 71
    }
    assertEquals(999, x);
}
// 実行: i=0: x=0+0=0, i=1: x=0+1=1, i=2: x=1+2=3 → 最終値 x=3
```

## 原因分析

### Probe 実行フローのトレース

| Step | 処理対象 | 隣接変数 | SuspLV | 結果 |
|------|----------|----------|--------|------|
| 0 | Target(x=3) | - | SuspLV(x,"3") | ASSIGN(x=3, line:71) |
| 1 | ASSIGN(x=3) | x=1, i=2 | SuspLV(x,"1"), SuspLV(i,"2") | x=1→OK, i=2→FAIL |
| 2 | ASSIGN(x=1) | x=0, i=1 | SuspLV(x,"0"), SuspLV(i,"1") | x=0→OK, i=1→FAIL |
| 3 | ASSIGN(x=0) | x=0, i=0 | SuspLV(x,"0"), SuspLV(i,"0") | **x=0→SKIP**, i=0→OK |

**Step 3 で `SuspLV(x,"0")` がスキップされる理由**: Step 2 で既に `investigatedVariables` に追加済み。

### 根本原因: 2つの問題の重畳

#### 問題 A: `investigatedVariables` の重複判定

`SuspiciousLocalVariable` は record のため、`equals()` は全フィールドで比較される。

Step 2 で追加された `SuspLV(x, "0")` と Step 3 で生成される `SuspLV(x, "0")` は
全フィールドが一致するため同一と判定される。

しかし意味的には異なる:
- Step 2: 「2回目の反復の pre-state で x=0」→ 原因は line:71 (1回目の反復: x=0+0=0)
- Step 3: 「1回目の反復の pre-state で x=0」→ 原因は line:69 (初期化: int x = 0)

**関連コード**:
- `Probe.java:59-61` — `investigatedVariables.contains(suspVar)` による重複チェック
- `SuspiciousLocalVariable.java` — record の自動生成 `equals()` (全フィールド比較)

#### 問題 B: `valueChangedToActualLine` の最新優先ロジック

仮に問題 A を解消しても、`CauseLineFinder.valueChangedToActualLine()` が
`max(TracedValue::compareTo)` で最新タイムスタンプの TracedValue を返すため、
line:69 には到達できない。

```java
// CauseLineFinder.java:121-128
private Optional<TracedValue> valueChangedToActualLine(...) {
    return tracedValues.stream()
            .filter(tv -> assignedLine.contains(tv.lineNumber))
            .filter(tv -> tv.value.equals(actual))
            .max(TracedValue::compareTo);  // ← 最新タイムスタンプを選択
}
```

value="0" にマッチする TracedValue:
| TracedValue | line | timestamp | 意味 |
|-------------|------|-----------|------|
| `int x = 0` | 69 | t1 (早い) | 初期化 |
| `x = 0+0=0` | 71 | t2 (遅い) | 1回目の反復 |

`max()` → (71, t2) → 現在処理中の ASSIGN(x=0, line:71) と同じ → **サイクル**

### 問題の構造

```
SuspLV(x,"0") が Step 2 で追加
  ↓
Step 3 で同じ SuspLV(x,"0") が Skip される  ← 問題 A: dedup
  ↓ (仮に Skip されなくても)
causeLineFinder.find(x=0) → max → line:71   ← 問題 B: 最新優先
  ↓
現在のノードと同じ → サイクル
  ↓
line:69 (int x = 0) に到達不能
```

**両方の問題を解決しないと `int x = 0;` には到達できない。**

## 解決の方向性（今後の課題）

### 問題 A の解決策候補

`investigatedVariables` の重複判定に「どの文脈から調査されたか」を含める。
例: 親ノード (SuspiciousExpression) の情報を SuspiciousVariable に付与する。

### 問題 B の解決策候補

`valueChangedToActualLine` に時間的文脈（「このタイムスタンプより前の原因を探す」）を渡す。
現在の `max()` を「指定タイムスタンプ以前の max」に変更する。

### 共通の設計課題

現在の `CauseLineFinder` はステートレスな設計（「変数 x が値 v を取った最後のタイミング」を返す）。
ループ内の因果追跡には「どの実行時点から遡るか」という時間的文脈が必要であり、
アーキテクチャレベルの変更が求められる。

## まとめ

- `x = x + i` の右辺 `x` が追跡されない原因は、`investigatedVariables` の record equals による
  重複判定（問題 A）と `valueChangedToActualLine` の最新優先ロジック（問題 B）の重畳
- 問題 A のみ、問題 B のみの解決では不十分で、両方の対処が必要
- 解決には CauseLineFinder への時間的文脈の導入という設計変更が必要
