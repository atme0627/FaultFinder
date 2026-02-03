# Probe ベンチマーク結果報告

## 概要

Probe の探索性能を評価するため、5つの異なる「極端さ」の軸でベンチマークを作成・実行しました。

## ベンチマーク結果サマリ

| # | テスト名 | 極端さの種類 | 実行時間 | ツリーサイズ |
|---|---------|-------------|----------|-------------|
| 1 | bench_depth_extreme | 深さ（20段ネスト） | 20,487 ms | 41 nodes |
| 2 | bench_repetition_extreme | 繰り返し（100回ループ） | 212,878 ms | 302 nodes |
| 3 | bench_branch_extreme | 分岐（2^10 = 1024 nodes） | 594,267 ms | 2,114 nodes |
| 4 | bench_polymorphism_extreme | 動的解決（50種類の実装） | 42,485 ms | 101 nodes |
| 5 | bench_realistic_multi_class | 現実的ケース（メソッドチェーン） | 8,218 ms | 19 nodes |

## 各ベンチマークの詳細

### 1. 深さ極端（Depth）

**目的**: call stack の深さへの耐性を測定

**フィクスチャ**:
```java
int x = d1(d2(d3(d4(d5(d6(d7(d8(d9(d10(d11(d12(d13(d14(d15(d16(d17(d18(d19(d20(1))))))))))))))))))));
```

**結果**:
- 実行時間: 20,487 ms（約20秒）
- ツリーサイズ: 41 nodes

**分析**: 深さ20段のネストでも比較的高速に処理できる。

---

### 2. 繰り返し極端（Repetition）

**目的**: 同一メソッドの重複呼び出しへの耐性を測定

**フィクスチャ**:
```java
int sum = 0;
for (int i = 1; i <= 100; i++) {
    sum += increment(i);
}
```

**結果**:
- 実行時間: 212,878 ms（約3.5分）
- ツリーサイズ: 302 nodes

**分析**: ループ内で同一メソッドを100回呼び出すと、約3.5分かかる。JDI のブレークポイント処理がボトルネックと考えられる。

**注**: 当初は再帰で実装予定だったが、JDI の `DuplicateRequestException`（同一スレッドで複数のステップリクエストを作成できない）により、ループパターンに変更。

---

### 3. 分岐極端（Branch）

**目的**: 指数的な探索空間への耐性を測定

**フィクスチャ**:
```java
int x = branch10(1);
// branch10(n) = branch9(n) + branch9(n+1)
// branch9(n) = branch8(n) + branch8(n+1)
// ...
// → 2^10 = 1024 の leaf node
```

**結果**:
- 実行時間: 594,267 ms（約10分）
- ツリーサイズ: 2,114 nodes

**分析**: 最も時間がかかるベンチマーク。探索空間が指数的に増加するため、実行時間も大幅に増加する。

---

### 4. 動的解決極端（Polymorphism）

**目的**: ポリモーフィズム解決コストを測定

**フィクスチャ**:
```java
Worker[] workers = createWorkers();  // 50種類の実装
int total = 0;
for (Worker w : workers) {
    total += w.work();  // 異なる実装が呼ばれる
}
```

**結果**:
- 実行時間: 42,485 ms（約42秒）
- ツリーサイズ: 101 nodes

**分析**: 50種類の異なる実装クラスをループで呼び出しても、比較的高速に処理できる。JDI のポリモーフィズム解決は効率的。

---

### 5. 現実的ケース（Realistic）

**目的**: 実際のアプリケーションに近いメソッドチェーンを測定

**フィクスチャ**:
```java
int result = processOrder(100, 5);
// processOrder → validate → calculate → formatResult
// 各メソッドが複数の下位メソッドを呼び出す
```

**結果**:
- 実行時間: 8,218 ms（約8秒）
- ツリーサイズ: 19 nodes

**分析**: 実際のアプリケーションで発生するメソッドチェーンは、十分に高速に処理できる。

---

## 発見された問題

ベンチマーク作成中に以下の問題を発見し、`probe-implementation-issues-plan.md` に追記しました：

### 問題 4: 複数行にまたがる式の解析で原因行が見つからない

```java
int x = d1(d2(d3(
        d4(d5(d6(1))))));  // ← 2行にまたがるとエラー
```

**回避策**: 1行に収める

### 問題 5: 内部クラス（static nested class）のメソッド追跡でエラー

```java
static class OrderService {
    String processOrder(...) { ... }
}
// ↑ 内部クラスのメソッドを追跡できない
```

**回避策**: 同一クラス内のメソッドで実装

---

## 結論

1. **深さ**と**ポリモーフィズム**は比較的高速に処理できる
2. **繰り返し**と**分岐**は時間がかかる（JDI のブレークポイント処理がボトルネック）
3. **現実的なケース**は十分に高速（8秒程度）

高速化が必要な場合、以下のアプローチが考えられる：
- ブレークポイント数の削減（探索の枝刈り）
- キャッシュの活用（同一メソッドの結果を再利用）
- 並列処理の導入

---

## ファイル

- フィクスチャ: `src/test/resources/fixtures/exec/src/main/java/jisd/fixture/ProbeBenchmarkFixture.java`
- テスト: `src/test/java/jisd/fl/benchmark/ProbeBenchmarkTest.java`

---

## 実行方法

```bash
./gradlew test --tests "jisd.fl.benchmark.ProbeBenchmarkTest"
```

**注**: 全テスト実行には約15分かかる
