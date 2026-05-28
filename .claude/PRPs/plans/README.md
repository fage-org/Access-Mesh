# Permission Center Frontend 完善计划总览

## 计划清单

本套PRP-Plan包含7个计划文件，按照执行顺序排列：

| 序号 | 计划文件 | 目标 | 复杂度 | 预估工时 |
|------|----------|------|--------|----------|
| 1 | `frontend-perm-structure-analysis.plan.md` | 代码规范和结构分析 | Low | 1-2天 |
| 2 | `frontend-perm-tree-component-abstraction.plan.md` | **Tree组件抽象** | Medium | 2-3天 |
| 3 | `frontend-perm-completed-modules-improvement.plan.md` | 已完成模块完善 | Medium | 2-3天 |
| 4 | `frontend-perm-phase1-development.plan.md` | 缺失模块开发 Phase 1（核心功能） | High | 5-7天 |
| 5 | `frontend-perm-domain-type-quick.plan.md` | **业务域与类型定义快速任务** | Medium | 2-3天 |
| 6 | `frontend-perm-phase2-development.plan.md` | 缺失模块开发 Phase 2（高级功能） | High | 4-5天 |
| 7 | `frontend-perm-phase3-development.plan.md` | 缺失模块开发 Phase 3（系统功能） | Medium | 3-4天 |

---

## 执行顺序（已排序）

以下是推荐的执行顺序，按依赖关系和功能重要性排列：

| 序号 | 计划文件 | 模块名称 | 优先级 | 预估工时 | 依赖 | 说明 |
|------|----------|----------|--------|----------|------|------|
| **01** | `frontend-perm-structure-analysis.plan.md` | 代码规范分析 | **P0** | 1-2天 | 无 | **必须最先执行**，建立编码规范基准 |
| **02** | `frontend-perm-tree-component-abstraction.plan.md` | Tree组件抽象 | **P1** | 2-3天 | 01 | 可复用组件，为后续页面提供基础设施 |
| **03** | `frontend-perm-completed-modules-improvement.plan.md` | 已完成模块完善 | **P1** | 2-3天 | 01, 02 | 修复现有代码问题，统一API规范 |
| **04** | `frontend-perm-domain-type-quick.plan.md` | 业务域与类型定义 | **P2** | 2-3天 | 01 | 基础数据管理，其他模块可能引用 |
| **05** | `frontend-perm-condition-module.plan.md` | 权限条件管理 | **P2** | 1-2天 | 01, 03 | 核心功能，角色权限配置可能依赖 |
| **06** | `frontend-perm-user-module.plan.md` | 抽象用户管理 | **P2** | 1-2天 | 01, 03 | 核心功能，用户角色分配的基础 |
| **07** | `frontend-perm-log-module.plan.md` | 日志审计 | **P3** | 1-2天 | 01, 03 | 可独立开发，无其他模块依赖 |
| **08** | `frontend-perm-explain-module.plan.md` | 权限排查诊断 | **P3** | 2-3天 | 01, 03, 13 | 依赖permissionView API完善 |
| **09** | `frontend-perm-conflict-module.plan.md` | 冲突规则管理 | **P3** | 2-3天 | 01, 05, 06 | 高级功能，依赖核心模块完成 |
| **10** | `frontend-perm-api-mapping-module.plan.md` | 资源API映射 | **P3** | 1-2天 | 01, 03 | 集成到资源管理页面 |
| **11** | `frontend-perm-version-module.plan.md` | 权限版本查询 | **P4** | 0.5-1天 | 01 | 系统功能，后端API有限 |
| **12** | `frontend-perm-system-config-module.plan.md` | 系统配置管理 | **P4** | 1天 | 01 | 系统功能，可独立开发 |
| **13** | `frontend-perm-view-improvement.plan.md` | 权限视图完善 | **P4** | 2-3天 | 01, 03, 06 | 补充完整API，提供子视图 |

### 优先级说明

| 优先级 | 含义 | 执行策略 |
|--------|------|----------|
| **P0** | 基础设施 | 必须最先完成，阻塞后续所有工作 |
| **P1** | 关键依赖 | 完成后才能开发核心功能模块 |
| **P2** | 核心功能 | 主要业务功能，应优先实现 |
| **P3** | 高级功能 | 可在核心功能完成后并行开发 |
| **P4** | 系统功能 | 优先级较低，可延后或并行 |

