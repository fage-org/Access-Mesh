# Plan: Permission Center Frontend 统一 Mock 与演示验收

## Summary

为权限中心前端所有接口调用补齐对应 mock，包括菜单/路由授权、按钮权限、`src/api/perm` 下全部 API 以及关键页面的展示场景。目标不是只让请求“能返回”，而是让用户可以直接根据 mock 数据展示评估完成情况和改进方向。

## User Story

As a 产品评审者 / 前端开发者, I want 在没有真实后端联调的情况下看到完整、可信、可切换场景的权限中心页面展示, so that 我可以判断哪些功能已经完成、哪些交互仍需要优化。

## Problem → Solution

当前计划已经覆盖 API 契约收敛和页面开发，但 mock 仍停留在通用登录 / 菜单演示层，无法完整支撑权限中心评审 → 新增最后一个统一 mock 计划，把所有权限中心接口调用、菜单权限和关键展示场景统一收口到可演示的 fake server 环境。

## Metadata

- **Complexity**: Medium
- **Source PRD**: `README.md` 最终执行顺序 + 用户新增要求
- **Estimated Files**: 10-16
- **Estimated Work**: 2-3 days

---

## Dependencies

| Plan                                                  | Relation     | Description                           |
| ----------------------------------------------------- | ------------ | ------------------------------------- |
| `frontend-perm-completed-modules-improvement.plan.md` | Prerequisite | 共享 API、权限码和路由先稳定          |
| `frontend-perm-domain-type-quick.plan.md`             | Prerequisite | 业务域、类型定义和域配置需要纳入 mock |
| `frontend-perm-condition-module.plan.md`              | Prerequisite | 权限条件接口与页面需先稳定            |
| `frontend-perm-user-module.plan.md`                   | Prerequisite | 抽象用户接口与页面需先稳定            |
| `frontend-perm-log-module.plan.md`                    | Prerequisite | 日志查询接口与页面需先稳定            |
| `frontend-perm-explain-module.plan.md`                | Prerequisite | 权限排查所依赖的组合视图需先稳定      |
| `frontend-perm-conflict-module.plan.md`               | Prerequisite | 冲突规则接口与页面需先稳定            |
| `frontend-perm-api-mapping-module.plan.md`            | Prerequisite | 资源 API 映射接口与页面需先稳定       |
| `frontend-perm-version-module.plan.md`                | Prerequisite | 版本查询接口需先稳定                  |
| `frontend-perm-system-config-module.plan.md`          | Prerequisite | 系统配置接口与页面需先稳定            |
| `frontend-perm-view-improvement.plan.md`              | Prerequisite | 权限视图接口与子视图需先稳定          |
| `frontend-perm-backend-api-gap-tracker.md`            | Reference    | 用于确认 mock 不伪造后端不存在的字段  |

---

## Mandatory Reading

| Priority | File                                       | Why                                    |
| -------- | ------------------------------------------ | -------------------------------------- |
| P0       | `frontend/build/plugins.ts`                | 确认 fake server 的扫描方式和启用策略  |
| P0       | `frontend/mock/login.ts`                   | 登录 mock 账号与按钮权限的当前组织方式 |
| P0       | `frontend/mock/asyncRoutes.ts`             | 菜单和动态路由 mock 的当前组织方式     |
| P0       | `frontend/src/api/perm/`                   | 统一接口清单的唯一来源                 |
| P1       | `frontend-perm-backend-api-gap-tracker.md` | 确保 mock 与真实契约边界一致           |

---

## Scope

### In Scope

- `frontend/src/api/perm/` 下所有现有与新增 API 文件。
- 权限中心页面运行所需的菜单路由、按钮权限和登录账号场景。
- 用户评审直接可见的 happy path、empty state、受限 / 禁用、冲突 / 历史等关键展示场景。

### Out of Scope

- 不为权限中心之外的业务模块补充新 mock。
- 不用 mock 伪造后端尚未设计或已在 gap tracker 中明确标记为未来增强的字段。
- 不引入随机且不可读的数据作为主展示场景。

---

## Mock Coverage Matrix

