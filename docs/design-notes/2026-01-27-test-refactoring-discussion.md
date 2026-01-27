# CauseLineFinderTest / ValueChangingLineFinderTest リファクタリング議論

## 背景

`CauseLineFinderTest` と `ValueChangingLineFinderTest` のコード品質を改善するため、テストコードのレビューと改善点の議論を行った。

## 議論項目と結論

### 1. 【必須】CauseLineFinderTest に @AfterAll を追加して設定を復元する

**問題**: `@BeforeAll` で `PropertyLoader.setProjectConfig()` を変更しているが、`@AfterAll` で復元していなかった。

**結論**: 実装。`ValueChangingLineFinderTest` と同様に `@AfterAll` を追加。

### 2. 【推奨】絶対パスのハードコードを解消する

**問題**: `/Users/ezaki/IdeaProjects/FaultFinder/...` のような絶対パスがハードコードされていた。

**検討した案**:
- 案A: `Path.of("").toAbsolutePath()` でプロジェクトルートを取得
- 案B: システムプロパティ `user.dir` を使用
- 案C: クラスローダーのリソースパスを使用

**結論**: 案Aを採用。`PROJECT_ROOT.resolve()` で相対パスに変更。

### 3. 【推奨】CauseLineFinderTest の重複するテストパターンを整理する

**問題**: 各テストで `new CauseLineFinder()`、`new SuspiciousLocalVariable(...)`、アサーションパターンが重複していた。

**検討した案**:
- 案A: フィールド化 + ヘルパーメソッド（積極的）
- 案B: `CauseLineFinder` のみフィールド化（最小限）
- 案C: 現状維持

**結論**: 案Aを採用。以下のヘルパーを追加：
- `localVar(method, varName, actual)`
- `localVarWithCallee(caller, callee, varName, actual)`
- `assertAssignmentAt(result, expectedLine, message)`
- `assertArgumentFound(result, message)`

### 4. 【推奨】Fixture 行番号取得方式の統一を検討する

**現状**:
- `ValueChangingLineFinderTest`: マーカーコメント方式（`@DECL` など）
- `CauseLineFinderTest`: AST解析方式

**結論**: 現状維持。テストの目的に合った方式が既に選択されている。

### 5. 【推奨】ValueChangingLineFinderFixture の static + this 問題を確認する

**問題**: `public static int fieldAssign()` メソッド内で `this.f` を使用していた（Java として不正）。

**検討した案**:
- 案A: インスタンスメソッド化（フィールドも non-static に）
- 案B: `this` を削除
- 案C: 現状維持

**結論**: 案Aを採用。テスト実行で案Bが `ValueChangingLineFinder.matchesTarget()` の実装と互換性がないことが判明したため。

**発見した課題**: `ValueChangingLineFinder` は `this.f` 形式の FieldAccessExpr のみを検出し、`f` のような NameExpr 形式の static フィールドアクセスは検出しない。TODO コメントとして記録。

### 6. 【任意】アサーションメッセージの言語を統一する

**問題**: 日本語と英語が混在していた。

**結論**: 日本語に統一。プロジェクト全体が日本語主体のため。

### 7. 【任意】テストメソッド名の命名規則を統一する

**現状**:
- `CauseLineFinderTest`: `{パターン}_{説明}` 形式
- `ValueChangingLineFinderTest`: `{対象}_{期待結果}` 形式（BDD風）

**結論**: 現状維持。各テストの目的に合った命名規則が既に使われている。

### 8. 【構造】テストヘルパーの共有方針を決定する

**検討**: 共通ベースクラスや utility クラスの作成を検討。

**結論**: 現状維持。各テストで必要なヘルパーが異なり、共有のメリットが小さい。テストの独立性を優先。

## 変更したファイル

- `src/test/java/jisd/fl/core/domain/CauseLineFinderTest.java`
- `src/test/java/jisd/fl/core/domain/internal/ValueChangingLineFinderTest.java`
- `src/test/resources/fixtures/parse/src/java/jisd/fl/fixture/ValueChangingLineFinderFixture.java`
- `src/main/java/jisd/fl/core/domain/internal/ValueChangingLineFinder.java`（TODO追加）

## 今後の課題

- `ValueChangingLineFinder` で static フィールドへの代入（`f = 1` や `ClassName.f = 1` 形式）を検出できるようにする（TODO として記録済み）

## まとめ

8項目中4項目を実装、4項目を現状維持とした。主な改善点：
- テストの信頼性向上（設定の復元）
- 可搬性向上（絶対パス解消）
- 保守性向上（ヘルパーメソッド導入、重複削減）
- コード品質向上（Fixture の修正、メッセージ統一）
