# Permission Center Frontend 完善计划总览

## 计划清单

本套 PRP-Plan 当前包含 14 个执行计划文件，另附 1 个后端 API 契约差异跟踪文档。

说明：

- 以下清单是唯一执行入口，执行顺序、范围边界和最终验收口径以本文件为准。
- `frontend-perm-phase1/2/3-development.plan.md` 为拆分后的聚合协调文档，不计入执行清单数量。
- `frontend-perm-api-mock.plan.md` 是最后一个计划，用于把所有权限中心接口调用收口到统一 mock 演示环境。

| 序号 | 计划文件                                              | 目标                     | 复杂度 | 预估工时 |
| ---- | ----------------------------------------------------- | ------------------------ | ------ | -------- |
| 1    | `frontend-perm-structure-analysis.plan.md`            | 代码规范和结构分析       | Low    | 1-2天    |
| 2    | `frontend-perm-tree-component-abstraction.plan.md`    | Tree 组件抽象            | Medium | 2-3天    |
| 3    | `frontend-perm-completed-modules-improvement.plan.md` | 已完成模块完善           | Medium | 2-3天    |
| 4    | `frontend-perm-domain-type-quick.plan.md`             | 业务域与类型定义快速任务 | Medium | 2-3天    |
| 5    | `frontend-perm-condition-module.plan.md`              | 权限条件管理             | Medium | 1-2天    |
| 6    | `frontend-perm-user-module.plan.md`                   | 抽象用户管理             | Medium | 1-2天    |
| 7    | `frontend-perm-log-module.plan.md`                    | 日志审计                 | Medium | 1-2天    |
| 8    | `frontend-perm-explain-module.plan.md`                | 权限排查诊断             | High   | 2-3天    |
| 9    | `frontend-perm-conflict-module.plan.md`               | 冲突规则管理             | High   | 2-3天    |
| 10   | `frontend-perm-api-mapping-module.plan.md`            | 资源 API 映射            | Medium | 1-2天    |
| 11   | `frontend-perm-version-module.plan.md`                | 权限版本查询             | Low    | 0.5-1天  |
| 12   | `frontend-perm-system-config-module.plan.md`          | 系统配置管理             | Low    | 1天      |
| 13   | `frontend-perm-view-improvement.plan.md`              | 权限视图完善             | High   | 2-3天    |
| 14   | `frontend-perm-api-mock.plan.md`                      | 统一 Mock 与演示验收     | Medium | 2-3天    |

---

## 执行顺序

| 序号 | 计划文件                                              | 模块名称             | 优先级 | 预估工时 | 依赖       | 说明                                        |
| ---- | ----------------------------------------------------- | -------------------- | ------ | -------- | ---------- | ------------------------------------------- |
| 01   | `frontend-perm-structure-analysis.plan.md`            | 代码规范分析         | P0     | 1-2天    | 无         | 必须最先执行，建立规范与代码边界            |
| 02   | `frontend-perm-tree-component-abstraction.plan.md`    | Tree 组件抽象        | P1     | 2-3天    | 01         | 为角色/资源/权限树形页面提供复用基础设施    |
| 03   | `frontend-perm-completed-modules-improvement.plan.md` | 已完成模块完善       | P1     | 2-3天    | 01         | 先稳定已有页面、共享 API、权限码和路由      |
| 04   | `frontend-perm-domain-type-quick.plan.md`             | 业务域与类型定义     | P2     | 2-3天    | 01, 03     | 基础数据管理，其他模块可复用                |
| 05   | `frontend-perm-condition-module.plan.md`              | 权限条件管理         | P2     | 1-2天    | 01, 03     | 核心能力，角色权限配置会依赖                |
| 06   | `frontend-perm-user-module.plan.md`                   | 抽象用户管理         | P2     | 1-2天    | 01, 03     | 核心能力，用户-角色链路的基础               |
| 07   | `frontend-perm-log-module.plan.md`                    | 日志审计             | P3     | 1-2天    | 01, 03     | 可独立开发，支撑审计与排障                  |
| 08   | `frontend-perm-explain-module.plan.md`                | 权限排查诊断         | P3     | 2-3天    | 01, 03, 13 | 依赖 permission view 能力先收敛             |
| 09   | `frontend-perm-conflict-module.plan.md`               | 冲突规则管理         | P3     | 2-3天    | 01, 05, 06 | 依赖条件和用户等核心模块稳定                |
| 10   | `frontend-perm-api-mapping-module.plan.md`            | 资源 API 映射        | P3     | 1-2天    | 01, 03     | 集成到资源管理页面                          |
| 11   | `frontend-perm-version-module.plan.md`                | 权限版本查询         | P4     | 0.5-1天  | 01, 03     | 当前只做后端已支持的 query 能力             |
| 12   | `frontend-perm-system-config-module.plan.md`          | 系统配置管理         | P4     | 1天      | 01, 03     | 系统级能力，可独立推进                      |
| 13   | `frontend-perm-view-improvement.plan.md`              | 权限视图完善         | P4     | 2-3天    | 01, 03, 06 | 统一权限视图 API 与子视图体验               |
| 14   | `frontend-perm-api-mock.plan.md`                      | 统一 Mock 与演示验收 | P5     | 2-3天    | 03, 04-13  | 最终收口所有接口调用与菜单/按钮权限演示环境 |