| API File                | Mock File                         | Required Coverage                                              |
| ----------------------- | --------------------------------- | -------------------------------------------------------------- |
| `role.ts`               | `mock/perm/role.ts`               | 树、列表、详情、增删改、角色授权基础展示                       |
| `resource.ts`           | `mock/perm/resource.ts`           | 树、列表、详情、增删改                                         |
| `service.ts`            | `mock/perm/service.ts`            | 服务列表、详情、增删改                                         |
| `userRole.ts`           | `mock/perm/userRole.ts`           | 用户角色分配与回显                                             |
| `domain.ts`             | `mock/perm/domain.ts`             | 业务域 CRUD                                                    |
| `domainConfig.ts`       | `mock/perm/domain.ts`             | 域配置 list / detail / save / remove                           |
| `type.ts`               | `mock/perm/type.ts`               | 类型定义 CRUD                                                  |
| `operation.ts`          | `mock/perm/type.ts`               | 操作权限配置与回显                                             |
| `resourceDependency.ts` | `mock/perm/resourceDependency.ts` | 依赖关系列表、同步、校验                                       |
| `rolePermission.ts`     | `mock/perm/rolePermission.ts`     | 角色权限回显与保存                                             |
| `permissionView.ts`     | `mock/perm/view.ts`               | effective-roles、resource-users、resource-tree、recent-changes |
| `condition.ts`          | `mock/perm/condition.ts`          | list / detail / create / update / remove                       |
| `user.ts`               | `mock/perm/user.ts`               | list / detail / create / update / remove / sync                |
| `log.ts`                | `mock/perm/log.ts`                | change log、operation log                                      |
| `conflictRule.ts`       | `mock/perm/conflict.ts`           | list / detail / create / update / remove / detect              |
| `apiMapping.ts`         | `mock/perm/apiMapping.ts`         | list / create / update / remove                                |
| `version.ts`            | `mock/perm/version.ts`            | query                                                          |
| `systemConfig.ts`       | `mock/perm/systemConfig.ts`       | list / detail / save                                           |

---

## Files to Create / Update

| File                                       | Action | Justification                             |
| ------------------------------------------ | ------ | ----------------------------------------- |
| `frontend/mock/perm/core.ts`               | CREATE | 统一成功响应、分页响应和公共 fixture 工具 |
| `frontend/mock/perm/role.ts`               | CREATE | 角色相关接口 mock                         |
| `frontend/mock/perm/resource.ts`           | CREATE | 资源相关接口 mock                         |
| `frontend/mock/perm/service.ts`            | CREATE | 服务相关接口 mock                         |
| `frontend/mock/perm/userRole.ts`           | CREATE | 用户角色分配 mock                         |
| `frontend/mock/perm/domain.ts`             | CREATE | 业务域与域配置 mock                       |
| `frontend/mock/perm/type.ts`               | CREATE | 类型定义与操作权限 mock                   |
| `frontend/mock/perm/resourceDependency.ts` | CREATE | 资源依赖 mock                             |
| `frontend/mock/perm/rolePermission.ts`     | CREATE | 角色权限 mock                             |
| `frontend/mock/perm/view.ts`               | CREATE | 权限视图相关 mock                         |
| `frontend/mock/perm/condition.ts`          | CREATE | 权限条件 mock                             |
| `frontend/mock/perm/user.ts`               | CREATE | 抽象用户 mock                             |
| `frontend/mock/perm/log.ts`                | CREATE | 日志查询 mock                             |
| `frontend/mock/perm/conflict.ts`           | CREATE | 冲突规则 mock                             |
| `frontend/mock/perm/apiMapping.ts`         | CREATE | 资源 API 映射 mock                        |
| `frontend/mock/perm/version.ts`            | CREATE | 权限版本查询 mock                         |
| `frontend/mock/perm/systemConfig.ts`       | CREATE | 系统配置 mock                             |
| `frontend/mock/login.ts`                   | UPDATE | 增加权限中心评审账号和按钮权限场景        |
| `frontend/mock/asyncRoutes.ts`             | UPDATE | 增加权限中心菜单、路由和 auths            |

---

## Scenario Design Principles

- **可读性优先**: 使用稳定、业务可解释的名称、编码、描述和时间戳，避免主场景依赖随机数据。
- **评审友好**: 每个核心页面至少提供一组 happy path 和一组边界场景。
- **契约优先**: mock 返回结构以 `src/api/perm` 的类型定义和 gap tracker 为准。
- **最小伪造**: 不新增后端不存在的一等字段；如需展示别名或分类，由前端元数据承担。

建议统一支持以下场景切换：

- 默认场景：页面主流程可完整展示。
- 空数据场景：验证空态布局。
- 受限 / 禁用场景：验证按钮权限、状态标签和只读态。
- 特殊业务场景：如冲突命中、近期变更较多、条件规则复杂、系统配置只读等。

---

## Step-by-Step Tasks

### Task 1: 盘点接口与运行时依赖

