# 应用图标

食刻 AI 的启动图标，取自网页版 `food-ai-agent-1` 的品牌标识。

## 来源

| 元素 | 出处 |
|---|---|
| 图形 | `lucide-vue-next` v0.468.0 的 `Sprout`（嫩芽），ISC License |
| 底色 | `static/src/style.css` 中的 `--green: #1f5138` |
| 圆角 | `.brand-mark` 的 `border-radius: 14px / 44px` ≈ 31.8% |
| 描边 | 白色 `#fff`，`stroke-width: 1.8` |

`Sprout` 的原始路径（24×24 坐标系）：

```js
["path", { d: "M7 20h10" }],
["path", { d: "M10 20c5.5-2.5.8-6.4 3-10" }],
["path", { d: "M9.5 9.4c1.1.8 1.8 2.2 2.3 3.7-2 .4-3.5.4-4.8-.3-1.2-.6-2.3-1.9-3-4.2 2.8-.5 4.4 0 5.5.8z" }],
["path", { d: "M14.1 6a7 7 0 0 0-1.1 4c1.9-.1 3.3-.6 4.3-1.4 1-1 1.6-2.3 1.7-4.6-2.7.1-4 1-4.9 2z" }]
```

## 尺寸

嫩芽刻意做小，四周留足品牌绿：

| 指标 | 数值 |
|---|---|
| 嫩芽宽 / 绿色方块 | **39.7%** |
| 嫩芽高 / 绿色方块 | **42.1%** |
| 嫩芽外接框面积 / 绿方 | **16.7%** |

`icon.svg` 用 `translate(26.6 26.6) scale(2.28)`：lucide 图形可视范围是 4..20（16 单位），
放大后占 108 画布的 35.8..72.2。

> 早期版本用 `scale(2.5)`~`scale(3.75)`，嫩芽占绿方 62% 以上，视觉上过于拥挤，已调小。

## 文件

- `icon.svg` — 矢量源文件，108×108 画布，圆角方块内缩 6px（96×96），嫩芽约 36.5×36.5 居中
- `icon-512.png` / `icon-1024.png` — 从 `icon.svg` 渲染的高分辨率位图

## 生成 Android 资源

`icon.svg` 是给设计和预览用的完整图标（含深绿底）。Android 侧拆成两套：

1. **自适应图标**（Android 8.0+，`mipmap-anydpi-v26`）
   - 背景：纯色 `#1f5138`（`drawable/ic_launcher_background.xml`）
   - 前景：白色嫩芽，画在 108×108 画布的中央安全区内（`drawable/ic_launcher_foreground.xml`）
   - 系统会自动裁切，所以前景不能带圆角底
2. **传统位图图标**（Android 7.x 及以下，`mipmap-*dpi`）
   - 直接用完整圆角图标缩放成 48/72/96/144/192 px

重新生成位图：

```powershell
magick -background none -density 1200 branding/icon.svg -resize 512x512 branding/icon-512.png
```

> 注意：ImageMagick 内置的 SVG 解析器不接受 XML 注释，所以 `icon.svg` 刻意不带注释。
