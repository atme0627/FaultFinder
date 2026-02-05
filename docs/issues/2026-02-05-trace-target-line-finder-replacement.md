# JavaParserTraceTargetLineFinder を ValueChangingLineFinder で代替する

## 現状

`TargetVariableTracer.traceValuesOfTarget()` で BP 設定行を決定する際、
変数の種類によって異なるクラスを使用している：

```java
// TargetVariableTracer.java:27-33
List<Integer> canSetLines;
if (target instanceof SuspiciousLocalVariable localVariable) {
    canSetLines = JavaParserTraceTargetLineFinder.traceTargetLineNumbers(localVariable);
} else {
    canSetLines = ValueChangingLineFinder.findBreakpointLines(target);
}
```

- ローカル変数: `JavaParserTraceTargetLineFinder`
- フィールド: `ValueChangingLineFinder.findBreakpointLines`

## 2つのクラスの比較

### JavaParserTraceTargetLineFinder.traceTargetLineNumbers

```java
bs.findAll(SimpleName.class)
    .filter(sn -> sn.getIdentifier().endsWith(variable))
    .forEach(sn -> {
        for (int i = -2; i <= 2; i++) {
            canSet.add(sn.getBegin().get().line + i);
        }
    });
```

- 変数名に一致する **全 SimpleName**（読み取りを含む）を検索
- 各出現位置の **±2 行** をヒューリスティックに追加
- 結果として多数の不要な行が含まれる

### ValueChangingLineFinder.findBreakpointLines

```java
// 0-a) 宣言行
findLocalVariableDeclarationLine(...)

// 2) 代入行
assigns.filter(matchesTarget)  → LineRange(begin, end)

// 3) ++/-- 行
unaries.filter(matchesTarget)  → LineRange(begin, end)
```

- 変数が **変更される行のみ** を AST レベルで正確に特定
- 宣言行 + AssignExpr + UnaryExpr (++/--)
- マルチライン式は begin..end の全範囲をカバー（※宣言行は要修正、問題 4 参照）

## 代替すべき理由

### 1. 意味的な正確性

`TargetVariableTracer` の目的は **変数の値が変化する行** で BP を設定し、
変化後の値（post-state）を観測すること。

- `JavaParserTraceTargetLineFinder`: 読み取り行にも BP を設定 → **不要な BP が大量に発生**
- `ValueChangingLineFinder.findBreakpointLines`: 変更行のみに BP → **正確**

### 2. ±2 行ヒューリスティックの脆弱性

- 隣接行に無関係なコードがあれば **誤った BP** が設定される
- 3行以上にまたがる式では **カバーしきれない可能性**がある
- `ValueChangingLineFinder` は AST の range を使うため、行数に関係なく正確

### 3. コードの統一

フィールドは既に `ValueChangingLineFinder.findBreakpointLines` を使用している。
ローカル変数も同じクラスを使えば、`JavaParserTraceTargetLineFinder` を完全に削除できる。

### 4. テスト不在

`JavaParserTraceTargetLineFinder` にはテストがない。
`ValueChangingLineFinder` は `findCauseLines` / `findBreakpointLines` として
`CauseLineFinder` 経由で間接的にテストされている。

## 修正案

```java
// TargetVariableTracer.java — 修正後
List<Integer> canSetLines = ValueChangingLineFinder.findBreakpointLines(target);
```

分岐が不要になり、`JavaParserTraceTargetLineFinder` を削除可能。

## 前提条件

問題 4 の修正（`collectMutationRanges` で VariableDeclarator の全範囲を使用）が先に必要。
現状では `findBreakpointLines` もマルチライン宣言で begin 行しか返さないため。

## 影響範囲

- `TargetVariableTracer.java` — import と BP 行決定ロジックの変更
- `JavaParserTraceTargetLineFinder.java` — 削除
- `TargetVariableTracerTest.java` — テストが使用していなければ影響なし
- `docs/design-notes/` — 参照のみ（変更不要）

## 関連

- `docs/issues/2026-02-05-multiline-expression-cause-not-found.md` — 問題 4（前提条件）