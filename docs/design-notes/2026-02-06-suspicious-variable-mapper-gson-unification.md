# SuspiciousVariableMapper の Gson 統一と Field 対応

## 背景

`SuspiciousVariableMapper` で JSON ライブラリが混在していた：
- `toJson()`: Gson を使用
- `fromJson()`: org.json を使用

また、`SuspiciousLocalVariable` のみ対応しており、`SuspiciousFieldVariable` は未対応だった。

## 実施した改善

### 1. Gson への統一
- org.json 依存を削除
- `private static final Gson GSON` でインスタンスを再利用
- `JsonParser.parseString()` / `JsonObject` / `JsonArray` を使用

### 2. type フィールドによる Local/Field 区別
JSON の末尾に `type` フィールドを追加：
```json
{
  "failedTest": "...",
  "locateMethod": "...",  // local の場合
  "locateClass": "...",   // field の場合
  "variableName": "nums[2]",
  "actualValue": "13",
  "isPrimitive": true,
  "type": "local"  // または "field"
}
```

### 3. 設計判断

| 検討事項 | 決定 | 理由 |
|----------|------|------|
| `variableName` の形式 | `nums[2]` 維持 | 目視確認用 |
| `arrayNth` フィールド | JSON に含めない | 要素数削減、パースで復元 |
| 後方互換 | なし | `type` フィールドは必須 |
| `type` の位置 | 末尾 | 主要な情報を先に表示 |

### 4. 変更ファイル

- `src/main/java/jisd/fl/mapper/SuspiciousVariableMapper.java`
- `src/test/java/jisd/fl/mapper/SuspiciousVariableMapperTest.java`（リネーム）
- `src/main/java/experiment/setUp/doProbe.java`（呼び出し側の型修正）

## テスト結果

```
./gradlew test --tests "jisd.fl.mapper.*" --rerun
BUILD SUCCESSFUL
```

## まとめ

- JSON ライブラリを Gson に統一し、保守性が向上
- `type` フィールドで Local/Field を区別可能に
- `variableName[n]` 形式は目視確認用に維持
