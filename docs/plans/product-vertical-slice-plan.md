---
doc_type: plan
title: 产品垂直切片与试点加固计划
status: proposed
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/schema/access-service.sql
  - docs/design/permission-center/api-contract.md
  - docs/design/permission-center/implementation.md
  - docs/design/services/admin-service-api-contract.md
  - docs/design/services/gateway.md
tasks:
  - T-ACCESS-016
  - T-ACCESS-017
  - T-PERM-042
  - T-ORG-001
  - T-ACCESS-018
  - T-ACCESS-019
  - T-ACCESS-020
  - T-FE-041
  - T-ACCESS-021
  - T-ADMIN-022
  - T-PERM-043
  - T-ADMIN-023
  - T-GW-007
  - T-ACCESS-024
  - T-ACCESS-025
  - T-ADMIN-024
  - T-API-001
  - T-ACCESS-026
acceptance: "里程碑 A（核心可运行，与 T-ACCESS-021 固定 8 步一致）：空库启动 → 首管理员（bootstrap 管理角色）真实登录 → 创建目标用户与普通 BASIC_ROLE 并分配给目标用户（角色空权限）→ 为目标接口创建 API 映射 → 目标用户实测 403 → 授权页授予 API:ACCESS → 实测 200 → 重启后仍 200 → 撤权后 30 秒内恢复 403；全过程一套用户 ID、一套资源类型、一套授权事实源；README 状态随 E2E 结论回写。里程碑 B（试点加固）：本计划 18 项任务全部 done 或经确认 cancelled，验证证据登记、access-post-merge-plan 归档。里程碑 B 任务不反向阻塞里程碑 A 的达成声明。"
last_updated: 2026-08-23
---

# 产品垂直切片与试点加固计划

> 来源：2026-08-22 风险基线全量核实 + 身份与资源模型方案拍板（B-lite 统一本地主体 ID）立项；2026-08-23 按评审结论修订（resource_entity.id 边界收窄、bootstrap/E2E 双角色双用户模型、模型任务顺序调整、CI 前移、GROUP_ROLE 直删、里程碑 A/B 拆分、T-FE-042 并入 T-FE-041）；同日补充修订（里程碑 B 硬门禁、类型收敛范围排除操作日志 targetType、bootstrap 载体定稿、E2E 页面场景与 initialPassword 定稿、example 改 Gateway 主线）。

## 目标

总目标（唯一产品验收口径，达成点=里程碑 A）：

> 从全新空库开始，管理员能够真实登录，创建普通 BASIC_ROLE 并分配给目标用户，将一个已映射 API 授给该角色，经 Gateway 从 403 变为 200，并在回收后 30 秒内恢复 403；全过程只有一套用户 ID、一套资源类型和一套授权事实源。

分项目标：

1. **身份与资源模型收敛**：统一本地主体 ID（B-lite，`abstract_user.id` 唯一主体 ID、本地用户 `sys_user.id = abstract_user.id`、删除 `OperatorSubjectResolver`）、资源类型五组合并（USER/ROLE/MENU/SYSTEM_CONFIG/ORG）+ 引擎显式资源 API（code 与 entityId 分离）。
2. **空库可用**：一键基础设施 + 幂等 bootstrap（固定租户 1 + 首管理员 + 管理用功能角色，按 bootstrap 管理 API 清单双层最小授权；不向目标角色/目标用户预授目标 API——首管理员仅预持目标 API 的 ACCESS+canGrant 供授权传递，且该 API 无映射、自身也无法调用）。
3. **前端真实登录**：脱离 mock 登录链路，统一信封，打通登录 + 主页 + 一个授权场景；默认导航收敛。
4. **首条授权垂直切片 E2E 验收**：目标用户由 E2E 场景内经管理链路创建，不以"测试全绿"替代。
5. **试点加固**：登录锁定、文件服务、Gateway CORS、时区、操作日志、退役 API、GROUP_ROLE 写入口删除。
6. **example 单接口接入**：以 Gateway 主线证明业务服务接入路径（接口级鉴权由 Gateway 承担，服务内不重复鉴权），Starter 名实对齐。
7. **文档与验证证据收口**：验证证据登记、access-post-merge-plan 归档、状态字段一致性复核。

