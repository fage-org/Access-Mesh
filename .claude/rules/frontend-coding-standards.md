# Frontend 编码规范 (pure-admin-thin)

基于 Vue 3 + TypeScript + Vite + Element Plus + Tailwind CSS。

## 1. 包管理器

**MUST** 使用 pnpm，禁止使用 npm 或 yarn。

```json
// package.json
"preinstall": "npx only-allow pnpm"
"engines": {
  "node": "^20.19.0 || >=22.13.0",
  "pnpm": ">=9"
}
```

## 2. TypeScript 类型导入

**MUST** 与 eslint 工具链一致（`@typescript-eslint/no-import-type-side-effects` + `consistent-type-imports`）：**纯类型语句**（语句内只导入类型）用 `import type { X }`；**混合语句**（同语句导入值+类型）用内联修饰 `{ type X }`。

```typescript
// ✅ 正确 — 纯类型语句（内联形态被 TS 擦除后残留 side-effect import，lint error + autofix 必改回）
import type { FormInstance } from "element-plus";

// ✅ 正确 — 混合语句（值+类型，顶层 type 无法修饰混合语句）
import { getUserPage, type OrgTreeNode } from "@/api/user-manage";

// ❌ 禁止 — 纯类型语句用内联
import { type FormInstance } from "element-plus";
```

## 3. Vue 组件定义

**MUST** 使用 `<script setup lang="ts">` + `defineOptions` 定义组件名。

```vue
<!-- ✅ 正确 -->
<script setup lang="ts">
defineOptions({
  name: "Login",
});

const loading = ref(false);
</script>

<!-- ❌ 禁止：Options API -->
<script>
export default {
  name: "Login",
  data() {
    return { loading: false };
  },
};
</script>
```

## 4. 组件导出模式

**MUST** 使用 barrel 文件（index.ts）导出组件。

```typescript
// ✅ 正确 — components/ReIcon/index.ts
import iconifyIconOffline from "./src/iconifyIconOffline";
import iconifyIconOnline from "./src/iconifyIconOnline";
import fontIcon from "./src/iconfont";

const IconifyIconOffline = iconifyIconOffline;
const IconifyIconOnline = iconifyIconOnline;
const FontIcon = fontIcon;

export { IconifyIconOffline, IconifyIconOnline, FontIcon };

// ❌ 禁止 — 直接导出
export { default as IconifyIconOffline } from "./src/iconifyIconOffline";
```

## 5. 组件命名约定

**SHOULD** 使用 `Re` 前缀命名可复用组件。

```
✅ 正确命名：
- ReAuth     (权限组件)
- ReDialog   (对话框组件)
- ReIcon     (图标组件)
- RePerms    (权限控制组件)
- ReText     (文本组件)

❌ 不推荐：
- Auth
- Dialog
- Icon
```

## 6. API 定义模式

**MUST** 导出类型定义 + API 函数，使用 `http.request<T>` 泛型。

```typescript
// ✅ 正确 — api/user.ts
import { http } from "@/utils/http";

export type UserResult = {
  success: boolean;
  data: {
    avatar: string;
    username: string;
    roles: Array<string>;
    accessToken: string;
  };
};

export const getLogin = (data?: object) => {
  return http.request<UserResult>("post", "/login", { data });
};

// ❌ 禁止 — 不导出类型
const getLogin = (data?: object) => http.request("post", "/login", { data });
```

## 7. Pinia Store 模式

**MUST** 使用 `defineStore` + `state/actions` + Hook 导出。

```typescript
// ✅ 正确 — store/modules/user.ts
import { defineStore } from "pinia";

export const useUserStore = defineStore("pure-user", {
  state: (): userType => ({
    avatar: "",
    username: "",
    roles: []
  }),
  actions: {
    SET_AVATAR(avatar: string) {
      this.avatar = avatar;
    },
    async loginByUsername(data) {
      return new Promise<UserResult>((resolve, reject) => {
        getLogin(data)
          .then(data => resolve(data))
          .catch(error => reject(error));
      });
    }
  }
});

// Hook 导出（用于组件外使用）
export function useUserStoreHook() {
  return useUserStore(store);
}

// ❌ 禁止 — 不导出 Hook
export const useUserStore = defineStore("pure-user", { ... });
```

## 8. 自定义 Hooks 模式

**MUST** 返回对象包含所有需要的方法和计算属性。

