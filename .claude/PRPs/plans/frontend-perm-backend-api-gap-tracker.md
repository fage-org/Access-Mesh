# Permission Center Frontend / Backend API 契约差异跟踪

## 目的

本文件用于记录当前前端计划与后端真实 API 契约之间的差异，以及后续如需后端增强时的改造方向。

适用原则：

- 当前前端实现优先以现有后端 Controller / DTO 为准。
- 能通过前端包装层解决的差异，不阻塞当前前端落地。
- 只有无法由前端单独补齐的能力，才进入后端增强清单。

---

## 使用方式

1. 前端实现时：把本文件当作“哪些差异由前端适配”的说明文档。
2. 后端改造时：把本文件当作增强 backlog 的输入来源。
3. 文档维护时：如果某项后端增强已落地，应同步更新对应前端计划并关闭本文件中的差异记录。

---

## 差异清单

| 模块          | 当前前端处理                                                                                                                              | 当前后端契约事实                                                                                                                 | 后续后端可选增强                                                                                                                 |
| ------------- | ----------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| 权限条件      | 前端保留“条件模板/分类”体验，但提交字段严格使用 `code`、`name`、`conditionRules`、`enabled`、`description`                                | 后端无 `conditionType`、`status`、`updatedAt` 等字段                                                                             | 如需强类型条件模型，可考虑新增 `conditionType`、模板元数据或结构化规则定义                                                       |
| 抽象用户      | API 层使用 `name`、`enabled`、`userId`、`version`；页面可用更友好的标签展示                                                               | 后端无 `username`/`status` 字段拆分；sync 必填 `version`                                                                         | 如业务需要区分登录名/显示名，可新增 `username` 或服务端自动管理 sync version                                                     |
| 冲突规则      | 当前只做规则级 CRUD 和 detect；检测请求按操作权限对处理                                                                                   | 后端 detect 仅支持 `firstOperationPermissionId`、`secondOperationPermissionId`、`resourceTypeValue`                              | 如需用户/角色级冲突诊断，可新增主体维度 detect API 和更丰富的建议返回结构                                                        |
| 资源 API 映射 | API 层严格使用 `pathPattern`、`matchOrder`、`enabled`、`extra`；展示字段由资源页上下文补齐                                                | 后端无 `requirePermission`、`description` 等一等字段                                                                             | 如需更强展示语义，可新增描述字段、权限要求标签或资源摘要信息                                                                     |
| 系统配置      | 分类、只读态、展示名由前端元数据维护；接口仍使用 `save` / `detail` / `list`                                                               | 后端 list 不支持 `category` / `keyword` 过滤；响应无 `category`、`editable`                                                      | 如需服务端统一配置中心视图，可新增 `category`、`editable`、条件过滤能力                                                          |
| 权限视图      | 当前按后端真实接口建模，页面可做前端包装（本地分页、快捷筛选、文案映射）                                                                  | `effective-roles`、`resource-users`、`resource-tree`、`recent-changes` 的请求/响应均比原计划更“窄”                               | 如需 `via/source`、用户级资源分布、按天快捷过滤等 richer 能力，可新增专门响应字段或独立接口                                      |
| 权限排查      | 当前前端只按 `/explain` 的单次判定能力实现 explain 页面，展示 `allowed`、`reason`、`sourceRoles`、`matchedPermissionIds`、`recentChanges` | 后端当前只有 `POST /api/perm/permission-view/explain`，请求 / 响应只覆盖单次权限判定，无矩阵视图、双主体对比或跨请求聚合诊断契约 | 如需资源-操作矩阵、双主体 / 双角色对比、跨接口聚合诊断，可新增专门 explain aggregation / compare 接口或更丰富的 explain 响应结构 |
| 权限版本      | 当前仅实现 `/query` 和可选轻量页面                                                                                                        | 后端只有 `/query`，无 `list/detail/create/compare/rollback`                                                                      | 如需快照管理、对比、回滚，需要扩展 `PermissionVersionController`                                                                 |

---

## 后续改造优先级建议

### P1：当前前端体验受限明显

- 权限视图增强字段（尤其是有效角色来源链、资源权限主体分布）
- 权限排查的矩阵视图、双主体 / 双角色对比与聚合诊断接口
- 系统配置服务端分类与只读态
- 冲突规则用户/角色级诊断接口

### P2：产品能力增强

- 权限版本快照、对比、回滚
- 权限条件强类型模型
- 抽象用户的展示名 / 登录名拆分与 sync version 自动管理

### P3：展示语义增强

- 资源 API 映射描述信息和额外展示字段

---

## 关联文档

- `README.md`
- `frontend-perm-condition-module.plan.md`
- `frontend-perm-user-module.plan.md`
- `frontend-perm-conflict-module.plan.md`
- `frontend-perm-api-mapping-module.plan.md`
- `frontend-perm-system-config-module.plan.md`
- `frontend-perm-explain-module.plan.md`
- `frontend-perm-view-improvement.plan.md`
- `frontend-perm-version-module.plan.md`