### 优先级说明

| 优先级 | 含义     | 执行策略                       |
| ------ | -------- | ------------------------------ |
| P0     | 基础分析 | 必须最先完成，阻塞后续所有工作 |
| P1     | 关键依赖 | 先稳定共享层和基础设施         |
| P2     | 核心功能 | 主要业务功能，应优先实现       |
| P3     | 高级功能 | 在核心功能完成后推进           |
| P4     | 系统功能 | 优先级较低，但仍需完成         |
| P5     | 最终验收 | 统一 mock、演示和产品评估入口  |

### 并行策略

- 阶段 2（P1）可并行：02 Tree 组件抽象、03 已完成模块完善的非冲突部分。
- 阶段 3（P2）可并行：04 业务域与类型定义、05 权限条件、06 抽象用户。
- 阶段 4（P3）可并行：07 日志审计、09 冲突规则、10 资源 API 映射。
- 阶段 5（P4）可并行：11 权限版本、12 系统配置、13 权限视图完善。
- 阶段 6（P5）收口：14 统一 Mock 与演示验收，原则上在前置 API 契约稳定后统一推进。

---

## 当前状态 vs 目标状态

### 模块完成情况

| 模块           | 后端 API | 前端 API        | 前端页面 | 状态     | 所属计划                                     |
| -------------- | -------- | --------------- | -------- | -------- | -------------------------------------------- |
| 角色管理       | ✅       | ✅              | ✅       | 完成     | -                                            |
| 资源管理       | ✅       | ✅              | ✅       | 完成     | -                                            |
| 服务配置       | ✅       | ✅              | ✅       | 完成     | -                                            |
| 用户角色分配   | ✅       | ✅              | ✅       | 完成     | -                                            |
| 权限视图       | ✅       | ⚠️ 部分         | ⚠️ 部分  | 待完善   | `frontend-perm-view-improvement.plan.md`     |
| 权限排查       | ✅       | ⚠️ 组合层待收敛 | ⚠️ 占位  | 待完善   | `frontend-perm-explain-module.plan.md`       |
| 业务域管理     | ✅       | ✅              | ❌       | 快速任务 | `frontend-perm-domain-type-quick.plan.md`    |
| 类型定义       | ✅       | ✅              | ❌       | 快速任务 | `frontend-perm-domain-type-quick.plan.md`    |
| 权限条件       | ✅       | ❌              | ❌       | 待开发   | `frontend-perm-condition-module.plan.md`     |
| 抽象用户       | ✅       | ❌              | ❌       | 待开发   | `frontend-perm-user-module.plan.md`          |
| 日志审计       | ✅       | ❌              | ❌       | 待开发   | `frontend-perm-log-module.plan.md`           |
| 冲突规则       | ✅       | ❌              | ❌       | 待开发   | `frontend-perm-conflict-module.plan.md`      |
| 资源 API 映射  | ✅       | ❌              | ❌       | 待开发   | `frontend-perm-api-mapping-module.plan.md`   |
| 域配置         | ✅       | ⚠️ 部分         | ❌       | 快速任务 | `frontend-perm-domain-type-quick.plan.md`    |
| 权限版本       | ✅       | ❌              | ❌       | 待开发   | `frontend-perm-version-module.plan.md`       |
| 系统配置       | ✅       | ❌              | ❌       | 待开发   | `frontend-perm-system-config-module.plan.md` |
| 统一 Mock 演示 | N/A      | ❌              | ❌       | 待开发   | `frontend-perm-api-mock.plan.md`             |