## 非目标

以下全部**不做**（有真实消费者后再评估立项）：

- OAuth2 业务资源授权解锁（scope 与用户权限交集、delegated user 过引擎等未决产品决策冻结）。
- PERSONAL、GROUP_ROLE 完整交付（GROUP_ROLE 写入口直接删除；未来按 `role_inclusion` 单事实源设计另行立项）。
- 业务域 CLASSIFY 三模式扩展、自动授权、动态数据权限、example 报表数据范围。
- 调度租约与多实例能力增强（现有代码保留冻结）。
- 可观测性平台建设（dashboard/告警编排/日志采集）。
- 全局统一对象 ID 中心、全平台共享序列、跨业务表的 `SysUserId`/包装类型双轨体系。
- 在线双读迁移或兼容别名双写：模型收敛按空库重建模式实施（项目无存量数据，权威 DDL 重建）。
- 缓存框架重设计：`CacheService`/`beginRead`/剩余 TTL/30 秒边界机制保留且零修改；主体键缓存本以 `abstract_user.id` 为标识符，统一后数值与键格式均不变，不新增迁移专用 evictAll/兼容键。
- 文件对象存储抽象层与通用清理调度平台（仅修安全缺陷 + 明确单实例约束）。
- 为 example-service 建设第二套管理 UI。
- 平台超管跨租户代管、硬编码超级用户旁路（bootstrap 首管理员走租户内管理用功能角色）。

## 准入条件

1. **管线冻结**：自本计划首个任务 in-progress 起，身份、资源、权限管线相关的新功能开发冻结至 T-ORG-001 done；其它计划（frontend-phase2 等）中触碰该管线的任务暂缓或与本计划协调顺序。
2. **安全网与 CI 先行**：模型重构（T-PERM-042 起）开始前，T-ACCESS-017 特征测试全绿且最小 CI 落地（单测强制、容器门控 PR/手动触发）。
3. **CI 落地前的测试补偿**：T-ACCESS-017 完成前，每次提交前由执行者手动执行全量单测 + 容器门控（9 类 68 项 Testcontainers，2026-08-22 基线 `7b7cf3254` 已在外部 Docker 主机验证可真实执行；68 为历史验证证据，CI 以退出状态判定成功，不维护计数同步）。
4. 模型收敛三任务（T-PERM-042 / T-ORG-001 / T-ACCESS-018）各自独立提交分批合入，禁止一次提交同时修改身份、资源类型、引擎与缓存。
5. **里程碑硬门禁**：里程碑 A 完成前（T-ACCESS-021 未 done）不启动任何里程碑 B 任务——全部 B 任务 `depends_on: T-ACCESS-021` 硬约束，T-ACCESS-026 依赖全部 B 任务与 T-API-001；仅已确认的紧急安全缺陷允许插队，插队须在本计划登记原因与范围。防止"核心链路未跑通先做日志、时区、CORS、文件加固"。

## 里程碑与阶段拆分

### 里程碑 A：核心可运行（达成即可声明"核心垂直切片完成"）

```text
T-ACCESS-016 设计定稿 ∥ T-ACCESS-017 窄回归安全网 + 最小 CI
        ↓
T-PERM-042   引擎显式资源 API（code/entityId 分离，实例门禁调用改造）
        ↓
T-ORG-001    统一本地主体 ID（B-lite，删除 OperatorSubjectResolver）
        ↓
T-ACCESS-018 资源类型收敛（五组合并 + 双常量合一 + 前端权限串）
        ↓
T-ACCESS-019 USER/ROLE 全写路径同事务投影（最终主体 ID + 最终类型码，一次到位）
        ↓
T-ACCESS-020 空库 bootstrap（首管理员 + 管理用功能角色）
        ↓
T-FE-041     前端真实登录 + 默认导航收敛
        ↓
T-ACCESS-021 BASIC_ROLE 授权垂直切片 E2E 验收 + README 状态回写
```

