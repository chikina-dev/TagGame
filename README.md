# TagGame

Paper 1.21 鬼ごっこプラグイン（Kotlin）

- `/tag start` で開始
- 棒/雪玉で鬼移譲、雪玉10sクールダウン
- 共有タイマー120s（免疫中は停止、0で鬼脱落→タイマーリセット）
- 10sごとにコンパス更新
- 途中参加は観戦、途中退出は脱落扱い

## ビルド

```bash
./mvnw -q -DskipTests package || mvn -q -DskipTests package
```

生成物: `target/taggame-*.jar`

## サーバー配置
- `plugins/` にJAR配置
- Paper 1.21.10 で動作