### API 层完成情况

| API 文件                | 当前状态 | 所属计划                                              | 说明                     |
| ----------------------- | -------- | ----------------------------------------------------- | ------------------------ |
| `role.ts`               | ✅ 完整  | -                                                     | -                        |
| `resource.ts`           | ✅ 完整  | -                                                     | -                        |
| `service.ts`            | ✅ 完整  | -                                                     | -                        |
| `userRole.ts`           | ✅ 完整  | -                                                     | -                        |
| `domain.ts`             | ✅ 完整  | `frontend-perm-domain-type-quick.plan.md`             | 业务域 CRUD 已可复用     |
| `operation.ts`          | ✅ 完整  | `frontend-perm-domain-type-quick.plan.md`             | 类型定义页面复用         |
| `type.ts`               | ✅ 完整  | `frontend-perm-domain-type-quick.plan.md`             | -                        |
| `resourceDependency.ts` | ✅ 完整  | `frontend-perm-completed-modules-improvement.plan.md` | 需做共享层规范化         |
| `rolePermission.ts`     | ✅ 完整  | `frontend-perm-completed-modules-improvement.plan.md` | 需做共享层规范化         |
| `permissionView.ts`     | ⚠️ 部分  | `frontend-perm-view-improvement.plan.md`              | 需补全当前后端支持的接口 |
| `user.ts`               | ❌ 缺失  | `frontend-perm-user-module.plan.md`                   | 抽象用户管理             |
| `condition.ts`          | ❌ 缺失  | `frontend-perm-condition-module.plan.md`              | 权限条件管理             |
| `log.ts`                | ❌ 缺失  | `frontend-perm-log-module.plan.md`                    | 日志审计                 |
| `conflictRule.ts`       | ❌ 缺失  | `frontend-perm-conflict-module.plan.md`               | 冲突规则管理             |
| `apiMapping.ts`         | ❌ 缺失  | `frontend-perm-api-mapping-module.plan.md`            | 资源 API 映射            |
| `domainConfig.ts`       | ❌ 缺失  | `frontend-perm-domain-type-quick.plan.md`             | 域配置管理               |
| `version.ts`            | ❌ 缺失  | `frontend-perm-version-module.plan.md`                | 权限版本查询             |
| `systemConfig.ts`       | ❌ 缺失  | `frontend-perm-system-config-module.plan.md`          | 系统配置管理             |

### Mock 层状态

| 区域                  | 当前状态          | 所属计划                         | 说明                                 |
| --------------------- | ----------------- | -------------------------------- | ------------------------------------ |
| `mock/login.ts`       | ⚠️ 仅通用登录示例 | `frontend-perm-api-mock.plan.md` | 需补权限中心账号与按钮权限场景       |
| `mock/asyncRoutes.ts` | ⚠️ 仅演示菜单     | `frontend-perm-api-mock.plan.md` | 需补权限中心菜单与路由授权           |
| `mock/perm/*.ts`      | ❌ 缺失           | `frontend-perm-api-mock.plan.md` | 需覆盖 `src/api/perm` 下全部接口调用 |

---

## 用户决策确认

| 决策项 | 选项 | 说明                                           | 执行计划                                           |
| ------ | ---- | ---------------------------------------------- | -------------------------------------------------- |
| 问题1  | B    | Tree 组件抽象成公共组件 `RePermissionTree`     | `frontend-perm-tree-component-abstraction.plan.md` |
| 问题2  | B    | 权限视图完整功能（4 个子视图）                 | `frontend-perm-view-improvement.plan.md`           |
| 问题3  | A    | 业务域独立页面 `/perm/domain`（含域配置）      | `frontend-perm-domain-type-quick.plan.md`          |
| 问题4  | A    | 类型定义独立页面 `/perm/type`（含操作权限）    | `frontend-perm-domain-type-quick.plan.md`          |
| 问题5  | B    | 权限排查完整诊断系统（含可视化图表和权限对比） | `frontend-perm-explain-module.plan.md`             |