```typescript
// ✅ 正确 — layout/hooks/useNav.ts
export function useNav() {
  const route = useRoute();
  const router = useRouter();

  const username = computed(() => useUserStoreHook()?.username);
  const isCollapse = computed(() => !pureApp.getSidebarStatus);

  function logout() {
    useUserStoreHook().logOut();
  }

  function toggleSideBar() {
    pureApp.toggleSideBar();
  }

  return {
    route,
    username,
    isCollapse,
    logout,
    toggleSideBar,
  };
}

// ❌ 禁止 — 只返回单个值
export function useNav() {
  return useRoute();
}
```

<a id="list-context"></a>

### 列表加载与上下文约束

复用 `useListLoad/usePagedList`：最后一次请求生效，同一上下文加载失败保留原数据，分页采用最新 total；API 错误文案由 `_envelope` 统一提取。`onLoaded` 回调异常不能伪装成请求失败。跨对象列表提供稳定 `contextKey`，发起切换即清空旧行，失败不回填其他对象数据；`clear()` 作废在途并经 `onClear` 复位。null 是合法上下文值，不能用 `?? NO_CONTEXT` 把它和“从未加载”合并。

保存入口仍须比对行/弹窗携带的对象与当前上下文，覆盖后退键、全局弹窗及路由重进窗口；显式 `openedAt*` 上下文不能回退猜测。UserDetailPanel 以弹窗内显式选择为表单权威，不套外层对象守卫；PositionTab 保留自有代际与预清空，不为统一形式强迁公共 loader。页面接线见[服务接口映射](../../docs/design/frontend/service-interface-mapping.md)和[资源操作设计](../../docs/design/frontend/resource-operation.md)；加载与保存边界以本节为准。

远程选择器翻页失败回滚页码并保留数据，迟到失败不能覆写新请求；重置回首页并清筛选，watcher 与显式加载单发。候选缓存不能 fallback 猜测对象；本地过滤使一页空时保留 disabled 占位 option，使下拉翻页仍可达。成员多选保留 keyword/page，角色候选 watcher 的 pre-flush 前提不能改成 post。加载失败重抛用于翻页回滚，搜索调用方需消费该异常。来源：T-FE-051、T-FE-058/059，[迁移前决定](../../docs/archive/2026-09-26/decision-registry-before.md)（原第 117、118、182–185、187 行）。

## 9. Vue 指令导出

**MUST** 使用 barrel 文件导出所有指令。

```typescript
// ✅ 正确 — directives/index.ts
export * from "./auth";
export * from "./copy";
export * from "./longpress";
export * from "./optimize";
export * from "./perms";
export * from "./ripple";

// main.ts 中注册
import * as directives from "@/directives";
Object.keys(directives).forEach((key) => {
  app.directive(key, (directives as { [key: string]: Directive })[key]);
});
```

## 10. 路由定义

**MUST** 在 `router/modules/` 目录下定义模块路由，自动导入。

```typescript
// ✅ 正确 — router/modules/home.ts
export default {
  path: "/",
  name: "Home",
  component: () => import("@/layout/index.vue"),
  redirect: "/welcome",
  meta: {
    title: "首页",
    rank: 0,
  },
  children: [
    {
      path: "/welcome",
      name: "Welcome",
      component: () => import("@/views/welcome/index.vue"),
      meta: {
        title: "欢迎页",
      },
    },
  ],
};

// router/index.ts 自动导入
const modules: Record<string, any> = import.meta.glob(
  ["./modules/**/*.ts", "!./modules/**/remaining.ts"],
  { eager: true },
);
```

## 11. HTML 自闭合标签

**MUST** 所有标签使用自闭合格式。

```vue
<!-- ✅ 正确 -->
<el-input v-model="value" />
<el-button @click="handleClick" />
<div class="container" />
<MyComponent />

<!-- ❌ 禁止 -->
<el-input v-model="value"></el-input>
<el-button @click="handleClick"></el-button>
```

## 12. Prettier 格式化

**MUST** 遵循以下格式化规则：

```javascript
// .prettierrc.js
{
  bracketSpacing: true,   // 对象括号内空格 { foo: bar }
  singleQuote: false,     // 使用双引号
  arrowParens: "avoid",   // 箭头函数单参数不加括号 x => x
  trailingComma: "none"   // 无尾逗号
}

// ✅ 正确
const obj = { name: "test", value: 123 };
const fn = x => x + 1;

// ❌ 禁止
const obj = {name: "test", value: 123};
const fn = (x) => x + 1;
const arr = [1, 2, 3,];
```

## 13. 未使用变量命名

**MUST** 使用 `_` 前缀标记故意未使用的变量。

