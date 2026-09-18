# 外部业务服务 sync / full-sync 运维手册（runbook）

> 适用对象：接入 AccessMesh 的**外部业务服务**（如 hr-service、bi-service）的同步运维人员与平台管理员。
> 契约权威：`docs/design/access-service-api-contract.md`（契约总册）§19.1~§19.6、§19.8；安全边界：`docs/design/access-service-architecture.md` §6.2。
> 产出任务：T-PERM-021 F1.e（2026-09-12）。内部 admin→permission full-sync 编排已随 T-ACCESS-005 删除——管理事实由 access.application 同事务本地投影维护，**不经本手册任何接口**。

## 1. 通道总览与选择

| 通道 | 接口 | 用途 | 语义 |
|---|---|---|---|
| 资源实体逐条 | `POST /api/access/resource-entity/sync` | 外部对象 ↔ 自有资源类型实例（UPSERT/DISABLE/DELETE） | 幂等单条 |
| 资源实体全量 | `POST /api/access/resource-entity/full-sync` | scope 内全量校准 | 补缺失 + 清多余 |
| 主体/角色/成员逐条 | `POST /api/access/abstract-user/sync`、`abstract-role/sync`、`user-role/sync` | 主体/角色 UPSERT/DISABLE/DELETE、成员 BIND/UNBIND | 幂等单条 |
| 主体/角色/成员全量 | 对应 `/full-sync` | scope 内全量校准 | 补缺失 + 清多余 |
| 服务接口清单 | `POST /api/access/service-config/sync`（§6.3） | API 清单登记（service-config 声明通道） | 全量替换 |

**选择规则**：常态增量事件 → 逐条 `sync`；对账/初始化/源侧发生过批量修复 → `full-sync`（单请求 = scope 内完整事实声明，**缺失即删除**）。组织树等有树依赖的数据按足够小的 scope 拆分调用，避免单请求过大。

## 2. 触发前置检查清单

- [ ] **类型声明**（资源实体通道）：目标 `resourceTypeCode` 已经 `type-definition/create` 建为自有类型，且 `extra.managedMode=SYNC`、`extra.syncSourceService=<本服务>`。门禁不满足一律 `SECURITY_DENIED`/`RESOURCE_TYPE_OWNERSHIP_DENIED`（类型不存在同码 fail-closed，真实原因只在服务端日志）。事实链路七类型 `USER/ORG/MENU/ROLE/ADMIN_FILE/TYPE_DEFINITION/CONDITION` 为 access-service 内部独占，外部一律入口拒绝。
- [ ] **主体/角色/成员通道**：`subjectTypeCode`/`roleTypeCode`/`sourceType` 均为本服务自有类型，且命中 service-config `extra.syncTypes` 三维白名单（subjectTypeCodes/roleTypeCodes/sourceTypes）；保留键 `LOCAL_USER`、`ORG|POSITION`、`SYS_USER_ORG` 一律 20045 拒绝。
- [ ] **服务注册与凭证**：本服务在 service_config 注册、未软删、`status=1` 启用；服务凭证已配置并生效（凭证通过后绑定 `X-Service-Code`，`sourceService` 必须与之一致，不匹配 `SECURITY_DENIED`）。服务停用/注销 = 四个同步通道一起断。
- [ ] **scope 规划**（full-sync）：明确 `scope`（资源实体=`sourceService+resourceTypeCode`；主体=`subjectTypeCode`；角色=`roleTypeCode+treeRootExternalId`；成员=`sourceType+roleTypeCode+treeRootExternalId`）。scope 是删除边界——**请求中缺失的同步事实会被软删/解绑**。
- [ ] **租户备份**：full-sync 前对目标租户 `resource_entity`/`abstract_user`/`abstract_role`/`user_role` 做快照或备份（见 §6 回滚）。

## 3. 执行步骤

1. 逐条 sync：按事件顺序发送；同幂等键乱序到达由服务端 `sync_metadata` 版本原子比较兜底（旧版本 no-op，见 §4）。
2. full-sync：构造单请求完整事实清单（含每个 item 的 `syncVersion`）。父边限同类型（T-PERM-068）：`parentResourceTypeCode` 缺省即按 item/scope 类型解析（同步同类型树只需传 `parentResourceCode`），**显式传异类型会被 `NON_RETRYABLE`/`PARENT_TYPE_MISMATCH` 拒绝**——不要为「不同类型的父」补传该字段；仅 `parentCodeType≠default` 时需显式传 codeType。父字段组仅 UPSERT 生效（DISABLE/DELETE 忽略父字段）；单条 DELETE 在存在有效子资源时返回 `DEPENDENCY_MISSING`/`CHILDREN_EXIST`（先删子再重发父，同版本重发自愈）。
3. 解析响应：**信封恒 `code=200`，勿以信封判失败**——失败判定 = `data.accepted=false || data.stale=true`（2026-09-12 契约勘误口径）。
4. full-sync 逐 item 核对 `data.detail.itemResults`（businessKey 级明细）；sync 接口 `data.detail=null`，只看顶层。
5. 对 `RETRYABLE`/`DEPENDENCY_MISSING` 项按 §4 表重发；对 `NON_RETRYABLE` 项修正请求或源数据后重发——环路/互斥/依赖类拒绝不推进同步版本，修正后同版本重发不会被 STALE 挡；推进 `syncVersion` 作为新事件发送是可选保险（便于区分修复轮次）。

