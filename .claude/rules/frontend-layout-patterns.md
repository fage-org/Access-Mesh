# Frontend 布局规范（pure-admin-thin 集成）

> 本文档记录 pure-admin-thin 模板下的布局模式、常见陷阱和推荐做法。

## 1. DOM 层级认知

**在动手写 CSS 前，必须先在浏览器中确认实际 DOM 结构。** pure-admin-thin 的 layout 在不同模式下渲染结构不同。

### fixedHeader 模式

```
<section class="app-main" style="height:100vh; padding-top:81px">
  <el-scrollbar>                              ← 主滚动容器
    <div class="el-scrollbar__wrap">
      <div class="el-scrollbar__view" style="display:flex; flex-direction:column">
        <div class="grow">                    ← 内容区（无 flex:1）
          <div class="your-page main-content"> ← main-content 由 layout 添加
            ...
          </div>
        </div>
        <LayFooter />                          ← 页脚在 scrollbar 内部
      </div>
    </div>
  </el-scrollbar>
</section>
```

### 非 fixedHeader 模式

```
<section class="app-main-nofixed-header" style="display:flex; flex-direction:column; padding-top:81px; min-height:calc(100vh-86px)">
  <div class="grow" style="flex:1">           ← flex:1 分配剩余高度
    <div class="your-page main-content">       ← 可用高度 = 100vh - 110px - margin
      ...
    </div>
  </div>
  <LayFooter />                                ← section 直接子元素
</section>
```

## 2. `class="main-content"` 的关键认知

**layout 通过 `<component :is="Comp" class="main-content" />` 把 `main-content` 直接加在我们根元素上，不是外层包裹。**

这意味着：
- 我们的根元素同时拥有 `user-page`（自己写的）和 `main-content`（layout 加的）
- layout 的 scoped CSS `.main-content[data-v-x] { margin: 24px }` 作用在同一元素上
- `:has()` 选择器无法匹配——因为没有父子关系

```
正确理解：
  <div class="user-page main-content">    ← 两个 class 在同一元素

错误理解：
  <div class="main-content">              ← 不存在这种包裹
    <div class="user-page">
```

## 3. 覆写 layout scoped 样式（不依赖 `!important`）

### 特异性阶梯

| 方法 | 选择器示例 | 特异性 | 建议 |
|------|-----------|--------|------|
| ❌ 单 class | `.main-content` | 0,1,0 | 不敌 scoped |
| ❌ `:has()` | `.main-content:has(.user-page)` | 0,2,0 | 同元素无效 |
| ✅ 双 class 组合 | `.user-page.main-content` | 0,2,0 | 同特异性，靠顺序赢（需验证） |
| ✅ 加 tag 提权 | `div.user-page.main-content` | 0,2,1 | **推荐**，确定性最高 |
| ⚠️ `!important` | `.user-page.main-content` | — | 万不得已 |

**规则**：用 `tag + 双 class` 选择器（0,2,1）即可覆盖 layout 的 scoped `class + attr`（0,2,0），无需 `!important`。

```css
/* unscoped <style> — 特异性 0,2,1 覆盖 layout 的 0,2,0 */
div.user-page.main-content {
  margin: 12px;
}
```

## 4. 页面整体布局：CSS Grid + Flex 分层

```
┌─ Grid（页面层）────────── 分配列宽，统一行高 ──┐
│ [树面板 200px] [表格区 1fr] [角色面板 360px]   │
│  ├─ Flex(树)     ├─ Flex(表格)                 │
│  │  ├─ 搜索(auto)│  ├─ 搜索栏(auto)            │
│  │  └─ 树(flex:1)│  └─ 表格区(flex:1)          │
│  │               │      └─ pure-table adaptive │
└────────────────────────────────────────────────┘
```

**规则**：
- **页面层**用 `display: grid`，列宽精确控制，子元素天然等高
- **面板内部**用 `display: flex; flex-direction: column`，header 固定 `auto`，内容区 `flex: 1; min-height: 0`
- **表格滚动**交给 `pure-table` 的 `adaptive` prop，不要手动设 overflow

```css
/* 页面层 — Grid */
.user-page {
  display: grid;
  grid-template-columns: 200px 1fr;
  overflow: hidden;

  &:has(.role-panel) {
    grid-template-columns: 200px 1fr 360px;
  }
}

/* 面板内部 — Flex */
.tree-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.tree-scroll {
  flex: 1;
  min-height: 0;         /* 关键：允许 flex 子元素收缩到内容以下 */
}
```

## 5. 固定高度防溢出（兼容 fixedHeader 模式）

**优先顺序**：

1. **首选**：让父容器的 flex/grid 自然约束高度，`overflow: hidden` 截断溢出
2. **次选**：当父容器无明确高度时（如 fixedHeader 的 `el-scrollbar`），用 `calc` 估算

```css
/* fixedHeader 模式下，el-scrollbar 无 flex 约束，需要手动设高 */
.user-page {
  height: calc(100vh - 150px);
  /* 150px = 81px(section padding-top) + 24px(main-content margin) + 35px(footer) + 10px(缓冲) */
  overflow: hidden;
}
```

**调整方法**：如果仍有滚动条，减小值（更多扣除）。如果底部有空隙，增大值。每次增减 10px 即可定位。

## 6. 通用高度计算公式

```
可用页面高度 = 100vh
               - section padding-top (约 81px，含 navbar+tabs)
               - main-content margin (上下合计)
               - footer (约 35px)
               - 其他固定元素高度
               - 安全缓冲 (10-20px)
```

| 模式 | 公式 |
|------|------|
| fixedHeader (有 tabs) | `100vh - 81px - margin - 35px - buffer` |
| fixedHeader (无 tabs) | `100vh - 48px - margin - 35px - buffer` |
| non-fixedHeader | grow 有 `flex:1`，用 `height: 100%` 适配即可 |

## 7. 表格 + 工具栏模式

```vue
<PureTableBar title="标题" :columns="columns" @refresh="onSearch">
  <template #buttons>
    <el-button>操作按钮</el-button>     <!-- 与工具栏同行 -->
  </template>
  <template v-slot="{ size, dynamicColumns }">
    <pure-table
      adaptive
      :adaptiveConfig="{ offsetBottom: 108 }"
      :size="size"
      :columns="dynamicColumns"
      ...
    />
  </template>
</PureTableBar>
```

**规则**：
- `PureTableBar` 的 `#buttons` 插槽放操作按钮，与刷新/密度/列设置同行
- 表格列定义需要 `slot` 属性 + 同名 `<template #xxx>` 才能使用自定义渲染
- `adaptive` + `offsetBottom` 让表格自动计算高度，表体内独立滚动

## 8. 禁止做法

| 禁止 | 原因 | 替代 |
|------|------|------|
| `:has()` 选择同一元素 | 不可能匹配自身 | `div.a.b` 组合选择器 |
| `!important` 覆盖 layout | 污染级联 | 提升特异性（加 tag） |
| 死算 `calc(xxx)` | 不同模式结构不同 | 先确认 DOM，再计算 |
| `h-full` 整页布��� | 父链无确定高度时失效 | Grid + calc viewport |
| 忽略 `min-height: 0` | flex 子元素不收缩 | `flex: 1; min-height: 0` |
