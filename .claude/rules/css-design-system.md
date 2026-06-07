# 前端样式最佳实践（CSS 设计系统）

## 1. 禁止魔法数字

**MUST** 将布局中的固定像素值提取为 CSS 变量。

```scss
/* ✅ 正确 — 使用 CSS 变量 */
.user-page {
  height: calc(100vh - var(--header-offset));
}

.table-wrap :deep(.el-table__body-wrapper) {
  max-height: calc(100vh - var(--table-offset));
}

/* ❌ 禁止 — 魔法数字 */
.user-page {
  height: calc(100vh - 135px);
}

.table-wrap :deep(.el-table__body-wrapper) {
  max-height: calc(100vh - 400px);
}
```

## 2. 响应式宽度

**MUST** 侧边栏等固定宽度区域使用 `minmax()` 而非固定像素。

```scss
/* ✅ 正确 — 响应式宽度 */
.user-page {
  grid-template-columns: minmax(180px, 240px) 1fr;
}

/* ❌ 禁止 — 固定宽度 */
.user-page {
  grid-template-columns: 200px 1fr;
}
```

## 3. 颜色必须使用变量

**MUST** 使用 CSS 变量或 Tailwind 主题色，禁止硬编码十六进制颜色。

```vue
<!-- ✅ 正确 — Element Plus 变量 -->
<el-tag type="success">启用</el-tag>
<span class="text-primary">主部门</span>
<span class="text-success">已启用</span>
<span class="text-warning">开发中</span>

<!-- ❌ 禁止 — 硬编码颜色 -->
<span class="text-[#409eff]">主部门</span>
<span class="text-[#67c23a]">已启用</span>
<span class="text-[#e6a23c]">开发中</span>
<span style="color: #f56c6c">删除</span>
```

```scss
/* ✅ 正确 — CSS 变量 */
.user-avatar {
  color: var(--el-color-white);
  background: var(--el-color-primary-light-5);
}

/* ❌ 禁止 — 硬编码 */
.user-avatar {
  color: #fff;
}
```

## 4. 间距使用设计 Token

**MUST** 使用 `design-tokens.css` 中定义的间距变量。

```scss
/* ✅ 正确 */
.org-info-card {
  padding: var(--space-4);
  margin: var(--space-3) var(--space-3) ;
}

/* ❌ 禁止 */
.org-info-card {
  padding: 16px;
  margin: 12px 12px 0;
}
```

## 5. 布局偏移量管理

**MUST** 在 `design-tokens.css` 中统一定义布局偏移量。

```css
:root {
  /* 页面主内容区高度偏移（含 navbar + tabs + main-content margin + footer） */
  --header-offset: 135px;
  
  /* 表格内容区高度偏移（含 header + org-card + tabs + pagination + buffer） */
  --table-offset: 400px;
  
  /* 对话框内容区偏移 */
  --dialog-offset: 80px;
  
  /* popover 最大高度 */
  --popover-max-height: 260px;
}
```

## 6. 深色模式兼容

**MUST** 使用 Element Plus 的 CSS 变量自动适配深色模式，禁止手动写死深色模式样式。

```vue
<!-- ✅ 正确 — 自动适配深色模式 -->
<div class="bg-fill-light text-gray-400">

<!-- ❌ 禁止 — 手动适配，维护困难 -->
<div class="bg-[#f5f7fa] dark:bg-[#262727] text-gray-400">
```

## 7. Tailwind 主题扩展

**MUST** 在 `tailwind.css` 的 `@theme` 中定义项目级颜色，禁止在模板中直接使用 `#` 颜色值。

```css
/* tailwind.css */
@theme {
  --color-primary: var(--el-color-primary);
  --color-success: var(--el-color-success);
  --color-warning: var(--el-color-warning);
  --color-danger: var(--el-color-danger);
  --color-info: var(--el-color-info);
  --color-fill-light: var(--el-fill-color-light);
}
```

## 8. 组件尺寸 Token

**MUST** 使用设计 Token 管理组件尺寸。

```css
:root {
  /* 侧边栏 */
  --sidebar-width: 200px;
  --sidebar-min-width: 180px;
  --sidebar-max-width: 240px;
  
  /* 头像 */
  --avatar-size-sm: 24px;
  --avatar-size-md: 40px;
  --avatar-size-lg: 64px;
  
  /* 状态点 */
  --status-dot-size: 6px;
}
```

## 9. 响应式断点

**MUST** 使用设计 Token 中的断点变量。

```css
:root {
  --breakpoint-sm: 640px;
  --breakpoint-md: 768px;
  --breakpoint-lg: 1024px;
  --breakpoint-xl: 1280px;
  --breakpoint-2xl: 1536px;
}
```

```scss
/* ✅ 正确 — 使用设计 Token */
@media (min-width: var(--breakpoint-md)) {
  .user-page {
    grid-template-columns: minmax(180px, 240px) 1fr;
  }
}
```

## 10. 审查清单

在提交代码前，检查以下项目：

| # | 检查项 | 工具 |
|---|--------|------|
| 1 | 是否存在魔法数字（如 `135px`, `400px`）？ | 全局搜索 `calc(100vh -` |
| 2 | 是否存在硬编码颜色（如 `#409eff`）？ | 全局搜索 `#` 在 `.vue` 文件中 |
| 3 | 是否存在固定宽度（如 `200px`）？ | 全局搜索 `grid-template-columns` |
| 4 | 是否使用了设计 Token？ | 检查 `var(--space-*)` 等 |
| 5 | 是否兼容深色模式？ | 检查是否有 `dark:` 类名 |

## 参考文件

- `frontend/src/style/design-tokens.css` — 设计系统 Token
- `frontend/src/style/tailwind.css` — Tailwind 主题扩展
- `frontend/src/style/index.scss` — 全局样式入口