```typescript
// ✅ 正确 — argsIgnorePattern 和 varsIgnorePattern 配置允许
function handler(_event, data) {
  // _event 未使用，但不报错
  return data;
}

const [_first, second] = arr;

// ❌ 禁止 — 未使用且无前缀
function handler(event, data) {
  return data; // event 未使用会报 ESLint 错误
}
```

## 14. SCSS/CSS 属性顺序

**MUST** 按 stylelint-config-recess-order 规定的顺序排列。

```scss
// ✅ 正确顺序
.element {
  // 1. $变量
  $color: red;

  // 2. 自定义属性
  --custom-prop: value;

  // 3. @规则
  @media (min-width: 768px) {
  }

  // 4. 声明（按逻辑分组）
  display: flex;
  position: relative;
  width: 100px;
  height: 100px;
  margin: 10px;
  padding: 10px;
  color: $color;
  font-size: 14px;

  // 5. 规则
  .child {
  }
}
```

## 15. Tailwind CSS 使用

**MUST** 使用 Tailwind 4.x 语法，支持 `@tailwind` 和 `@apply`。

```scss
// ✅ 正确
@tailwind base;
@tailwind components;
@tailwind utilities;

.button {
  @apply flex items-center justify-center;
}

// ❌ 禁止 — 旧版语法
@import "tailwindcss/base";
```

## 16. Git Commit 格式

**MUST** 使用 Conventional Commits 规范。

```
// ✅ 正确格式
feat: 添加用户管理页面
fix: 修复登录验证逻辑
perf: 优化路由加载性能
style: 调整按钮样式
docs: 更新API文档
test: 添加单元测试
refactor: 重构权限模块
build: 更新构建配置
ci: 修改CI流程
chore: 更新依赖版本
revert: 撤销上一次提交

// ❌ 禁止
添加用户管理页面
update code
fix bug
```

## 17. 路径别名

**MUST** 使用 `@/` 和 `@build/` 别名。

```typescript
// ✅ 正确
import { http } from "@/utils/http";
import { router } from "@/router";
import { getPluginsList } from "@build/plugins";

// ❌ 禁止 — 相对路径
import { http } from "../../utils/http";
import { router } from "../router";
```

## 18. 全局类型定义

**MUST** 在 `types/` 目录定义全局类型，无需导入即可使用。

```typescript
// types/index.d.ts — 全局可用
type RefType<T> = T | null;
type EmitType = (event: string, ...args: any[]) => void;
type Recordable<T = any> = Record<string, T>;
type Nullable<T> = T | null;

interface Fn<T = any, R = T> {
  (...arg: T[]): R;
}

// 直接使用，无需导入
const data: Recordable = { name: "test" };
const callback: Fn = () => {};
```

## 19. 异步组件加载

**MUST** 使用 `() => import()` 懒加载组件。

```typescript
// ✅ 正确
const IFrame = () => import("@/layout/frame.vue");
component: () => import("@/views/welcome/index.vue");

// ❌ 禁止 — 静态导入（除非必要）
import IFrame from "@/layout/frame.vue";
```

## 20. 权限控制

**MUST** 使用 `hasPerms` 函数或 `<Perms>` / `<Auth>` 组件。

```vue
<!-- ✅ 正确 — 组件方式 -->
<Perms value="USER:CREATE">
  <el-button>添加用户</el-button>
</Perms>

<Auth :value="['USER:UPDATE', 'USER:DELETE']">
  <el-button>编辑</el-button>
</Auth>

<!-- ✅ 正确 — 函数方式 -->
<script setup lang="ts">
import { hasPerms } from "@/utils/auth";

if (hasPerms("USER:CREATE")) {
  // 执行添加操作
}
</script>

<!-- ❌ 禁止 — 硬编码权限判断 -->
<script setup>
if (user.permissions.includes("USER:CREATE")) {
}
</script>
```

## 21. Token 处理

**MUST** 使用 `auth.ts` 中的统一方法。

```typescript
// ✅ 正确
import { getToken, setToken, removeToken, formatToken } from "@/utils/auth";

const token = getToken();
setToken(data);
removeToken();
headers["Authorization"] = formatToken(token);

// ❌ 禁止 — 直接操作 Cookie/LocalStorage
Cookies.get("token");
localStorage.getItem("token");
```

## 22. 工具函数使用

**SHOULD** 优先使用 `@pureadmin/utils` 提供的工具。