---

## 风险汇总

| 风险                     | 影响阶段 | 可能性 | 影响 | 缓解措施                                       |
| ------------------------ | -------- | ------ | ---- | ---------------------------------------------- |
| 后端 API 变更            | 所有阶段 | 低     | 高   | 开发前确认 controller / DTO 为真相来源         |
| 文档边界再次漂移         | 所有阶段 | 中     | 高   | 聚合计划只做协调，字段级契约以下游模块计划为准 |
| 页面复杂度超预期         | P2-P4    | 中     | 中   | 拆分组件、按任务和场景分批落地                 |
| Mock 与真实契约漂移      | P5       | 中     | 高   | Mock 以 `src/api/perm` 和 gap tracker 双重校验 |
| 用户评审反馈集中在展示层 | P5       | 高     | 中   | mock 数据必须可读、稳定、可切换场景            |

---

## 验收标准汇总

### Phase 0：结构审查与拆分口径

- [ ] 全部 14 个执行计划文件结构清晰、依赖关系明确
- [ ] 聚合计划不再承载字段级契约示例
- [ ] 后端 API 差异统一记录到 `frontend-perm-backend-api-gap-tracker.md`

### Phase 1：基础设施与共享层

- [ ] `frontend-perm-tree-component-abstraction.plan.md` 完成
- [ ] `frontend-perm-completed-modules-improvement.plan.md` 完成
- [ ] 共享权限码、共享路由、共享 API 包装层已收敛

### Phase 2-P4：功能模块交付

- [ ] `frontend-perm-domain-type-quick.plan.md` 完成
- [ ] `frontend-perm-condition-module.plan.md` 完成
- [ ] `frontend-perm-user-module.plan.md` 完成
- [ ] `frontend-perm-log-module.plan.md` 完成
- [ ] `frontend-perm-explain-module.plan.md` 完成
- [ ] `frontend-perm-conflict-module.plan.md` 完成
- [ ] `frontend-perm-api-mapping-module.plan.md` 完成
- [ ] `frontend-perm-version-module.plan.md` 完成当前范围
- [ ] `frontend-perm-system-config-module.plan.md` 完成
- [ ] `frontend-perm-view-improvement.plan.md` 完成当前范围

### Phase 5：统一 Mock 与演示验收

- [ ] `frontend/src/api/perm/` 下所有接口调用均有对应 mock 路由
- [ ] `/auth/user-menu` 和登录权限 mock 能完整展示权限中心菜单与按钮权限
- [ ] 关键页面具备可读的 happy path、empty state、受限/禁用或异常场景
- [ ] 用户可以直接根据 mock 数据展示评估前端完成度和改进方向

---

## 附录

### 计划文件位置

结构分析计划（2 个）：

```text
.claude/PRPs/plans/
├── frontend-perm-structure-analysis.plan.md
└── frontend-perm-completed-modules-improvement.plan.md
```

组件抽象计划（1 个）：

```text
├── frontend-perm-tree-component-abstraction.plan.md
```

模块开发计划（11 个）：

```text
├── frontend-perm-domain-type-quick.plan.md
├── frontend-perm-condition-module.plan.md
├── frontend-perm-user-module.plan.md
├── frontend-perm-log-module.plan.md
├── frontend-perm-explain-module.plan.md
├── frontend-perm-conflict-module.plan.md
├── frontend-perm-api-mapping-module.plan.md
├── frontend-perm-version-module.plan.md
├── frontend-perm-system-config-module.plan.md
├── frontend-perm-view-improvement.plan.md
└── frontend-perm-api-mock.plan.md
```

原始 Phase 聚合计划（已拆分，供协调参考）：

```text
├── frontend-perm-phase1-development.plan.md
├── frontend-perm-phase2-development.plan.md
└── frontend-perm-phase3-development.plan.md
```

附加跟踪文档：

```text
└── frontend-perm-backend-api-gap-tracker.md
```

### 前端代码位置

```text
frontend/
├── mock/
├── src/api/perm/
├── src/views/perm/
├── src/router/modules/perm.ts
└── src/constants/permission.ts
```
