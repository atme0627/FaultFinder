# JavaParserTraceTargetLineFinder の削除と ValueChangingLineFinder への統一

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

## 2つのクラスの比較

### JavaParserTraceTargetLineFinder

```java
bs.findAll(SimpleName.class)
    .filter(sn -> sn.getIdentifier().endsWith(variable))
    .forEach(sn -> {
        for (int i = -2; i <= 2; i++) {
            canSet.add(sn.getBegin().get().line + i);
        }
    });
```

- 変数名に一致する**全 SimpleName**（読み取りを含む）を検索
- 各出現位置の **±2 行** をヒューリスティックに追加
- 不要な行が大量に含まれる

### ValueChangingLineFinder

```java
// 宣言行
findLocalVariableDeclarationLine(...)

// 代入行
assigns.filter(matchesTarget) → LineRange(begin, end)

// ++/-- 行
unaries.filter(matchesTarget) → LineRange(begin, end)
```

- 変数が**変更される行のみ**を AST レベルで正確に特定
- マルチライン式は begin..end の全範囲をカバー

## 統一すべき理由

1. **意味的な正確性**: `TargetVariableTracer` の目的は変数の値が変化する行で BP を設定すること。読み取り行への BP は不要。
2. **±2 行ヒューリスティックの脆弱性**: 隣接行に無関係なコードがあれば誤った BP が設定される。
3. **コードの統一**: フィールドは既に `ValueChangingLineFinder` を使用。ローカル変数も統一すれば `JavaParserTraceTargetLineFinder` を削除できる。
4. **テスト不在**: `JavaParserTraceTargetLineFinder` にはテストがない。

## 修正内容

```java
// 変更後
List<Integer> canSetLines = JavaParserValueChangingLineFinder.findBreakpointLines(target);
```

`JavaParserTraceTargetLineFinder.java` を削除。

## パッケージ移動とリネーム

- `ValueChangingLineFinder` を `core.domain.internal` から `infra.javaparser` に移動
- クラス名を `JavaParserValueChangingLineFinder` に変更（パッケージ内の命名規約に統一）

## 副作用: BP 発火不安定問題

統一後に `ProbeTest` の scenario1/scenario2 が不安定化した。これは統一とは別の問題（JDWP CLE ポリシー）であり、CLE flush BP で解決済み。

詳細は `2026-02-05-valuechanginglinefinder-bp-instability.md` を参照。

## 関連コミット

- `bc5e630`: refactor: ValueChangingLineFinder を infra.javaparser パッケージに移動
- `3bf6eef`: refactor: ValueChangingLineFinder を JavaParserValueChangingLineFinder にリネーム
