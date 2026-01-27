# CauseLineFinder のリファクタリングとコード品質改善

## 概要

CauseLineFinder クラスに対して、依存性注入（DI）の導入、コード品質の改善、ドキュメントの全面刷新を実施した。

**日付**: 2026-01-27
**対象ファイル**: `src/main/java/jisd/fl/core/domain/CauseLineFinder.java`
**関連コミット**:
- `62eaf20`: DI 導入 + post-state バグ修正
- `2e9546d`: コード品質改善（Optional、ロガー、コメント整理）
- `394b03e`: コメント・ドキュメント全面改善

**関連 design-note**:
- `2026-01-27-cause-line-finder-post-state-adaptation.md`

---

## 背景

### 初期レビューでの指摘事項

CauseLineFinder.java のコードレビューを実施した結果、以下の改善点が特定された：

#### 必須（優先度: 高）
- ✅ **ValueChangingLineFinder.find() → findCauseLines() への変更**
  - 状況: 既にユーザーが修正済み
  - 理由: `find()` は範囲展開版、`findCauseLines()` は代表行のみを返す

#### 推奨（優先度: 中）
1. **依存性注入（DI）の欠如**
   - コンストラクタで具体的な実装に依存
   - テストでモックを使用できない
   - 拡張性が低い

2. **Optional の使い方に古いイディオムが混在**
   - `isEmpty()` の使用（`isPresent()` が標準的）
   - 不要な if-else による Optional の再ラップ

3. **エラーハンドリング**
   - `System.err.println` の使用（ロガーが推奨）

4. **コメントアウトコードの整理**
   - 古いコメントアウトコードが残存

#### 任意（優先度: 低）
- Javadoc の @param, @return の欠落
- コメントスタイルの不統一
- 冗長な変数

---

## 実施した改善

### 1. 依存性注入（DI）の導入

#### 変更内容

```java
// Before
public class CauseLineFinder {
    SuspiciousExpressionFactory factory;
    SuspiciousArgumentsSearcher suspiciousArgumentsSearcher;
    TargetVariableTracer tracer;

    public CauseLineFinder() {
        this.factory = new JavaParserSuspiciousExpressionFactory();
        this.suspiciousArgumentsSearcher = new JDISuspiciousArgumentsSearcher();
        this.tracer = new TargetVariableTracer();
    }
}

// After
public class CauseLineFinder {
    private final SuspiciousExpressionFactory factory;
    private final SuspiciousArgumentsSearcher suspiciousArgumentsSearcher;
    private final TargetVariableTracer tracer;

    // DI用コンストラクタ
    public CauseLineFinder(
            SuspiciousExpressionFactory factory,
            SuspiciousArgumentsSearcher suspiciousArgumentsSearcher,
            TargetVariableTracer tracer) {
        this.factory = factory;
        this.suspiciousArgumentsSearcher = suspiciousArgumentsSearcher;
        this.tracer = tracer;
    }

    // デフォルトコンストラクタ（後方互換性）
    public CauseLineFinder() {
        this(
            new JavaParserSuspiciousExpressionFactory(),
            new JDISuspiciousArgumentsSearcher(),
            new TargetVariableTracer()
        );
    }
}
```

#### 判断理由

**なぜ DI を導入したのか**:
- **テスタビリティ**: テスト時にモック実装を注入可能
- **拡張性**: 異なる実装を容易に差し替え可能
- **単一責任原則**: 依存の生成責任を分離
- **不変性**: `final` により意図しない変更を防止

**なぜデフォルトコンストラクタを残したのか**:
- 後方互換性の維持
- 既存コードへの影響を最小化
- 一般的な使用ケースでのシンプルさ

**代替案と選択理由**:
- 代替案1: デフォルトコンストラクタのみ → テスト性が低い（不採用）
- 代替案2: DI のみで後方互換性なし → 既存コードの修正が必要（不採用）
- **採用**: DI + デフォルトコンストラクタ → バランスが良い

---

### 2. Optional の使い方の統一

#### 変更内容

```java
// Before
Optional<SuspiciousArgument> result = suspiciousArgumentsSearcher.searchSuspiciousArgument(...);
if(result.isEmpty()){
    return Optional.empty();
} else {
    return Optional.of(result.get());
}

// After
return suspiciousArgumentsSearcher.searchSuspiciousArgument(...)
        .map(arg -> arg);
```

#### 判断理由

**なぜ Stream API の map() を使うのか**:
- 宣言的で意図が明確
- 冗長な if-else を削減
- Optional の標準的なイディオム

**キャストの削減**:
- IDEの診断により、キャストが冗長であることが判明
- `.map(arg -> arg)` で型推論により適切に変換される

---

### 3. エラーハンドリングのロガー化