### 并行策略

以下计划可以并行执行（同一阶段的多个计划）：

**阶段2（P1）可并行：**
- 02 Tree组件抽象
- 03 已完成模块完善（部分工作）

**阶段3（P2）可并行：**
- 04 业务域与类型定义
- 05 权限条件管理
- 06 抽象用户管理

**阶段4（P3）可并行：**
- 07 日志审计
- 09 冲突规则管理
- 10 资源API映射

**阶段5（P4）可并行：**
- 11 权限版本查询
- 12 系统配置管理
- 13 权限视图完善

---

## 当前状态 vs 目标状态

### 模块完成情况对比

| 模块 | 后端API | 前端API | 前端页面 | 状态 | 所属计划 |
|------|---------|---------|----------|------|----------|
| **角色管理** | ✅ | ✅ | ✅ | 完成 | - |
| **资源管理** | ✅ | ✅ | ✅ | 完成 | - |
| **服务配置** | ✅ | ✅ | ✅ | 完成 | - |
| **用户角色分配** | ✅ | ✅ | ✅ | 完成 | - |
| **权限视图** | ✅ | ⚠️部分 | ⚠️部分 | 待完善 | `frontend-perm-view-improvement.plan.md` |
| **权限排查** | ✅ | ⚠️部分 | ⚠️占位 | 待完善 | `frontend-perm-explain-module.plan.md` |
| **业务域管理** | ✅ | ✅ | ❌ | 快速任务 | `frontend-perm-domain-type-quick.plan.md` |
| **类型定义** | ✅ | ✅ | ❌ | 快速任务 | `frontend-perm-domain-type-quick.plan.md` |
| **权限条件** | ✅ | ❌ | ❌ | 待开发 | `frontend-perm-condition-module.plan.md` |
| **抽象用户** | ✅ | ❌ | ❌ | 待开发 | `frontend-perm-user-module.plan.md` |
| **日志审计** | ✅ | ❌ | ❌ | 待开发 | `frontend-perm-log-module.plan.md` |
| **冲突规则** | ✅ | ❌ | ❌ | 待开发 | `frontend-perm-conflict-module.plan.md` |
| **资源API映射** | ✅ | ❌ | ❌ | 待开发 | `frontend-perm-api-mapping-module.plan.md` |
| **域配置** | ✅ | ⚠️部分 | ❌ | 快速任务 | `frontend-perm-domain-type-quick.plan.md` |
| **权限版本** | ✅ | ❌ | ❌ | 待开发 | `frontend-perm-version-module.plan.md` |
| **系统配置** | ✅ | ❌ | ❌ | 待开发 | `frontend-perm-system-config-module.plan.md` |

### API层完成情况

| API文件 | 当前状态 | 所属计划 | 说明 |
|---------|----------|----------|------|
| `role.ts` | ✅ 完整 | - | - |
| `resource.ts` | ✅ 完整 | - | - |
| `service.ts` | ✅ 完整 | - | - |
| `userRole.ts` | ✅ 完整 | - | - |
| `domain.ts` | ✅ 完整 | `frontend-perm-domain-type-quick` | 需补充域配置方法 |
| `operation.ts` | ✅ 完整 | `frontend-perm-domain-type-quick` | 类型定义页面复用 |
| `type.ts` | ✅ 完整 | `frontend-perm-domain-type-quick` | - |
| `resourceDependency.ts` | ✅ 完整 | - | - |
| `rolePermission.ts` | ✅ 完整 | - | - |
| `permissionView.ts` | ⚠️ 部分 | `frontend-perm-view-improvement` | 需补充完整方法 |
| `user.ts` | ❌ 缺失 | `frontend-perm-user-module` | 抽象用户管理 |
| `condition.ts` | ❌ 缺失 | `frontend-perm-condition-module` | 权限条件管理 |
| `log.ts` | ❌ 缺失 | `frontend-perm-log-module` | 日志审计 |
| `conflictRule.ts` | ❌ 缺失 | `frontend-perm-conflict-module` | 冲突规则管理 |
| `apiMapping.ts` | ❌ 缺失 | `frontend-perm-api-mapping-module` | 资源API映射 |
| `domainConfig.ts` | ❌ 缺失 | `frontend-perm-domain-type-quick` | 域配置管理 |
| `version.ts` | ❌ 缺失 | `frontend-perm-version-module` | 权限版本管理 |
| `systemConfig.ts` | ❌ 缺失 | `frontend-perm-system-config-module` | 系统配置管理 |