```typescript
// ✅ 正确 — 使用 @pureadmin/utils
import {
  isString,
  cloneDeep,
  isAllEmpty,
  storageLocal,
  debounce,
  deviceDetection,
} from "@pureadmin/utils";

// ❌ 不推荐 — 自己实现
function isString(val) {
  return typeof val === "string";
}
function cloneDeep(obj) {
  return JSON.parse(JSON.stringify(obj));
}
```

## 23. 图标使用

**MUST** 使用 Iconify 或 iconfont 组件。

```vue
<!-- ✅ 正确 — Iconify 离线图标 -->
<IconifyIconOffline :icon="Lock" />

<!-- ✅ 正确 — Iconify 在线图标 -->
<IconifyIconOnline icon="ri:user-fill" />

<!-- ✅ 正确 — iconfont -->
<FontIcon icon="icon-name" />

<!-- ✅ 正确 — 自动导入图标（unplugin-icons） -->
import Lock from "~icons/ri/lock-fill";

<!-- ❌ 禁止 — 直接使用 img -->
<img src="icon.png" />
```

## 24. 目录结构

**MUST** 遵循约定目录结构：

```
frontend/
├── build/          # Vite 构建配置
├── mock/           # Mock 数据
├── public/         # 静态资源
├── src/
│   ├── api/        # API 调用
│   ├── assets/     # 资源文件
│   ├── components/ # 可复用组件 (ReXxx)
│   ├── config/     # 配置
│   ├── directives/ # Vue 指令
│   ├── layout/     # 布局组件
│   │   ├── components/ # 布局子组件
│   │   └── hooks/      # 布局相关 hooks
│   ├── plugins/    # 插件配置
│   ├── router/     # 路由
│   │   ├── modules/    # 路由模块
│   │   └── utils.ts    # 路由工具
│   ├── store/      # Pinia 状态
│   │   ├── modules/    # Store 模块
│   │   ├── utils.ts    # Store 工具
│   │   └── types.ts    # Store 类型
│   ├── style/      # 全局样式
│   ├── utils/      # 工具函数
│   ├── views/      # 页面视图
│   ├── App.vue     # 根组件
│   └── main.ts     # 入口
├── types/          # 全局类型定义
├── .env            # 环境变量
├── eslint.config.js
├── stylelint.config.js
├── tsconfig.json
├── vite.config.ts
└── package.json
```

## 25. 禁止事项

| 禁止行为         | 替代方案                        |
| ---------------- | ------------------------------- |
| 使用 npm/yarn    | 使用 pnpm                       |
| Options API      | Composition API + script setup  |
| 静态导入大组件   | 懒加载 `() => import()`         |
| 硬编码权限判断   | `hasPerms` / `<Perms>`          |
| 直接操作 Cookie  | `getToken/setToken/removeToken` |
| 自己实现通用工具 | `@pureadmin/utils`              |
| 相对路径导入     | `@/` 别名                       |
| 单引号           | 双引号                          |
| 尾逗号           | 无尾逗号                        |
| 标签非自闭合     | 自闭合 `<el-input />`           |

## 26. 提交前验证

**MUST** 在 git commit 或创建 PR 前运行当前仓库已定义的完整验证流程。

### 验证顺序

```bash
# 1. 构建
pnpm build

# 2. 类型检查
pnpm typecheck

# 3. Lint 检查
pnpm lint
```

frontend/package.json 已定义 `test` 脚本（`vitest run`）；涉前端代码的提交前应运行 `pnpm test`。

### Commit 前验证要求

```bash
# ✅ 正确 — 验证后提交
pnpm build && pnpm typecheck && pnpm lint && git commit -m "feat: add feature"

# ❌ 禁止 — 跳过验证直接提交
git commit -m "feat: add feature"  # 未运行 build/typecheck/lint
```

**MUST NOT** 任意验证阶段失败时提交代码。

### Lint 检查命令

```bash
# ESLint 检查
pnpm lint:eslint

# Prettier 格式化
pnpm lint:prettier

# Stylelint 检查
pnpm lint:stylelint

# 全量 lint
pnpm lint

# 类型检查
pnpm typecheck
```

### 覆盖率要求

**SHOULD** 保持业务逻辑代码 80%+ 测试覆盖率。见 `testing-standards.md` §2。

## 27. 已知配置项（可修改）

以下配置项可根据项目需要调整：

- `strict: false` (tsconfig) — 可考虑开启
- `no-debugger: "off"` (eslint) — 生产应改为 "error"
- `vue/no-v-html: "off"` (eslint) — 安全考虑应改为 "warn"
- `@typescript-eslint/no-explicit-any: "off"` — 应逐步消除 any