## 4. 响应分类与重试决策表

| retryClass | 含义 | 重试策略 |
|---|---|---|
| `RETRYABLE` | 瞬时失败（部分失败时顶层 `FULL_SYNC_PARTIAL_FAILURE`） | 指数退避重发**原请求**（同版本原样重发不会被 STALE 挡） |
| `DEPENDENCY_MISSING` | 父资源/关联角色不存在（依赖与判环先于版本写入，不推进版本）；资源 DELETE 命中有效子资源（`CHILDREN_EXIST`，T-PERM-068） | 短退避重发原请求；持续失败先补齐依赖侧 sync；`CHILDREN_EXIST` 先删子资源再重发父（同版本重发自愈） |
| `STALE_VERSION` | 旧版本 no-op（唯一 `accepted=true` 的失败：`applied=false, stale=true`） | **不重试**——服务端已持更新事实，调度器置 SUCCESS |
| `NON_RETRYABLE` | 参数/结构错误：父环路 `RESOURCE_PARENT_INVALID`、跨类型父边 `PARENT_TYPE_MISMATCH`（父类型码 ≠ item/scope 类型，修正源数据）、成员关系互斥 `ROLE_MUTEX_CONFLICT`、保留键 20045 等 | 不盲目重试；修正请求/源数据后重发（此类拒绝不推进同步版本，同版本重发不会被 STALE 挡；推进版本作新事件为可选保险） |
| `SECURITY_DENIED` | 服务身份不匹配 / 类型所有权门禁拒绝 / 白名单未命中 / 未注册停用 | **先排根因再动**：核对类型声明、syncTypes 白名单、服务注册状态；排除配置前重发只会继续被拒 |

## 5. 验收检查

- [ ] `detail.appliedCount + staleCount + failedCount = items 总数`，`deactivatedCount` 与预期的「源侧已删对象数」一致。
- [ ] 抽查 `sync_metadata`（`source_service/scope_key/sync_key_hash/last_sync_occurred_at/last_sync_sequence_no`）与源侧最新事件版本一致。
- [ ] 管理面对应查询（资源树 / 主体 / 角色 / 成员关系）所见与源侧一致；被 full-sync 清理的对象在管理面已消失（软删）或成员已解绑。
- [ ] 鉴权面冒烟：依赖该批事实的授权判定（或网关接口检查）符合预期。

## 6. 回滚与误删恢复

- **full-sync 不可直接回滚**（删除语义是声明式结果，无事务级逆操作）。恢复手段 = 反向补数据：从备份/源系统导出被误删对象，按逐条 `sync`（UPSERT）重新写入，**syncVersion 必须大于等于历史最高序**。
- 误删影响面：full-sync 只清理 `sync_metadata` 命中 scope 的同步事实，不触碰 MANUAL 管理面数据与其他通道（`service-config/sync` 是独立 ownership 通道）——恢复时同样只影响本 scope。
- `DISABLE`/`DELETE` 单条误操作：以新的 UPSERT + 更高版本覆盖恢复（软删行复活走同幂等键 upsert）。

## 7. 常见故障与处置

| 现象 | 根因方向 | 处置 |
|---|---|---|
| 全量 `SECURITY_DENIED`/`RESOURCE_TYPE_OWNERSHIP_DENIED` | 类型未声明 SYNC / 声明来源非本服务 / 类型不存在 / 服务未注册或停用 | 核对 type-definition extra 与 service_config；修配置后原请求重发 |
| `DEPENDENCY_MISSING` 长期不消 | 父资源/关联角色从未同步 | 先对依赖侧跑 sync/full-sync，再重发 |
| 明明有新数据却 `stale=true` | 调用方时钟回拨或序号生成器重置，`syncVersion` 不单调 | 修版本源；以更高 `sequenceNo` 重发 |
| 成员 BIND `NON_RETRYABLE` + `ROLE_MUTEX_CONFLICT` | 目标用户已持互斥角色（T-PERM-063/064 授权时校验） | 业务侧解绑互斥角色后再发；这不是平台故障 |
| 批量 `RETRYABLE` 持续失败 | access-service 或存储异常 | 查服务端日志与健康端点，恢复后原请求重发（幂等安全） |
| NON_RETRYABLE 永久放弃的后果 | 该事实在平台内**永久缺失**（如新员工 UPSERT 失败 → 该员工所有鉴权拒绝） | 放弃前必须确认影响并留档；恢复=修正后推进版本重发 |

## 8. 依据锚点

- 响应信封恒 200 + 失败以 `accepted/retryClass` 判定：契约总册 §19.3（2026-09-12 勘误，随扩展指南外评修正）。
- 类型级所有权门禁与七内部类型：契约总册 §19 规则条 + T-PERM-052 定案（decision-registry 2026-09-05 行）。
- full-sync ownership 以 `sync_metadata` 为准、两记录列不作清理依据：契约总册 §19.1/§19.2 + access-service-architecture §4.3（T-PERM-021 F1.c 定案 2026-09-12）。
- 角色/资源同步互斥守卫（BIND 逐条 `ROLE_MUTEX_CONFLICT`）：T-PERM-063/064（decision-registry 2026-09-12 两行）。
- 服务间认证与凭证绑定：契约总册 §19.5、access-service-architecture §6.2。