顺序依据：先统一主体 ID 再做类型收敛与投影，投影直接使用最终主体 ID 和最终类型码，同一条投影链只写一次，无过渡转换层；引擎语义测试（手工装配投影 fixtures）与生产写路径投影测试分属 T-PERM-042 / T-ACCESS-019 两级验收。

### 里程碑 B：试点加固（不反向阻塞里程碑 A；全部 B 任务硬依赖 T-ACCESS-021）

```text
T-ADMIN-022  登录锁定临时化与账号状态语义统一   ← T-ORG-001, T-ACCESS-021
T-PERM-043   GROUP_ROLE 写入口删除与前端隐藏     ← T-ACCESS-019, T-ACCESS-021
T-ADMIN-023  文件服务安全加固                    ← T-ACCESS-021
T-GW-007     Gateway CORS 环境化与 actuator 收口  ← T-ACCESS-021
T-ACCESS-024 时间语义 UTC 统一                    ← T-ACCESS-021
T-ACCESS-025 操作日志收敛                        ← T-ACCESS-021
T-ADMIN-024  退役 API 直接删除                    ← T-ACCESS-021
        ↓
T-API-001    example 单受保护接口接入（Gateway 主线）← T-ACCESS-021
T-ACCESS-026 验证证据与文档状态收口               ← 全部里程碑 B 任务 + T-API-001
```

加固任务互不阻塞，但避免与同链路任务并发（T-ADMIN-022 与 T-ORG-001 同触 AuthService/用户写链路故串行；T-PERM-043 与 T-ACCESS-019 同触 RoleManageAppServiceImpl 故串行）。

跨计划引用：63 位掩码 JSON 精度问题归属既有任务 [T-PERM-028](../tasks/T-PERM-028.md)（frontend-phase2），本计划不重复立项；建议在里程碑 B 前完成以免前端授权页联调返工。

## 任务卡编制硬约束（防过度设计 / 防局部最优）

每张任务卡必填：解决的具体风险、直接依赖、修改范围与明确非目标、验收标准；数据模型/API 变化、需更新的权威文档、删除或保留的旧入口、回滚或空库重建策略按适用性填写（不适用不填，避免文档形式主义）；测试层声明适用的最小层（不适用项注明原因即可、不补无价值测试）。

1. 没有三个真实调用方，不新增通用框架。
2. 没有真实消费者，不解锁 OAuth2、GROUP_ROLE、PERSONAL、动态数据权限。
3. 没有存量调用方，不创建兼容层，直接删除旧入口。
4. 一个关系只能有一个事实源，禁止数据库关系表与 JSON 同时权威。
5. 一个本地用户只能有一个公开主体 ID。
6. `resource_entity.id` 边界：USER/ROLE 等业务对象门禁与跨服务 SDK 不得使用（统一业务 code/externalId）；权限域内部及直接管理资源实体的后台接口（资源树、API 映射、资源依赖、权限树等 resource_entity 自身的管理链路）允许继续使用，现有契约 DTO 不因此重构。
7. 新增工程化功能必须回答：是否阻断当前 E2E、是否修复安全/数据损坏风险。
8. 每项功能最多一次设计复核和一次实现复核，禁止无限评审轮次。
9. 完成标准是用户场景跑通，不是类、接口和测试数量。
10. 投入配比约 70% 核心链路、20% 安全与测试、10% 文档与最小运维。

## 任务清单

