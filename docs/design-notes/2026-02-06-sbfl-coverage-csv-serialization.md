# SBFL カバレッジデータの CSV 永続化機能

## 背景

計測した SBFL カバレッジを CSV ファイルに永続化・復元する機能が必要となった。
復元したカバレッジは `FaultFinder` でランキング生成に使用可能にすることで、
カバレッジ計測と分析を分離できるようになる。

## 技術的な議論

### 設計方針: 共通インターフェースの導入

`FaultFinder` が JaCoCo からの計測データと CSV から復元したデータの両方を
統一的に扱えるよう、`SbflCoverageProvider` インターフェースを導入した。

```java
public interface SbflCoverageProvider {
    Stream<LineCoverageEntry> lineCoverageEntries(boolean hideZeroElements);
    Stream<MethodCoverageEntry> methodCoverageEntries(boolean hideZeroElements);
    Stream<ClassCoverageEntry> classCoverageEntries();
}
```

### CSV フォーマット設計

RFC 4180 準拠の CSV 形式を採用:

```csv
# FaultFinder SBFL Coverage v1
# totalPass=5,totalFail=2
"com.example.Foo#bar(int, String)",12,3,1
```

- **ヘッダー**: バージョン識別用
- **メタデータ**: totalPass/totalFail（NP/NF の導出に必要）
- **データ行**: メソッドFQN, 行番号, EP, EF
- NP/NF は `totalPass - EP`, `totalFail - EF` で導出（保存不要）

### RestoredSbflCoverage の集約ロジック

メソッド/クラスレベルのカバレッジは行レベルから導出:
- EP/EF: 各行の最大値を採用（いずれかの行が実行されれば実行とみなす）
- NP/NF: 各行の最小値を採用

## 実施した変更

### 新規ファイル

| ファイル | 役割 |
|---------|------|
| `SbflCoverageProvider.java` | 共通インターフェース |
| `RestoredSbflCoverage.java` | CSV から復元したカバレッジを保持 |
| `SbflCoverageSerializer.java` | CSV 入出力ユーティリティ |
| `SbflCoverageSerializerTest.java` | ユニットテスト（13ケース） |

### 変更ファイル

| ファイル | 変更内容 |
|---------|---------|
| `ProjectSbflCoverage.java` | `implements SbflCoverageProvider` を追加 |
| `FaultFinder.java` | `SbflCoverageProvider` を受け取るコンストラクタを追加 |

## テスト結果

```
./gradlew test --tests "jisd.fl.presenter.SbflCoverageSerializerTest" --rerun
BUILD SUCCESSFUL
```

13テストケース全て成功:
- Writer テスト（ヘッダー、メタデータ、データ行）
- Reader テスト（パース、エラーケース）
- Round-trip テスト
- hideZeroElements フィルタリング
- メソッド/クラスレベルの集約

## 使用例

```java
// カバレッジを CSV に保存
SbflCoverageSerializer.write(coverage, Path.of("coverage.csv"));

// CSV からカバレッジを復元
RestoredSbflCoverage restored = SbflCoverageSerializer.read(Path.of("coverage.csv"));

// 復元したカバレッジからランキング生成
FaultFinder ff = new FaultFinder(restored);
ff.printRanking();
```

## 今後の課題

- 大規模データでのパフォーマンス検証
- 圧縮オプションの検討（gzip 等）
- CLI からの直接利用サポート

## まとめ

- `SbflCoverageProvider` インターフェースにより、カバレッジソースの抽象化を実現
- CSV フォーマットは RFC 4180 準拠でシンプルかつ可読性を維持
- 既存コードへの影響を最小限に抑えつつ、新機能を追加