#### 変更内容

```java
// Before
System.err.println("Cannot find probe line of field. [FIELD NAME] " + target.variableName());

// After
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

private static final Logger logger = LoggerFactory.getLogger(CauseLineFinder.class);
logger.warn("Cannot find probe line of field. [FIELD NAME] {}", target.variableName());
```

#### 判断理由

**なぜロガーを導入したのか**:
- **ログレベル制御**: 本番環境でログレベルを動的に変更可能
- **パフォーマンス**: プレースホルダー形式で不要な文字列連結を回避
- **監視性**: ログ集約ツールとの連携が容易
- **将来の移行**: プロジェクト全体のロガー移行の先駆け

**なぜ部分的な導入なのか**:
- プロジェクト全体で 89 箇所 `System.out/err` が使用されている
- 全体の方針が決まるまで、このクラスだけ先行導入
- 将来的な移行の参考実装として機能

**なぜ logger.warn() を使うのか**:
- Field 変数は現状サポート外（制約事項）
- エラーではなく警告として扱うのが適切
- 処理は継続される（Optional.empty() を返す）

---

### 4. コメントアウトコードの整理

#### 変更内容

```java
// Before
/* 3. throw内などブレークポイントが置けない行で、代入が行われているパターン */
//            System.err.println("There is no value which same to actual.");
//            return Optional.empty();

// After
// TODO: Pattern 3 の実装
// throw 内などブレークポイントが置けない行で、代入が行われているパターンへの対応
// 現在は未実装。静的解析の拡張が必要。
```

#### 判断理由

**なぜ TODO コメントに変更したのか**:
- 未実装部分が明確になる
- IDE の TODO リストに表示される
- 実装意図と制約が明示される

**なぜ削除しなかったのか**:
- Pattern 3 の実装は将来的に必要になる可能性がある
- 設計時の検討内容として記録する価値がある

---

### 5. Javadoc の充実

#### 変更内容

すべてのメソッドに対して：
- @param, @return タグの追加
- 説明の構造化（箇条書き、コード例）
- post-state 観測の説明を追加
- Pattern 番号との対応を明確化

#### 例：find() メソッド

```java
/**
 * 与えられた Suspicious Variable に対して、その直接的な原因となる Expression を探索する。
 * <p>
 * 変数が actual 値を取るようになった原因を以下のパターンで特定する：
 * <ul>
 *   <li>Pattern 1: 代入による値の変更（初期化を含む）</li>
 *   <li>Pattern 2: 引数として渡された値（メソッド呼び出し元）</li>
 * </ul>
 *
 * @param target 調査対象の suspicious variable
 * @return 原因となる suspicious expression。見つからない場合は empty
 */
public Optional<SuspiciousExpression> find(SuspiciousVariable target) { ... }
```

#### 判断理由

**なぜ Javadoc を充実させたのか**:
- IDE での補完時に詳細な情報が表示される
- API ドキュメント生成時に完全な情報が含まれる
- 新しい開発者のオンボーディングが容易になる

**なぜ構造化したのか**:
- 箇条書きで読みやすくなる
- `<pre>` タグでコード例が適切にフォーマットされる
- Pattern 番号でメソッド間の関連性が明確になる

---

### 6. インラインコメントの整理

#### 変更内容

```java
// Before
/* 1a. すでに定義されていた変数に代入が行われたパターン */
//代入の実行後にactualの値に変化している行の特定(ない場合あり)

// After
// Pattern 1: 代入による値の変更
```

#### 判断理由

**なぜコメントスタイルを統一したのか**:
- `/* */` と `//` の混在は可読性を低下させる
- `//` は行単位のコメントとして一般的
- Javadoc と区別が明確になる

**なぜ簡潔にしたのか**:
- 冗長なコメントは保守コストを増加させる
- コードから明らかなことはコメント不要
- Pattern 番号で Javadoc との対応が明確

---

### 7. 冗長な変数の削除

#### 変更内容

```java
// Before
TracedValue causeLine = changeToActualLine.get();
int causeLineNumber = causeLine.lineNumber;
return Optional.of(resultIfAssigned(target, causeLineNumber));

// After
return Optional.of(resultIfAssigned(target, changeToActualLine.get().lineNumber));
```

#### 判断理由

**なぜ中間変数を削除したのか**:
- 1回しか使用されない変数は冗長
- メソッドチェーンで意図が明確
- IDEの診断で冗長性が指摘されていた

---

## テスト結果

### 全 3 段階でのテスト

| 段階 | コミット | テスト結果 | 内容 |
|------|---------|----------|------|
| 1 | 62eaf20 | ✅ 全8テスト成功 | DI導入 + post-state バグ修正 |
| 2 | 2e9546d | ✅ 全8テスト成功 | コード品質改善 |
| 3 | 394b03e | ✅ 全8テスト成功 | ドキュメント改善 |

