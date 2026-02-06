# 問題 4: 複数行にまたがる式の解析で原因行が見つからない

## 現象

```java
int x = d1(d2(d3(          // line A
        d4(d5(d6(1))))));   // line B
```

→ `[Probe For STATEMENT] Cause line not found.` エラー
→ 1行に収めると動作する

## 原因分析

### `valueChangedToActualLine` でのマッチング失敗

エラーは `Probe.java:47` で `causeLineFinder.find(firstTarget)` が `Optional.empty()` を返すことに起因する。

```java
// CauseLineFinder.java:121-128
private Optional<TracedValue> valueChangedToActualLine(...) {
    List<Integer> assignedLine = ValueChangingLineFinder.findCauseLines(target);
    return tracedValues.stream()
            .filter(tv -> assignedLine.contains(tv.lineNumber))  // ← ここでミスマッチ
            .filter(tv -> tv.value.equals(actual))
            .max(TracedValue::compareTo);
}
```

### `collectMutationRanges` の非対称性

`ValueChangingLineFinder.collectMutationRanges` は変更イベントの種類によってレンジの構築方法が異なる：

| 変更イベント | レンジの構築 | マルチライン時 |
|---|---|---|
| **VariableDeclarator (宣言)** | `LineRange(begin, begin)` | begin 行のみ |
| **AssignExpr (代入)** | `LineRange(begin, end)` | 全範囲 |
| **UnaryExpr (++/--)** | `LineRange(begin, end)` | 全範囲 |

VariableDeclarator だけが `findLocalVariableDeclarationLine` の返り値（begin 行のみ）を使い、
`LineRange(ln, ln)` を作成している。

```java
// ValueChangingLineFinder.java:60-63
for (int ln : JavaParserUtils.findLocalVariableDeclarationLine(...)) {
    ranges.add(new LineRange(ln, ln));  // ← 単一行レンジ
}
```

```java
// JavaParserUtils.java:83-86 — begin 行のみ返す
result = vds.stream()
    .filter(vd1 -> vd1.getNameAsString().equals(localVarName))
    .map(vd -> vd.getRange().get().begin.line)  // ← begin のみ
    .toList();
```

一方、AssignExpr は全範囲を使用：

```java
// ValueChangingLineFinder.java:87-89
ae.getRange().ifPresent(r -> ranges.add(new LineRange(r.begin.line, r.end.line)));
```

### 失敗のフロー

```
JavaParser: VariableDeclarator("x").getRange() = (line A, line B)
  ↓
findLocalVariableDeclarationLine → begin.line = line A のみ
  ↓
collectMutationRanges → LineRange(A, A)
  ↓
findCauseLines → [A]
  ↓
valueChangedToActualLine: filter(tv -> [A].contains(tv.lineNumber))
  ↓
TracedValue.lineNumber = B（javac がバイトコードを line B に帰属した場合）
  ↓
マッチ失敗 → Pattern 2 (引数) も失敗 → "Cause line not found"
```

### javac のバイトコード行番号帰属

マルチライン式 `int x = d1(d2(d3(\n        d4(d5(d6(1))))))` に対して、javac は各サブ式を
そのソース行に帰属させる。式の構造によっては全バイトコードが line B に帰属する可能性がある。

- `d4(d5(d6(1)))` → line B のバイトコード
- `d3(...)`, `d2(...)`, `d1(...)`, `istore x` → 通常は line A だが、compiler 依存

line A にバイトコードがない場合、BP が line A に設定されず、
TracedValue は line B のみとなり、`findCauseLines` = [A] とのマッチが失敗する。

## 解決の方向性

### `collectMutationRanges` の修正

VariableDeclarator でも AssignExpr と同様に全範囲を使用する：

```java
// 現在
for (int ln : findLocalVariableDeclarationLine(...)) {
    ranges.add(new LineRange(ln, ln));
}

// 修正案: findLocalVariableDeclarationLine を変更するか、
// 直接 VariableDeclarator の range を使用
for (VariableDeclarator vd : ...) {
    Range r = vd.getRange().get();
    ranges.add(new LineRange(r.begin.line, r.end.line));
}
```

### `valueChangedToActualLine` の修正

`findCauseLines`（begin 行のみ）ではなく `findBreakpointLines`（全範囲）を使用する：

```java
// 現在
List<Integer> assignedLine = ValueChangingLineFinder.findCauseLines(target);

// 修正案
List<Integer> assignedLine = ValueChangingLineFinder.findBreakpointLines(target);
```

`resultIfAssigned(target, tracedValue.lineNumber)` は `extractStmt(class, lineNumber)` を呼ぶが、
`getStatementByLine` は `begin <= line && line <= end` でフィルタするため、
lineNumber が line B でも正しく line A-B のステートメントを見つけられる。

### 両方の修正が必要

1. `collectMutationRanges`: VariableDeclarator の全範囲を使用（findBreakpointLines の精度向上）
2. `valueChangedToActualLine`: findBreakpointLines を使用（マッチング範囲の拡大）

## 検証方法

1. マルチライン宣言のテストケースを `ProbeFixture.java` に追加
2. `javap -l -c` で実際のバイトコード行番号帰属を確認
3. Probe 実行で "Cause line not found" が解消されることを確認

## 関連

- `docs/plans/2026-02-03-probe-implementation-issues-plan.md` — 問題 4 として記録済み