- **ACTION**: 以 `frontend/src/api/perm/` 为唯一来源生成 mock 覆盖清单
- **IMPLEMENT**:
  - 校对所有现有 API 文件与计划中的新增 API 文件
  - 标记每个页面除业务接口外还依赖哪些菜单、按钮权限和登录权限
  - 形成 mock coverage matrix

### Task 2: 创建共享 mock 基础设施

- **ACTION**: 创建 `frontend/mock/perm/core.ts`
- **IMPLEMENT**:
  - 提供统一 `success/data/items/total/pageNum/pageSize/hasNext` 响应工具
  - 提供固定 fixture 数据和可复用的 fixture builder
  - 预留场景切换工具，支持 empty / disabled / readonly / error 等模式

### Task 3: 补齐业务接口 mock

- **ACTION**: 按 coverage matrix 创建模块化 mock 文件
- **IMPLEMENT**:
  - 基础模块：role、resource、service、userRole、domain、type、resourceDependency、rolePermission
  - 新增模块：condition、user、log、view、conflict、apiMapping、version、systemConfig
  - 域配置与类型 / 操作权限 mock 允许按文件聚合，但路由必须完整覆盖

### Task 4: 补齐权限中心菜单与按钮权限 mock

- **ACTION**: 更新 `frontend/mock/login.ts` 和 `frontend/mock/asyncRoutes.ts`
- **IMPLEMENT**:
  - 增加权限中心菜单树和对应路由项
  - 增加按钮级权限码，确保页面操作按钮可以按账号差异显示
  - 建议至少提供 3 类账号：全权限评审账号、只读审计账号、受限运营账号

### Task 5: 设计展示场景而不是只返回数据

- **ACTION**: 为关键页面准备评审场景
- **IMPLEMENT**:
  - 权限条件：正常条件、禁用条件、空列表
  - 抽象用户：正常列表、同步结果、只读账号
  - 日志审计：大量变更记录、空日志、分页
  - 冲突规则：命中冲突、未命中冲突
  - 权限视图：有效角色、资源授权分布、资源树、近期变更
  - 系统配置：可编辑配置、只读配置

### Task 6: 验证 mock 可支撑评审

- **ACTION**: 以页面展示为目标做 walkthrough 验证
- **IMPLEMENT**:
  - 确认权限中心页面在无真实后端时可正常进入和展示
  - 确认筛选、详情、表单回显、空态、按钮权限均能被看见
  - 对照 gap tracker，确认没有 mock 出后端不存在的字段

---

## Testing Strategy

### Static Analysis

```bash
cd frontend
pnpm typecheck
pnpm lint:eslint
pnpm lint:prettier
```

### Demo Validation

| Check            | Expected Result                                        |
| ---------------- | ------------------------------------------------------ |
| 权限中心菜单进入 | mock 菜单可看到权限中心全部目标页面入口                |
| 关键页面初次加载 | 不依赖真实后端即可渲染主要信息                         |
| 按钮权限差异     | 不同 mock 账号能看到不同按钮展示                       |
| 空态和异常态     | 页面能展示空态 / 只读态 / 禁用态                       |
| 复杂页面展示     | explain / view / conflict / log 等页面有可读的示例数据 |

---

## Validation Commands

```bash
cd frontend
pnpm dev
pnpm typecheck
pnpm lint:eslint
pnpm build
```

EXPECT: 开发环境下 fake server 可直接支撑权限中心页面演示，类型检查和构建通过。

---

## Acceptance Criteria

- [ ] `frontend/src/api/perm/` 下所有接口调用都有对应 mock 路由
- [ ] 权限中心菜单、路由和按钮权限可以在 mock 环境下完整演示
- [ ] 每个关键页面至少具备 1 组 happy path 和 1 组边界场景
- [ ] mock 数据结构与 `src/api/perm` 和 gap tracker 保持一致
- [ ] 用户可直接根据 mock 数据展示评估前端完成情况和改进方向

---

## Risks

| Risk                          | Likelihood | Impact | Mitigation                                        |
| ----------------------------- | ---------- | ------ | ------------------------------------------------- |
| mock 覆盖不全导致评审结论失真 | Medium     | High   | 以 `src/api/perm` 全量清单做 coverage matrix      |
| mock 数据可读性差             | High       | Medium | 以固定、可解释 fixture 为主，不依赖随机生成       |
| mock 伪造未来字段             | Medium     | High   | 以 gap tracker 和 API 类型定义双重校验            |
| 菜单 / 按钮权限未联动         | Medium     | Medium | 同步更新 `login.ts`、`asyncRoutes.ts` 和业务 mock |