### テストケース（CauseLineFinderTest）

- Pattern 1a: 既存変数への代入（3テスト）
- Pattern 1b: 宣言時の初期化（2テスト）
- Pattern 2: 引数由来（3テスト）

**計 8 テスト全て成功**

---

## 技術的な議論

### 議論1: DI コンテナを使うべきか？

**質問**: Spring や Guice などの DI コンテナを使うべきか？

**回答**: 現時点では不要
- プロジェクトで DI コンテナが使用されていない
- コンストラクタインジェクションで十分
- 依存関係の数が少ない（3つのみ）
- 将来的に必要になれば移行可能

### 議論2: ロガーをファクトリー経由で注入すべきか？

**質問**: Logger も DI で注入すべきか？

**回答**: 不要
- Logger は通常クラスごとに static で定義される
- ログ出力のテストは一般的に不要
- SLF4J の設計思想に従う

### 議論3: Optional.map(arg -> arg) は意味があるのか？

**質問**: `.map(arg -> arg)` は何もしていないのでは？

**回答**: 型変換のために必要
- `searchSuspiciousArgument()` は `Optional<SuspiciousArgument>` を返す
- メソッドの戻り値は `Optional<SuspiciousExpression>` が必要
- `SuspiciousArgument extends SuspiciousExpression` なので、型推論により適切に変換される
- IDE の診断により、キャストが冗長であることが確認された

---

## 今後の課題

### 1. Field Pattern のサポート

**現状**: Field 変数の cause line 特定は未対応
```java
if (target instanceof SuspiciousFieldVariable) {
    logger.warn("Cannot find probe line of field. [FIELD NAME] {}", target.variableName());
    return Optional.empty();
}
```

**課題**:
- Field の変更箇所を動的に観測することが困難
- 静的解析とランタイム観測の組み合わせが必要

**対応方針**:
- Fixture には 6 パターンのテストケースがある
- 将来的にサポートする価値はある
- 優先度は中〜低

### 2. Pattern 3 の実装

**現状**: throw 内などブレークポイントが置けない行は未対応

**課題**:
- ブレークポイントが設定できない行の観測方法
- JDI の制約を回避する方法

**対応方針**:
- 静的解析の拡張が必要
- 実際の需要を見てから実装を検討

### 3. プロジェクト全体のロガー移行

**現状**: CauseLineFinder のみロガーを使用

**今後**:
- プロジェクト全体で 89 箇所の `System.out/err` がある
- 段階的に移行するか、一括で移行するか検討が必要
- ログ設定ファイル（logback.xml）の整備

### 4. DI の拡張

**現状**: CauseLineFinder のみ DI 対応

**今後**:
- 他のドメインクラスへの DI 導入
- 一貫した設計パターンの適用
- テスタビリティの向上

---

## まとめ

### 変更のポイント

1. **DI 導入**: テスタビリティと拡張性の向上
2. **コード品質**: Optional、ロガー、コメントの改善
3. **ドキュメント**: 完全な Javadoc と整理されたコメント

### メリット

- **テスタビリティ**: モックを使ったユニットテストが可能に
- **保守性**: コードとドキュメントの一貫性が向上
- **可読性**: 構造化された説明と統一されたスタイル
- **拡張性**: 異なる実装への差し替えが容易に
- **後方互換性**: 既存コードへの影響なし

### 影響範囲

- **変更ファイル**: CauseLineFinder.java のみ
- **テスト**: 全 8 テスト成功（デグレードなし）
- **既存コード**: デフォルトコンストラクタにより影響なし

### 学び

- **段階的リファクタリング**: 3 段階に分けることで変更の追跡が容易
- **テスト駆動**: 各段階でテストを実行し、安全性を確保
- **ドキュメント先行**: 設計意図を明確にすることでレビューが効率的
- **部分的導入**: プロジェクト全体への影響を最小化しつつ改善を進める

---

## 参考資料

### 関連する design-note
- `2026-01-27-cause-line-finder-post-state-adaptation.md`: Post-state 観測への対応

### 関連する実装
- `ValueChangingLineFinder.java`: 値が変化する行の静的解析
- `TargetVariableTracer.java`: 変数の値の動的観測（post-state）
- `SuspiciousExpressionFactory.java`: Suspicious expression の生成
- `SuspiciousArgumentsSearcher.java`: Suspicious argument の探索

### コミット履歴
```
394b03e docs: CauseLineFinder のコメントとドキュメントを全面改善
2e9546d refactor: CauseLineFinder のコード品質を改善
62eaf20 refactor: CauseLineFinder に依存性注入（DI）を導入
```