| ID | 标题 | 状态 | 直接依赖 | 里程碑 |
|---|---|---|---|---|
| [T-ACCESS-016](../tasks/T-ACCESS-016.md) | 身份与资源模型设计定稿（B-lite 终态 + 类型收敛映射 + 引擎显式 API 契约 + 首管理员权限模型） | ✅ | — | A |
| [T-ACCESS-017](../tasks/T-ACCESS-017.md) | 窄回归安全网与最小 CI | ✅ | — | A |
| [T-PERM-042](../tasks/T-PERM-042.md) | 权限引擎显式资源 API 与实例门禁修复 | ✅ | T-ACCESS-016, T-ACCESS-017 | A |
| [T-ORG-001](../tasks/T-ORG-001.md) | 统一本地主体 ID（B-lite：共享主体 ID，删除 OperatorSubjectResolver） | ✅ | T-PERM-042 | A |
| [T-ACCESS-018](../tasks/T-ACCESS-018.md) | 资源类型收敛（五组合并 + 双常量合一 + 前端权限串） | ✅ | T-ORG-001 | A |
| [T-ACCESS-019](../tasks/T-ACCESS-019.md) | USER/ROLE 全写路径同事务资源投影 | ✅ | T-ACCESS-018 | A |
| [T-ACCESS-020](../tasks/T-ACCESS-020.md) | 空库 bootstrap（一键基础设施 + 幂等首管理员种子） | ⚙️ | T-ACCESS-019 | A |
| [T-FE-041](../tasks/T-FE-041.md) | 前端真实登录链路与默认导航收敛 | ⚙️ | T-ACCESS-020 | A |
| [T-ACCESS-021](../tasks/T-ACCESS-021.md) | BASIC_ROLE 授权垂直切片 E2E 验收 + README 回写 | ⚙️ | T-ACCESS-020, T-FE-041 | A |
| [T-ADMIN-022](../tasks/T-ADMIN-022.md) | 登录锁定临时化与账号状态语义统一 | ⚙️ | T-ORG-001, T-ACCESS-021 | B |
| [T-PERM-043](../tasks/T-PERM-043.md) | GROUP_ROLE 写入口删除与前端隐藏 | ⚙️ | T-ACCESS-019, T-ACCESS-021 | B |
| [T-ADMIN-023](../tasks/T-ADMIN-023.md) | 文件服务安全加固（VIEW 门禁 + 路径安全 + 删除顺序） | ⚙️ | T-ACCESS-021 | B |
| [T-GW-007](../tasks/T-GW-007.md) | Gateway CORS 环境化与 actuator 暴露收口 | ⚙️ | T-ACCESS-021 | B |
| [T-ACCESS-024](../tasks/T-ACCESS-024.md) | 时间语义 UTC 统一（TypeHandler/JDBC/JVM） | ⚙️ | T-ACCESS-021 | B |
| [T-ACCESS-025](../tasks/T-ACCESS-025.md) | 操作日志收敛（默认不序列化参数，复用 summary 摘要） | ⚙️ | T-ACCESS-021 | B |
| [T-ADMIN-024](../tasks/T-ADMIN-024.md) | 恒拒绝退役 API 直接删除 | ⚙️ | T-ACCESS-021 | B |
| [T-API-001](../tasks/T-API-001.md) | example 单受保护接口接入（Gateway 主线）与 Starter 名实对齐 | ⚙️ | T-ACCESS-021 | B |
| [T-ACCESS-026](../tasks/T-ACCESS-026.md) | 验证证据登记与文档状态收口（含 post-merge 归档） | ⚙️ | 全部 B 任务 + T-API-001 | B |

> T-FE-042（前端默认导航收敛）已取消：范围并入 [T-FE-041](../tasks/T-FE-041.md)（同为前端发布面，避免任务碎片化），看板保留 cancelled 记录。

## 归档条件

- 里程碑 A 与里程碑 B 均达成：18 项任务全部 `done` 或经确认 `cancelled`。
- T-ACCESS-021 总目标 E2E 验收通过且证据登记，README 已随结论回写。
- 身份与资源模型稳定结论已沉淀至 `docs/design/`（access-service-architecture / schema / api-contract / admin-service-api-contract），本计划不残留第二套契约。
- access-post-merge-plan 已按其归档条件收口归档（CI 准入前置由 T-ACCESS-017 最小 CI 落地关闭）。

## 当前进度

