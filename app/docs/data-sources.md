# 内置营养数据状态说明

## 1. 当前状态：待核验

`app/src/main/java/com/foodmoment/app/data/SeedFoods.kt` 中的 28 条食物，**数值为常见参考值，尚未逐条与 USDA FoodData Central 核对**。

因此：

- `fdcId = null`
- `dataType = "pending"`
- 界面上显示「待核验」标记（营养页与卡片）

这不是缺陷掩盖，而是把不确定性显式暴露出来：宁可标「待核验」，也不填一个看起来精确的假编号。

## 2. 取证步骤

对每一条：

1. 打开 https://fdc.nal.usda.gov ，用 `SeedFoods.kt` 里的英文 `name` 检索。
2. 优先选 `Foundation Foods`，其次 `SR Legacy`；复合菜才用 `FNDDS`。
3. 记录该条目的 `fdcId`，并把四项数值改成该条目的每 100g 值。
4. 把 `dataType` 改成 `foundation` / `sr_legacy` / `fndds`，标记自动消失。
5. 在 `docs/data-sources.md` 的核对表里打勾。

## 3. 核对表

| # | id | 中文名 | 英文名（检索用） | 数据集 | fdc_id | 已核对 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | f_rice_white | 白米饭（熟） | Rice, white, cooked | | | ☐ |
| 2 | f_rice_brown | 糙米饭（熟） | Rice, brown, cooked | | | ☐ |
| 3 | f_noodle | 面条（煮） | Noodles, cooked | | | ☐ |
| 4 | f_bun | 馒头 | Steamed bun | | | ☐ |
| 5 | f_bread_ww | 全麦面包 | Bread, whole wheat | | | ☐ |
| 6 | f_potato | 土豆（熟） | Potatoes, cooked | | | ☐ |
| 7 | f_sweetpotato | 红薯（蒸） | Sweet potato, cooked | | | ☐ |
| 8 | f_egg | 鸡蛋（煮） | Egg, whole, boiled | | | ☐ |
| 9 | f_chicken_breast | 鸡胸肉（熟） | Chicken breast, cooked | | | ☐ |
| 10 | f_pork_loin | 猪里脊（熟） | Pork loin, cooked | | | ☐ |
| 11 | f_beef_lean | 牛肉（瘦，熟） | Beef, lean, cooked | | | ☐ |
| 12 | f_salmon | 三文鱼（熟） | Salmon, cooked | | | ☐ |
| 13 | f_tofu | 豆腐（北豆腐） | Tofu, firm | | | ☐ |
| 14 | f_milk | 牛奶（全脂） | Milk, whole | | | ☐ |
| 15 | f_yogurt | 酸奶（原味） | Yogurt, plain | | | ☐ |
| 16 | f_soymilk | 豆浆（无糖） | Soymilk, unsweetened | | | ☐ |
| 17 | f_cola | 可乐 | Carbonated beverage, cola | | | ☐ |
| 18 | f_broccoli | 西兰花（熟） | Broccoli, cooked | | | ☐ |
| 19 | f_spinach | 菠菜（熟） | Spinach, cooked | | | ☐ |
| 20 | f_tomato | 番茄 | Tomato, raw | | | ☐ |
| 21 | f_apple | 苹果 | Apple, raw | | | ☐ |
| 22 | f_banana | 香蕉 | Banana, raw | | | ☐ |
| 23 | f_oil | 大豆油 | Soybean oil | | | ☐ |
| 24 | f_peanut | 花生（炒） | Peanuts, roasted | | | ☐ |
| 25 | f_fried_rice | 蛋炒饭 | Fried rice, with egg | FNDDS | | ☐ |
| 26 | f_dumpling | 猪肉饺子（煮） | Dumpling, pork, boiled | FNDDS | | ☐ |
| 27 | f_baozi | 肉包子 | Steamed bun, meat filling | FNDDS | | ☐ |
| 28 | f_tomato_rice | 番茄鸡蛋盖饭 | Rice with tomato and egg | FNDDS | | ☐ |

## 4. 口径约定

- 全部数值为**每 100g 可食部**。
- `edibleRatio`（可食部比例）缺失时按 1.0 计，并在界面标注。
- 生熟状态写在 `state` 字段：`raw` / `cooked`。
- **中文名是本项目译注，不是 FDC 原文**；FDC 英文名保留在 `name` 字段便于核对。
- FNDDS 条目（25–28）是调查用复合食品，数值来自典型配方，**不代表任何店家的实际做法**，界面必须显示「典型配方估算」。
- 中式份量（一碗、一两）由本项目标注为估算预设，不作为 FDC 数据。

## 5. 署名

> U.S. Department of Agriculture, Agricultural Research Service, FoodData Central.

FDC 数据为美国政府作品，可自由使用。本项目不使用 USDA 徽标，也不暗示 USDA 对本项目的认可。

## 6. 覆盖缺口（已知）

`Foundation Foods` 与 `SR Legacy` 中几乎不含馒头、包子、豆浆、油条等中式主食，因此：

- 查得到就收录，查不到**不强行找近似条目填充**；
- 由用户在营养页「新建自定义食物」录入，`is_custom = 1`，与内置表分开显示；
- 自定义食物也会随导出/导入一起迁移。