---

## 用户决策确认 ✅

| 决策项 | 选项 | 说明 | 执行计划 |
|--------|------|------|----------|
| **问题1** | **B** | Tree组件抽象成公共组件 `RePermissionTree` | `frontend-perm-tree-component-abstraction.plan.md` |
| **问题2** | **B** | 权限视图完整功能（4个子视图） | `frontend-perm-phase3-development.plan.md` |
| **问题3** | **A** | 业务域独立页面 `/perm/domain`（含域配置） | `frontend-perm-domain-type-quick.plan.md` |
| **问题4** | **A** | 类型定义独立页面 `/perm/type`（含操作权限） | `frontend-perm-domain-type-quick.plan.md` |
| **问题5** | **B** | 权限排查完整诊断系统（含可视化图表和权限对比） | `frontend-perm-phase1-development.plan.md` |

---

## 风险汇总

| 风险 | 影响阶段 | 可能性 | 影响 | 缓解措施 |
|------|----------|--------|------|----------|
| 后端API变更 | 所有阶段 | 低 | 高 | 开发前确认API稳定性 |
| 页面复杂度超预期 | Phase 1-3 | 中 | 中 | 按Task拆分，及时反馈 |
| 权限码命名冲突 | 所有阶段 | 低 | 中 | 统一命名规范 |
| 类型检查失败 | Phase 1-3 | 中 | 低 | 逐步修复类型问题 |
| 用户决策延迟 | Phase 0 | 中 | 高 | 提前准备决策选项 |

---

## 验收标准汇总

### Phase 0 验收标准

- [ ] 完成7个模块的规范审查
- [ ] 完成11个API文件的规范审查
- [ ] 识别所有规范偏差问题
- [ ] 明确标记需要用户决策的问题
- [ ] 生成结构优化建议文档

### Phase 1 验收标准（Tree组件抽象）

- [ ] `RePermissionTree` 组件创建完成
- [ ] `useTreeFilter` Hook抽取完成
- [ ] `RoleTree.vue` 和 `ResourceTree.vue` 重构完成
- [ ] 角色管理和资源管理页面功能正常
- [ ] 全量编译通过

### Phase 2 验收标准（已完成模块完善）

- [ ] 所有API文件使用内联类型导入语法
- [ ] `permissionView.ts` 包含完整的权限视图API
- [ ] 权限视图页面功能完整（多Tab视图）
- [ ] 权限排查页面功能完整（含可视化图表和权限对比）
- [ ] `constants/permission.ts` 包含所有需要的权限码
- [ ] 路由配置包含完整的权限绑定
- [ ] 全量编译通过，ESLint警告 < 10

### Phase 3+ 验收标准（各模块独立验收）

**业务域与类型定义** (`frontend-perm-domain-type-quick.plan.md`):
- [ ] `/perm/domain` 业务域管理页面功能完整（含域配置）
- [ ] `/perm/type` 类型定义管理页面功能完整（含操作权限）
- [ ] 权限码已补充
- [ ] 路由配置已更新
- [ ] 全量编译通过

**权限条件管理** (`frontend-perm-condition-module.plan.md`):
- [ ] `api/perm/condition.ts` 创建完成，包含完整CRUD
- [ ] `views/perm/condition/` 页面组件创建完成
- [ ] 权限码补充完整
- [ ] 路由配置更新完成
- [ ] 全量编译通过

**抽象用户管理** (`frontend-perm-user-module.plan.md`):
- [ ] `api/perm/user.ts` 创建完成，包含完整CRUD
- [ ] `views/perm/user/` 页面组件创建完成
- [ ] 权限码补充完整
- [ ] 路由配置更新完成
- [ ] 全量编译通过

**日志审计** (`frontend-perm-log-module.plan.md`):
- [ ] `api/perm/log.ts` 创建完成，包含两种日志查询
- [ ] `views/perm/log/` 页面组件创建完成
- [ ] 权限码补充完整
- [ ] 路由配置更新完成
- [ ] 全量编译通过

