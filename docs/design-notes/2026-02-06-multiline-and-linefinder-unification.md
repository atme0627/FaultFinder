# マルチライン式対応と ValueChangingLineFinder への統一

## 背景

`TargetVariableTracer` でブレークポイント設定行を決定する際、変数の種類によって異なるクラスを使用していた:

```java
// 変更前
if (target instanceof SuspiciousLocalVariable localVariable) {
    canSetLines = JavaParserTraceTargetLineFinder.traceTargetLineNumbers(localVariable);
} else {
    canSetLines = ValueChangingLineFinder.findBreakpointLines(target);
}
```

- ローカル変数: `JavaParserTraceTargetLineFinder`（±2行ヒューリスティック）
- フィールド: `ValueChangingLineFinder`（変数変更行のみ）

この構成で以下の問題が発生していた:

1. **マルチライン式で原因行が見つからない** — `VariableDeclarator` の宣言行で begin 行のみを使用していたため、複数行にまたがる宣言でバイトコードが end 行に帰属した場合にマッチ失敗
2. **BP 発火不安定問題** — 統一後に発生（別途 CLE flush BP で解決済み、`2026-02-05-valuechanginglinefinder-bp-instability.md` 参照）

## 実施した変更

### 1. マルチライン式対応（コミット 190451b）

`ValueChangingLineFinder.collectMutationRanges()` で `VariableDeclarator` の全範囲を使用するように修正:

```java
// 修正前
for (int ln : findLocalVariableDeclarationLine(...)) {
    ranges.add(new LineRange(ln, ln));  // begin 行のみ
}

// 修正後
node.findAll(VariableDeclarator.class).stream()
    .filter(vd -> vd.getNameAsString().equals(v.variableName()))
    .forEach(vd -> vd.getRange().ifPresent(r ->
        ranges.add(new LineRange(r.begin.line, r.end.line))));  // 全範囲
```

これにより:
- `findCauseLines()`: begin 行のみを返す（原因行の特定用）
- `findBreakpointLines()`: begin〜end の全行を返す（BP 設定用）

### 2. JavaParserTraceTargetLineFinder の削除

`TargetVariableTracer` を `ValueChangingLineFinder` に統一:

```java
// 変更後
List<Integer> canSetLines = JavaParserValueChangingLineFinder.findBreakpointLines(target);
```

`JavaParserTraceTargetLineFinder.java` を削除。

### 3. パッケージ移動とリネーム

- `ValueChangingLineFinder` を `core.domain.internal` から `infra.javaparser` に移動
- クラス名を `JavaParserValueChangingLineFinder` に変更（パッケージ内の命名規約に統一）

## テスト

### 単体テスト（JavaParserValueChangingLineFinderTest）

| テストケース | 検証内容 |
|------------|---------|
| `localCase_includes_decl_assign_unary_lines` | 宣言・代入・++/-- 行の検出 |
| `multiLineAssignCauseLines_includes_begin_to_end_range` | マルチライン代入の cause 行 |
| `multiLineAssignBpLines_includes_begin_to_end_range` | マルチライン代入の BP 行 |
| `multiLineDeclBpLines_includes_begin_to_end_range` | マルチライン宣言の BP 行 |
| `arrayAssign_includes_array_assignment_line` | 配列代入行の検出 |
| `fieldAssign_includes_field_assignment_line` | フィールド代入行の検出 |

### E2E テスト（ProbeTest シナリオ 5）

| テストケース | 検証内容 |
|------------|---------|
| `scenario5_multiline_declaration` | 複数行にまたがる宣言の因果追跡 |
| `scenario5_multiline_assignment` | 複数行にまたがる代入の因果追跡 |

両テストとも木構造マッチング（`ExpectedNode`）で検証。

## 残存課題: ループ内変数追跡

`x = x + i` のような式で右辺の `x` が追跡されない問題は未解決。

### 原因

2つの問題が重畳している:

1. **investigatedVariables の重複判定**: `SuspiciousLocalVariable` は record のため、異なる実行時点の同じ変数が同一と判定される
2. **valueChangedToActualLine の最新優先**: `max(TracedValue::compareTo)` が最新タイムスタンプを返すため、過去の原因行に到達できない

### 解決の方向性

- `investigatedVariables` に時間的文脈（親ノード情報）を含める
- `valueChangedToActualLine` に「このタイムスタンプより前」という制約を渡す
- `CauseLineFinder` への時間的文脈の導入（アーキテクチャ変更）

詳細は本ドキュメント末尾の「ループ内変数追跡の調査記録」を参照。

## まとめ

- **マルチライン式対応**: `VariableDeclarator` の全範囲を使用することで解決
- **LineFinder 統一**: `JavaParserTraceTargetLineFinder` を削除し、`JavaParserValueChangingLineFinder` に一本化
- **BP 発火不安定問題**: 統一とは別の問題（CLE ポリシー）として解決済み
- **ループ内追跡**: 今後の課題として設計変更が必要

---

## 付録: ループ内 `x = x + i` 追跡の調査記録

### 現象

```java
void scenario4_loop_variable_update() {
    int x = 0;                    // line 69
    for (int i = 0; i < 3; i++) { // line 70
        x = x + i;                // line 71
    }
    assertEquals(999, x);
}
```

現在の因果木:
```
ASSIGN(x=3, line:71) x = x + i;
└── ASSIGN(x=1, line:71) x = x + i;
    └── ASSIGN(x=0, line:71) x = x + i;
        └── ASSIGN(i=0, line:70) for-loop init (leaf)
```

理想的な因果木:
```
ASSIGN(x=3, line:71)
└── ASSIGN(x=1, line:71)
    └── ASSIGN(x=0, line:71)
        ├── ASSIGN(x=0, line:69) int x = 0;   ← MISSING
        └── ASSIGN(i=0, line:70) for-loop init
```

### 問題 A: investigatedVariables の重複判定

`SuspiciousLocalVariable` は record のため `equals()` は全フィールドで比較される。

Step 2 で追加された `SuspLV(x, "0")` と Step 3 で生成される `SuspLV(x, "0")` は全フィールドが一致するため同一と判定されスキップされる。

しかし意味的には異なる:
- Step 2: 「2回目の反復の pre-state で x=0」→ 原因は line:71 (1回目の反復: x=0+0=0)
- Step 3: 「1回目の反復の pre-state で x=0」→ 原因は line:69 (初期化: int x = 0)

### 問題 B: valueChangedToActualLine の最新優先

仮に問題 A を解消しても、`CauseLineFinder.valueChangedToActualLine()` が `max(TracedValue::compareTo)` で最新タイムスタンプの TracedValue を返すため、line:69 には到達できない。

value="0" にマッチする TracedValue:
| TracedValue | line | timestamp | 意味 |
|-------------|------|-----------|------|
| `int x = 0` | 69 | t1 (早い) | 初期化 |
| `x = 0+0=0` | 71 | t2 (遅い) | 1回目の反復 |

`max()` → (71, t2) → 現在処理中のノードと同じ → サイクル

### 解決には両方の対処が必要

問題 A のみ、問題 B のみの解決では不十分。`CauseLineFinder` への時間的文脈の導入という設計変更が求められる。