- 2026-08-22 立项：19 项任务全部 ⚙️ proposed。
- 2026-08-23 评审修订：resource_entity.id 边界收窄（资源实体管理链路豁免）、bootstrap/E2E 双角色双用户模型定稿、模型顺序调整为 042 → T-ORG-001 → 018 → 019（投影一次到位）、最小 CI 前移并入 T-ACCESS-017、T-ACCESS-017 改特征测试（缺陷预期测试移入 T-PERM-042 同提交转绿）、T-ORG-001 去除 evictAll 迁移（缓存零修改）、T-ADMIN-022 计数键即锁（不新增 lockUntil）、GROUP_ROLE 改直删写入口（无新错误码）、T-API-001 接入主线更正 + POM 瘦身、里程碑 A/B 拆分、T-FE-042 并入 T-FE-041（cancelled）。当前 18 项 ⚙️。
- 2026-08-23 补充修订：里程碑 B 硬门禁（B 任务全部 depends_on T-ACCESS-021、T-ACCESS-026 依赖全部 B + T-API-001）；类型收敛范围排除操作日志 targetType（物理表名契约，两个命名空间）；bootstrap 载体定稿（固定租户 1、access-service 内默认关闭幂等 runner、复用领域服务与 BCrypt、compose 仅基础设施、最小管理权限清单归 T-ACCESS-016）；E2E 页面场景定稿（授权页授予/回收单场景 + UserCreateResp.initialPassword 取令牌）；example 改 Gateway 主线（不消费 perm-client 则删依赖，修正 architecture.md 能力表）；文字收口（016 缓存口径对齐零修改、025 白名单残留清理、043 错误码更正 20022、GW-007 固定独立管理端口、根 README 即时真实化、计划仅保留 frontmatter 状态、017 回写不适用）。
- 2026-08-23 闭环修订：bootstrap 授权链闭合（Gateway 空快照默认拒绝 + canGrant 授权传递两个先有鸡问题，经最小种子解决）——T-ACCESS-016 定稿 bootstrap 管理 API 清单（双层最小授权）与 E2E 目标接口 POST /admin/role/my-info（仅预建资源+预授 canGrant 用权限、不建映射）；T-ACCESS-020 补种子执行与幂等语义（事务化 initializer、业务键 create/no-op、单事务、fail-fast、单实例）；T-ACCESS-021 场景对齐（真实创建 my-info 映射、页面授予/runbook 回收固定分工）；GW-007 主端口删除全部 actuator 口径收口；T-FE-041 场景与导航确定清单；T-ACCESS-017 补 CI 真实运行证据；README 特性表真实化。
- 2026-08-23 契约修订：真实登录契约补全（LoginReq 四要素+验证码强校验、tenantId=1/clientId=admin-web、expiresIn 转绝对时间、不接刷新令牌、复用 _envelope.unwrap 不改全局解包、删默认账号密码）；Gateway 唯一外部路径约定（/auth/**、/admin/**、/perm/api/perm/**、/example/**，vite 同路径转发，仅切换登录+授权页消费模块）；bootstrap 管理 API 清单补授权页初始化读接口；幂等口径统一（业务键 create/no-op、占用即 fail-fast、专用写入组件不加公开无操作者入口）；导航口径改"里程碑 A 只显示冒烟通过的页面"；测试要求改最小适用层；CI 证据以 GHA 两 job 真实跑通为准（外部主机基线为历史事实）；README 架构图与两处索引的旧口径（SDK 参考实现、40 项）真实化。
- 2026-08-23 验收链路修订：E2E 固定 8 步顺序（角色分配先于授权、403 断言先于授权真实执行、撤权单调计时轮询）；bootstrap 管理角色业务门禁最小集明确到 operation/scopeMode/实例；bootstrap 幂等改三状态口径（完整存在整体 no-op / 部分残缺启动失败报告冲突）；T-FE-041 模块清单收敛为授权页实际消费五模块、新增 401 窄处理规则；任务卡规范改必填+按适用填写；索引旧"40 项"口径统一。计划自此冻结扩展，进入执行。
- 前置事实基线：代码基线 @ `7b7cf3254`（计划与任务卡文档修改使工作区非 clean）；外部 Docker 主机已真实执行 9 类 68 项 Testcontainers 门控（修复链 `c8dc06c52` → `b9f48bb39` → `7b7cf3254`，构成历史验证基线）。
- 2026-08-23 执行：T-ORG-001 done——主体 ID 统一（序列预取 + sys_user/abstract_user 同 ID 双表插入，Flex 主键策略适配 KeyType.None/insertWithExplicitId）；OperatorSubjectResolver 与 resolveOperatorSubjectId 全量删除清零（82 处调用 + Javadoc）；空库重建 runbook 落地（含 Redis 清理）；双层全绿（单测 674 + 容器 65，新增 LocalSubjectIdUnificationPgIT）。T-ACCESS-018 依赖解除。
- 2026-08-23 执行：T-ACCESS-018 done——DDL 种子按 §13 终值重编（resource_type 28→23、operation_permission 139→117，LOCAL_USER 更名、ORG=29、USER:ENABLE bit32）；ResourceTypeCode 扩为 23 码单一常量源、AdminResourceType 删除（约 26 文件切换）、AdminOperationCode.GRANT/REVOKE 删除；subject 保留键 ADMIN_USER→LOCAL_USER（含登录会话与全链路投影解析）；resource 侧取消类型级保留、外部 sync mutation 前置 rejectIfLocalResource（20045，含 DELETE 负向测试）、管理入口清单换值 {USER, ORG, MENU}；前端权限串全量切换（user/grant/permission-query 页 + mock）；文档全量同步（api-contract、admin 契约、runbook fixture、org-user 契约 v1.5 等）；schema 双测试新增退役码值登记（16/17/18/19/22/28 不复用）。T-ACCESS-019 依赖解除。
- 2026-08-23 执行：T-PERM-042 done——引擎四显式 API（hasPermissionByCode/getDeniedResourceCodes 对外、hasPermissionByEntityId/getDeniedEntityIds entityId 轨）落地，hasPermission/validateBatch/getDeniedIds/toLongId 删除清零；9 处 getDeniedIds 全量改造（USER/ROLE 6 处错传点改业务编码语义）；授权页 3 读接口补类型级 VIEW 门禁；新增 InstanceGateBusinessCodePgIT（自装配投影 fixtures）。全量双层验证 BUILD SUCCESS（单测 670 + 容器 63），T-ORG-001 依赖解除。
- 2026-08-23 执行：T-ACCESS-017 done（`37de53f61` → `80e87fb98`）——五链路特征测试单测+PG 两层全绿；最小 CI 两 job（单测 push 强制 / 容器 PR+手动门控）在 GitHub Actions 真实跑通。过程中发现并修复两处生产缺陷：`OperationPermissionMapper.selectByResourceTypeAndCodes` XML 参数名与接口不一致（真实查询路径 BindingException）；MyBatis-Flex 全局方言被 H2 上下文污染致 PG 容器测试反引号 SQL（surefire 双 execution 分层隔离）。T-PERM-042 依赖解除，模型收敛 Epic 可启动。
- 2026-08-23 执行：T-ACCESS-019 done——LocalProjectionDomainService 新增 ROLE/USER 资源投影四方法（upsertRoleResource 父镜像 fail-closed、softDelete 批量 owner 过滤、upsertUserResource code=subjectId）；RoleManage 四写路径（create/update/move/remove）与 UserManage 三写路径（create/update/remove）同事务投影 + @PermissionChange/mark 补齐（createUser/updateUser 原缺失、deleteRoles 补 markRoles）+ permission_change_log 登记；abstract-user/update 门禁升实例级（4 项设计决策经用户拍板：ROLE 父镜像 fail-closed / sync 三入口登记遗留 / 门禁升实例级 / 补变更日志）；新增 UserRoleWriteProjectionPgIT（真实 PG+Redis 4 用例：写路径产投影 → 实例门禁按业务编码命中/拒绝 → 投影故障注入整体回滚）；附带修复存量 bug SubjectDomainServiceImpl.selectValidRoleById 实参反转（IT 暴露，updateRole/moveRole/deleteRoles 对 roleId≠租户号恒报不存在）。全量 670 tests 0 failures。外部 sync 三入口（AbstractUser/AbstractRole/UserRoleSync）无投影/无缓存失效登记遗留待立项。T-ACCESS-020 依赖解除。