**权限排查** (`frontend-perm-explain-module.plan.md`):
- [ ] 查询模式选择（用户/角色）
- [ ] 完整的权限解释分析
- [ ] 来源角色追溯（via继承链）
- [ ] 近期变更历史展示
- [ ] 权限可视化图表（资源-操作矩阵）
- [ ] 权限对比功能（对比两个主体/角色的权限差异）
- [ ] 全量编译通过

**冲突规则管理** (`frontend-perm-conflict-module.plan.md`):
- [ ] `api/perm/conflictRule.ts` 创建完成
- [ ] `views/perm/conflict/` 页面组件创建完成
- [ ] 权限码补充完整
- [ ] 路由配置更新完成
- [ ] 全量编译通过

**资源API映射** (`frontend-perm-api-mapping-module.plan.md`):
- [ ] `api/perm/apiMapping.ts` 创建完成
- [ ] 资源管理页面添加API映射功能
- [ ] 权限码补充完整
- [ ] 全量编译通过

**权限版本管理** (`frontend-perm-version-module.plan.md`):
- [ ] `api/perm/version.ts` 创建完成
- [ ] `views/perm/version/` 页面组件创建完成
- [ ] 权限码补充完整
- [ ] 路由配置更新完成
- [ ] 全量编译通过

**系统配置管理** (`frontend-perm-system-config-module.plan.md`):
- [ ] `api/perm/systemConfig.ts` 创建完成
- [ ] `views/perm/system-config/` 页面组件创建完成
- [ ] 权限码补充完整
- [ ] 路由配置更新完成
- [ ] 全量编译通过

**权限视图完善** (`frontend-perm-view-improvement.plan.md`):
- [ ] `permissionView.ts` 补充完整所有API方法
- [ ] 权限视图页面包含所有子视图（有效角色、资源权限分布、近期变更、用户资源树）
- [ ] 权限码补充完整
- [ ] 路由配置更新完成
- [ ] 全量编译通过

---

## 附录

### 计划文件位置

**结构分析计划（2个）：**
```
.claude/PRPs/plans/
├── frontend-perm-structure-analysis.plan.md             # Phase 0: 代码规范分析
└── frontend-perm-completed-modules-improvement.plan.md # Phase 1: 已完成模块完善
```

**组件抽象计划（1个）：**
```
└── frontend-perm-tree-component-abstraction.plan.md    # Tree组件抽象
```

**模块开发计划（按功能模块拆分，10个）：**
```
├── frontend-perm-domain-type-quick.plan.md             # 业务域与类型定义
├── frontend-perm-condition-module.plan.md             # 权限条件管理模块
├── frontend-perm-user-module.plan.md                   # 抽象用户管理模块
├── frontend-perm-log-module.plan.md                    # 日志审计模块
├── frontend-perm-explain-module.plan.md                # 权限排查（诊断）模块
├── frontend-perm-conflict-module.plan.md               # 冲突规则管理模块
├── frontend-perm-api-mapping-module.plan.md            # 资源API映射模块
├── frontend-perm-version-module.plan.md                # 权限版本管理模块
└── frontend-perm-system-config-module.plan.md          # 系统配置管理模块
└── frontend-perm-view-improvement.plan.md              # 权限视图完善

**原始Phase计划（已拆分，供参考）：**
```
├── frontend-perm-phase1-development.plan.md            # Phase 1原始（已拆分为4个模块）
├── frontend-perm-phase2-development.plan.md            # Phase 2原始（已拆分为2个模块）
└── frontend-perm-phase3-development.plan.md            # Phase 3原始（已拆分为3个模块）
```

### 相关规范文件

```
.claude/rules/
├── frontend-coding-standards.md          # 前端编码规范
├── permission-center-coding-standards.md  # 权限中心业务规范
└── common/
    ├── coding-style.md                    # 通用编码规范
    ├── code-review.md                     # 代码审查标准
    ├── testing.md                         # 测试标准
    └── security.md                        # 安全标准
```

### 前端代码位置

```
frontend/src/
├── api/perm/                              # 权限中心API层
├── views/perm/                            # 权限中心页面
│   ├── role/
│   ├── resource/
│   ├── service/
│   ├── user-role/
│   ├── view/
│   ├── explain/
│   └── ... (待创建)
├── router/modules/perm.ts                 # 权限中心路由
└── constants/permission.ts                # 权限码常量
```
