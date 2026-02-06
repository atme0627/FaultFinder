# マルチライン式で原因行が見つからない問題の修正

## 背景

複数行にまたがる式で `[Probe For STATEMENT] Cause line not found.` エラーが発生していた。

```java
int x = d1(d2(d3(          // line A
        d4(d5(d6(1))))));   // line B
```

1行に収めると動作するが、マルチラインでは失敗する。

## 原因

`ValueChangingLineFinder.collectMutationRanges()` で `VariableDeclarator` の宣言行が begin 行のみを使用していた。

```java
// 修正前
for (int ln : findLocalVariableDeclarationLine(...)) {
    ranges.add(new LineRange(ln, ln));  // begin 行のみ
}
```

javac がバイトコードを line B に帰属させた場合、BP は line B で発火するが、`findCauseLines()` は [A] のみを返すためマッチ失敗。

## 修正内容

`VariableDeclarator` の全範囲を使用するように変更:

```java
// 修正後
node.findAll(VariableDeclarator.class).stream()
    .filter(vd -> vd.getNameAsString().equals(v.variableName()))
    .forEach(vd -> vd.getRange().ifPresent(r ->
        ranges.add(new LineRange(r.begin.line, r.end.line))));
```

これにより:
- `findCauseLines()`: begin 行のみを返す（原因行の特定用）
- `findBreakpointLines()`: begin〜end の全行を返す（BP 設定用）

## テスト

### 単体テスト（JavaParserValueChangingLineFinderTest）

- `multiLineAssignBpLines_includes_begin_to_end_range`: マルチライン代入の BP 行
- `multiLineDeclBpLines_includes_begin_to_end_range`: マルチライン宣言の BP 行

### E2E テスト（ProbeTest シナリオ 5）

- `scenario5_multiline_declaration`: 複数行にまたがる宣言の因果追跡
- `scenario5_multiline_assignment`: 複数行にまたがる代入の因果追跡

## 関連コミット

- `190451b`: fix: マルチライン宣言で原因行が見つからない問題を修正
- `a470931`: test: マルチライン式のE2Eテストを ProbeTest に追加
- `a3bd9f8`: fix: scenario5_multiline_assignment を木構造マッチングに修正
