# 任务看板（Task Board）

本目录是 AccessMesh 任务的**唯一权威清单**。任务为原子执行单元，归属某个 [计划](../plans/)，并声明将改动的 [设计](../design/) 章节。

> 治理规则见 skill：`.claude/skills/design-plan-task-lifecycle/SKILL.md`。任务 ID 格式 `T-<DOMAIN>-<NNN>`，各领域独立递增、ID 冻结不回收。

> **归档约定（2026-09-12 定案）**：终态任务卡（done/cancelled 且无活跃计划归属）与随计划归档的卡物理存放于 `docs/archive/<日期>/tasks/`，本表行保留终态图标、链接直指归档位置；`docs/tasks/` 只留未终态卡与活跃计划附属卡。

## 领域计数器

| 领域 | 前缀 | 下一编号 |
|---|---|---|
| access-service 归并（跨服务） | `T-ACCESS` | 056 |
| permission-center | `T-PERM` | 080 |
| admin-service | `T-ADMIN` | 030 |
| gateway | `T-GW` | 011 |
| 组织/用户（跨 admin+perm） | `T-ORG` | 004 |
| 跨服务 API 契约 | `T-API` | 005 |
| 前端 | `T-FE` | 060 |

> 新建任务时从对应领域取下一编号，计数器 +1。

## 任务总表

> 状态简写：⚙️=proposed / 🔨=in-progress / 👀=review / ✅=done / ❌=cancelled。回写：⏳=pending / ✓=done。

> **后端门禁已解除（2026-08-22，T-ACCESS-012 完成）**：`T-PERM-*` / `T-ADMIN-*` 后端任务已全部重基线到 access-service 单模块与 `schema/access-service.sql`，可按各自 `depends_on` 推进；前端真接口联调等待对应 Phase 2 后端任务完成。归并主计划已归档（[archive/2026-08-22/access-service-merge-plan.md](../archive/2026-08-22/access-service-merge-plan.md)），后续强化计划亦已归档（[access-post-merge-plan](../archive/2026-08-27/access-post-merge-plan.md)，T-ACCESS-013~015 全 done，CI 准入前置由 T-ACCESS-017 最小 CI 关闭；68 项为 2026-08-22 外部主机历史验证基线，CI 以退出状态判定成功）。

### IAM 核心正确性与用户任务闭环

[计划](../plans/iam-task-closure-plan.md) · [待实施方案](../design/iam-task-closure.md) · [证据与去重映射](../archive/2026-09-20/comprehensive-review.md)

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-ORG-002](T-ORG-002.md) | 默认身份目录删除与恢复边界闭合 | ⚙️ | — |
| [T-ADMIN-028](T-ADMIN-028.md) | OAuth2 授权码客户端关联校验 | ⚙️ | — |
| [T-PERM-074](T-PERM-074.md) | 同步失败与版本记账事务一致性 | ✅ | — |
| [T-PERM-075](T-PERM-075.md) | 互斥角色有效期与判定入口一致性 | ⚙️ | — |
| [T-ORG-003](T-ORG-003.md) | 组织与岗位成员候选门禁统一 | ⚙️ | — |
| [T-PERM-076](T-PERM-076.md) | 资源批量创建复合身份一致性 | ⚙️ | — |
| [T-FE-057](T-FE-057.md) | 岗位停用后可发现与恢复 | ⚙️ | — |
| [T-FE-058](T-FE-058.md) | 岗位与成员角色候选分页闭合 | ⚙️ | — |
| [T-API-004](T-API-004.md) | 可编辑字段显式清空协议贯通 | ⚙️ | — |
| [T-ADMIN-029](T-ADMIN-029.md) | 公告状态与受众生命周期闭合 | ⚙️ | — |
| [T-FE-059](T-FE-059.md) | 共享列表上下文与可写对象绑定 | ⚙️ | — |
| [T-GW-010](T-GW-010.md) | 开发与代理拓扑的 Origin 接入一致性 | ⚙️ | — |
| [T-PERM-077](T-PERM-077.md) | 操作继承掩码缺省值契约对齐 | ⚙️ | — |
| [T-ACCESS-052](T-ACCESS-052.md) | 实例委派的目录菜单与管理任务闭环 | ⚙️ | T-ORG-003 |
| [T-ACCESS-053](T-ACCESS-053.md) | 首次服务接入与撤销验证路径简化 | ⚙️ | T-GW-010 |
| [T-PERM-078](T-PERM-078.md) | 自动授权实施前协议与算法校准 | ✅ | — |
| [T-ACCESS-054](T-ACCESS-054.md) | 外围任务能力与缓存过渡机制取舍 | ⚙️ | — |
| [T-ACCESS-055](T-ACCESS-055.md) | 核心用户任务组合验收与文档收口 | ⚙️ | T-ORG-002, T-ADMIN-028, T-PERM-074, T-PERM-075, T-PERM-076, T-FE-057, T-FE-058, T-API-004, T-ADMIN-029, T-FE-059, T-PERM-077, T-ACCESS-052, T-ACCESS-053 |

### access-service 归并（主链 ✅ 2026-08-22 完成归档；后续强化 ✅ 2026-08-27 归档）

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| [T-ACCESS-001](../archive/2026-08-22/tasks/T-ACCESS-001.md) | 建立 access-service 工程骨架并物理归并源码 | access-service-merge（已归档） | design/access-service-architecture.md §2/§3/§9；project-rules | — | ✅ | ✓ |
| [T-ACCESS-002](../archive/2026-08-22/tasks/T-ACCESS-002.md) | 建立 access_db 最终 DDL 并收敛持久层模型 | access-service-merge | access-service-architecture §4/§5/§8.1；schema/access-service.sql（本任务产出） | T-ACCESS-001 | ✅ | ✓ |
| [T-ACCESS-003](../archive/2026-08-22/tasks/T-ACCESS-003.md) | 收敛单数据源、MyBatis、Redis、JSON等运行基础配置 | access-service-merge | access-service-architecture §2/§6/§7.1；project-rules；gateway | T-ACCESS-001, T-ACCESS-002 | ✅ | ✓ |
| [T-ACCESS-004](../archive/2026-08-22/tasks/T-ACCESS-004.md) | 实现可信请求上下文和统一安全策略矩阵 | access-service-merge | access-service-architecture §6；admin/permission API 契约；gateway | T-ACCESS-003 | ✅ | ✓ |
| [T-ACCESS-005](../archive/2026-08-22/tasks/T-ACCESS-005.md) | 实现强事务权限投影并删除内部同步子系统 | access-service-merge | access-service-architecture §3/§4；permission core-flows；用户生命周期；admin-service-api-contract §3/§4/§6/§7 | T-ACCESS-002, T-ACCESS-004 | ✅ | ✓ |
| [T-ACCESS-006](../archive/2026-08-22/tasks/T-ACCESS-006.md) | 建立跨域只读查询模型 | access-service-merge | access-service-architecture §3；org-user/permission 契约 | T-ACCESS-002, T-ACCESS-005 | ✅ | ✓ |
| [T-ACCESS-007](../archive/2026-08-22/tasks/T-ACCESS-007.md) | 合并系统配置与操作审计并落实日志事务分级 | access-service-merge | access-service-architecture §5.2/§8.2；project-rules | T-ACCESS-002, T-ACCESS-004 | ✅ | ✓ |
| [T-ACCESS-008](../archive/2026-08-22/tasks/T-ACCESS-008.md) | 统一缓存并实现多实例失效及30秒安全边界 | access-service-merge | access-service-architecture §7；project-rules §12；v3.5 §7.2；gateway | T-ACCESS-003, T-ACCESS-005 | ✅ | ✓ |
| [T-ACCESS-009](../archive/2026-08-22/tasks/T-ACCESS-009.md) | 建立数据库任务租约、幂等和异步执行治理 | access-service-merge | access-service-architecture §8；admin-service | T-ACCESS-002, T-ACCESS-004 | ✅ | ✓ |
| [T-ACCESS-010](../archive/2026-08-22/tasks/T-ACCESS-010.md) | 切换 Gateway、SDK、Nacos和部署配置 | access-service-merge | access-service-architecture §2/§9；architecture；gateway | T-ACCESS-004, T-ACCESS-005, T-ACCESS-008 | ✅ | ✓ |
| [T-ACCESS-011](../archive/2026-08-22/tasks/T-ACCESS-011.md) | 完成契约、回滚、架构、空库和双实例验收 | access-service-merge | access-service-architecture §10；项目与 API 契约；gateway | T-ACCESS-006, T-ACCESS-007, T-ACCESS-009, T-ACCESS-010 | ✅ | ✓ |
| [T-ACCESS-012](../archive/2026-08-22/tasks/T-ACCESS-012.md) | 删除残留引用、回写设计并重基线任务看板 | access-service-merge（已归档） | access-service-architecture；architecture；project-rules；admin-service-api-contract；permission-center 设计；schema/access-service.sql；文档索引 | T-ACCESS-011 | ✅ | ✓ |
| [T-ACCESS-013](../archive/2026-08-27/tasks/T-ACCESS-013.md) | OAuth2 资源服务器与 scope 授权模型（委托令牌访问业务 API 显式开放） | access-post-merge（已归档） | access-service-architecture §6；admin-service-api-contract | T-ACCESS-012 | ✅ | ✅ |
| [T-ACCESS-014](../archive/2026-08-27/tasks/T-ACCESS-014.md) | admin/application 域 AppService 操作日志强制覆盖 | access-post-merge（已归档） | access-service-architecture §8.2；project-rules | T-ACCESS-007 | ✅ | ✓ |
| [T-ACCESS-015](../archive/2026-08-27/tasks/T-ACCESS-015.md) | 菜单 CRUD 写链路对齐 v3.5 最终态与权威 DDL（sys_menu DDL-实体漂移收口） | access-post-merge（已归档） | access-service-architecture §3；v3.5-design §2.1/§4.1；schema/access-service.sql；admin-service-api-contract | T-ACCESS-012 | ✅ | ✓ |
| [T-ACCESS-016](../archive/2026-08-27/tasks/T-ACCESS-016.md) | 身份与资源模型设计定稿（B-lite 终态 + 类型收敛映射 + 引擎显式 API 契约） | product-vertical-slice（已归档） | access-service-architecture（新增章节）；schema/access-service.sql；api-contract；implementation；admin-service-api-contract | — | ✅ | ✓ |
| [T-ACCESS-017](../archive/2026-08-27/tasks/T-ACCESS-017.md) | 窄回归安全网与最小 CI | product-vertical-slice（已归档） | access-service-architecture；core-flows | — | ✅ | ✓ |
| [T-ACCESS-018](../archive/2026-08-27/tasks/T-ACCESS-018.md) | 资源类型收敛（五组合并 + 双常量合一 + 前端权限串） | product-vertical-slice（已归档） | schema/access-service.sql；access-service-architecture；api-contract；admin-service-api-contract；frontend/README；access-service-rebuild-runbook | T-ORG-001 | ✅ | ✓ |
| [T-ACCESS-019](../archive/2026-08-27/tasks/T-ACCESS-019.md) | USER/ROLE 全写路径同事务资源投影 | product-vertical-slice（已归档） | access-service-architecture；schema/access-service.sql；admin-service-api-contract | T-ACCESS-018 | ✅ | ✓ |
| [T-ACCESS-020](../archive/2026-08-27/tasks/T-ACCESS-020.md) | 空库 bootstrap（一键基础设施 + 幂等首管理员种子） | product-vertical-slice（已归档） | schema/access-service.sql；access-service-architecture；architecture | T-ACCESS-019 | ✅ | ✓ |
| [T-ACCESS-021](../archive/2026-08-27/tasks/T-ACCESS-021.md) | BASIC_ROLE 授权垂直切片 E2E 验收 + README 回写 | product-vertical-slice（已归档） | core-flows；gateway；access-service-architecture | T-ACCESS-020, T-FE-041 | ✅ | ✅ |
| [T-ACCESS-024](../archive/2026-08-27/tasks/T-ACCESS-024.md) | 时间语义 UTC 统一（TypeHandler/JDBC/JVM） | product-vertical-slice（已归档） | project-rules；access-service-architecture | T-ACCESS-021 | ✅ | ✓ |
| [T-ACCESS-025](../archive/2026-08-27/tasks/T-ACCESS-025.md) | 操作日志收敛（默认不序列化参数，裁剪覆盖要求） | product-vertical-slice（已归档） | access-service-architecture；project-rules；admin-service-api-contract | T-ACCESS-021 | ✅ | ✓ |
| [T-ACCESS-026](../archive/2026-08-27/tasks/T-ACCESS-026.md) | 验证证据登记与文档状态收口（含 post-merge 归档） | product-vertical-slice（已归档） | architecture；access-post-merge-plan；project-rules | T-API-001 + 里程碑 B 全部 | ✅ | ✓ |
| [T-ACCESS-027](../archive/2026-09-07/tasks/T-ACCESS-027.md) | 产品定位定稿回写与文档三档叙事整改（开源通用 IAM 定案） | product-positioning-landing（已归档） | README；docs/README；design/README；architecture；access-service-architecture；permission-center overview/implementation | — | ✅ | ✓ |
| [T-ACCESS-028](../archive/2026-09-07/tasks/T-ACCESS-028.md) | perm-data 空装配模块删除（SDK 面名实对齐） | product-positioning-landing（已归档） | architecture；README；example-service | — | ✅ | ✓ |
| [T-ACCESS-029](../archive/2026-09-14/tasks/T-ACCESS-029.md) | bootstrap 固定图授权收缩通道——软删墓碑三分判定 | [design-audit-followup](../archive/2026-09-14/design-audit-followup-plan.md)（已归档） | access-service-architecture §14.2 | — | ✅ | ✓ |
| [T-ACCESS-030](../archive/2026-09-12/tasks/T-ACCESS-030.md) | 容器测试轨道提速——单例容器 + 按类建库 + 复用 + fork 级并行 | —（2026-09-06 用户决策提速定案） | docs/archive/2026-09-12/tasks/T-ACCESS-030.md 设计口径（工程改进，无设计文档回写） | — | ✅ | ✓ |
| [T-ACCESS-031](../archive/2026-09-12/tasks/T-ACCESS-031.md) | E2E 独立模块分轨——gateway 解除 test 依赖 + skipE2E 日常口径 + -T 模块并行恢复 | —（2026-09-06 用户决策 B+结构性拆分） | docs/archive/2026-09-12/tasks/T-ACCESS-031.md 设计口径；gateway.md §测试域、access-service-architecture §14.8（E2E 落位回写） | — | ✅ | ✓ |
| [T-ACCESS-042](../archive/2026-09-16/tasks/T-ACCESS-042.md) | URL 单命名空间统一 /api/access/**——外部路径=服务路径（✅ 2026-09-15 收口：全量回归 1699 全绿含 E2E + dev 冒烟九项 + 双轨评审处置完毕；Q-001 收敛；2026-09-16 随 release-preview 批次单卡归档） | —（Q-001 单卡转出） | 契约总册 §2 URL 形态总述；capability-structure §6；services/gateway.md；engine/core-flows.md | — | ✅ | ✓ |

### access-service 能力包融合（2026-09-13 立项）

> [access-capability-fusion-plan](../archive/2026-09-14/access-capability-fusion-plan.md)（已归档 2026-09-14：十任务全 done，三通道外评处置后用户确认定稿）；目标设计 [capability-structure](../design/access-service-capability-structure.md)（**adopted**，2026-09-13 T-ACCESS-032 §8 定稿、十项裁决登记 decision-registry 同日行；033 迁移已于 2026-09-13 完成，代码即能力包结构）；定案见 decision-registry 2026-09-13 两行。

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| [T-ACCESS-032](../archive/2026-09-14/tasks/T-ACCESS-032.md) | 融合设计定稿——归属清单与边界断言 | [capability-fusion](../archive/2026-09-14/access-capability-fusion-plan.md) | capability-structure（产出 §8 并转 adopted）；access-service-architecture §1.2/§3/§9/§11；project-rules §8.2 | — | ✅ | ✓ |
| [T-ACCESS-033](../archive/2026-09-14/tasks/T-ACCESS-033.md) | 机械迁移——能力包搬包 + 命名收敛 + 断言面重建（✅ 2026-09-13 收口：500 类搬迁 + 17 对改名 + 僵尸三件删除 + 五测试重建 + @MapperScan 14 包；全量回归含 E2E 绿，终态与遗留登记见任务卡完成记录） | capability-fusion | capability-structure §2/§8；access-service-architecture §3 | T-ACCESS-032 | ✅ | ✓ |
| [T-ACCESS-034](../archive/2026-09-14/tasks/T-ACCESS-034.md) | 操作码合一与 USER 轨细粒度化（✅ 2026-09-13 收口：OperationCode 单一常量面 + USER 字段分档门禁 + USER:MANAGE 退役 + 空库首管理员放行） | capability-fusion | capability-structure §5.1；契约总册 §12.1/§4；schema | T-ACCESS-033, T-ACCESS-040 | ✅ | ✅ |
| [T-ACCESS-035](../archive/2026-09-14/tasks/T-ACCESS-035.md) | 双轨死字段消减（✅ 2026-09-13 收口：username 投影/容器行 sort_order+extra.orgType 签名级停写 + 死注入死 import 清除；flex 全列插入显式 NULL 实证） | capability-fusion | capability-structure §5.2；schema | T-ACCESS-033 | ✅ | ✓ |
| [T-ACCESS-036](../archive/2026-09-14/tasks/T-ACCESS-036.md) | resource_entity.sort_order 退役（✅ 2026-09-13 收口：列/实体/SDK 双册/sync 两 DTO/树响应/五面写入点/前端与 mock 同批退役 + 严格 mapper 400 负向锁四 DTO 面） | capability-fusion | capability-structure §5.2；契约总册 §12.1；schema；perm-common | T-ACCESS-033, T-ACCESS-040 | ✅ | ✓ |
| [T-ACCESS-037](../archive/2026-09-14/tasks/T-ACCESS-037.md) | system_config 单入口化（✅ 2026-09-13 收口：admin /config 五文件整链删除 + mapper 五方法/XML + 错误码 10701/10702 退役登记 + 快照负向锁；SYSTEM_CONFIG 门禁收敛 perm 口径零悬空） | capability-fusion | capability-structure §5.3；契约总册 §17.2/§17.3 | T-ACCESS-033, T-ACCESS-040 | ✅ | ✓ |
| [T-ACCESS-038](../archive/2026-09-14/tasks/T-ACCESS-038.md) | 错误码合类不合号（✅ 2026-09-13 收口：合一为单册 AccessErrorCode（118 项=112 平移+6 碰撞段前缀）+ 80 文件机械收敛；ErrorCodeContractTest 单键基线+碰撞对专项锁；双轨+claude/grok 外评全处置） | capability-fusion | capability-structure §3；access-service-architecture §9 | T-ACCESS-033 | ✅ | ✓ |
| [T-ACCESS-039](../archive/2026-09-14/tasks/T-ACCESS-039.md) | 缓存目录合一（✅ 2026-09-13 收口：AccessCacheCatalog 单册 9 条目 mode/TTL 零改动 + admin:org-visibility 越域归位 access:org-visibility 不做兼容双读 + 边界校验器六条重绑判据不变 + 三条合一回归锁；双轨+claude/grok 外评全处置（claude P3×1/grok P3×0，均可定稿）） | capability-fusion | capability-structure §3；dual-layer-cache-framework skill（双副本） | T-ACCESS-033, T-ACCESS-034 | ✅ | ✓ |
| [T-ACCESS-040](../archive/2026-09-14/tasks/T-ACCESS-040.md) | API 契约深合一与设计文档重组（✅ 2026-09-13 收口：总册 2856 行零丢失合并 + 目录解散迁 engine/ + 全仓重挂 64 代码文件 + 双轨评审处置完毕） | capability-fusion | capability-structure §7；契约总册（本任务产出） | T-ACCESS-033 | ✅ | ✓ |
| [T-ACCESS-041](../archive/2026-09-14/tasks/T-ACCESS-041.md) | 规则与技能文件重写（能力 + 引擎口径）（✅ 2026-09-14 收口：规则改名 permission-coding-standards.md v7.0.0 权限面口径 + skills 双副本同步 + AGENTS 指针表/架构图 + project-rules 互斥句承接 + adopted 设计约 80 处域叙事改管理面/权限面（词汇用户拍板）；残留二分判定闭合、旧文件名活引用为零；双轨评审全处置） | capability-fusion | capability-structure §7；project-rules | T-ACCESS-033, T-ACCESS-034~039, T-ACCESS-040 | ✅ | ✓ |

### access-service 跨能力 mapper 直读收敛（Q-009 转出，2026-09-15 立项）

> [capability-mapper-convergence-plan](../archive/2026-09-15/capability-mapper-convergence-plan.md)（已归档 2026-09-15，四任务全 done）：冻结白名单 19 类 30 边全量收敛至零并退役断言白名单；定案与新服务硬契约见 decision-registry 2026-09-15 行；计划内四任务串行（白名单记账串行化），边清单以计划文件「消费类:行号 → mapper#方法」定位为准。

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| [T-ACCESS-043](../archive/2026-09-15/tasks/T-ACCESS-043.md) | 收敛批次①——既有服务直换 + 死边清理（9 边）（✅ 2026-09-15 收口：模块双 fork 1249+210 全绿；白名单 30→21） | capability-mapper-convergence | capability-structure §8.4（豁免 6 行缩减） | — | ✅ | ✓ |
| [T-ACCESS-044](../archive/2026-09-15/tasks/T-ACCESS-044.md) | 收敛批次②——grant 授权事实服务 + rule 条件读（6 边）（✅ 2026-09-15 收口：RoleResourcePermissionDomainService 落地 + 条件三读；模块双 fork 全绿；白名单 21→15） | 同 | 同 | T-ACCESS-043 | ✅ | ✓ |
| [T-ACCESS-045](../archive/2026-09-15/tasks/T-ACCESS-045.md) | 收敛批次③——type/resource/domain 供给读服务（11 边）（✅ 2026-09-15 收口：五新服务落地 + 全租户/IN 双口径；模块双 fork 全绿；白名单 15→4） | 同 | 同 | T-ACCESS-044 | ✅ | ✓ |
| [T-ACCESS-046](../archive/2026-09-15/tasks/T-ACCESS-046.md) | 收敛批次④——user-role 原始行/投影 + 白名单退役收口（4 边）（✅ 2026-09-15 收口：30 边全收敛、白名单退役零容忍 + fixture 自证；全量 -T 1C 含 E2E 1698 全绿 + 双轨评审处置完毕；Q-009 收敛） | 同 | capability-structure §8.4；project-rules §8.2 | T-ACCESS-045 | ✅ | ✓ |

### 发布预览（release-preview-plan，2026-09-16 立项——✅ 2026-09-16 全部收口归档）

> [release-preview-plan](../archive/2026-09-16/release-preview-plan.md)（已归档 2026-09-16，四任务全 done）：首个发布预览（v0.1.0）与部署验证——全栈 compose profile + 发布文档全套 + 空环境部署验证 + 本地 tag v0.1.0；三项定调与双轨评审处置见 decision-registry 2026-09-16 两行；最终全量回归含 E2E 全绿（1706 项 0 失败）。

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| [T-ACCESS-047](../archive/2026-09-16/tasks/T-ACCESS-047.md) | 全栈部署编排——compose app profile + 服务/前端镜像 + 配置占位符 | release-preview（已归档） | gateway.md §配置项；access-service-architecture §2/§16 | — | ✅ | ✓ |
| [T-ACCESS-048](../archive/2026-09-16/tasks/T-ACCESS-048.md) | Q-006 双命名空间失效修复——ORG_VISIBILITY legacy 别名同批 evict + 回归锁 | 同 | dual-layer-cache-framework skill 双副本 | — | ✅ | ✓ |
| [T-ACCESS-049](../archive/2026-09-16/tasks/T-ACCESS-049.md) | 发布文档与版本化——quickstart/deployment 基线/CHANGELOG/README/LICENSE | 同 | extension-guide；design/README 三档口径；docs/README | T-ACCESS-047 | ✅ | ✓ |
| [T-ACCESS-050](../archive/2026-09-16/tasks/T-ACCESS-050.md) | 首次部署验证与版本打点收口——空环境全栈冒烟 + v0.1.0 tag + 042 归档 | 同 | release-preview-plan；decision-registry | T-ACCESS-047, T-ACCESS-048, T-ACCESS-049 | ✅ | ✓ |

### access-service 零星任务（Q 清单转出）

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| [T-ACCESS-051](../archive/2026-09-18/tasks/T-ACCESS-051.md) | Q-013 时序用例裸 sleep 清扫——TaskExecutionLeaseConcurrencyTest 两方法改 5s 有界轮询（✅ 2026-09-18 收口：定向 10/10 + 全量含 E2E 1724 项 0 失败；双轨评审零 P0-P2，同族三处登记 Q-014） | — | testing-standards rule §10.3（口径已载，无设计回写面） | — | ✅ | — |

### permission-center（工作单 A/B 计划与 D/E/F 计划均已归档；前端 Phase 2/4 后端任务计划 2026-09-14 归档——T-PERM-035 已取消〔2026-09-19 定稿拆卡 070~073 承接并随批次归档〕；自动授权前置 T-PERM-070 已完成，实施前细化为 T-PERM-078、实施为 T-PERM-071～073；T-PERM-036/054 维持暂缓）

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| [T-PERM-001](../archive/2026-06-28/tasks/T-PERM-001.md) | Gateway 缓存改快照模式（user → InterfaceSnapshot） | [perm-cache-invalidation](../archive/2026-06-28/perm-cache-invalidation-plan.md) | design/permission-center-v3.5-design.md §7.2；design/services/gateway.md | — | ✅ | ✓ |
| T-PERM-002 | PermissionChangeContext ThreadLocal + AppService AOP afterCommit | perm-cache-invalidation | design/permission-center-v3.5-design.md §7.2 | T-PERM-001 | ✅ | ✓ |
| T-PERM-003 | 删除 permission_version 表+实体+Service+Mapper+Controller+DTO（存量环境 DROP TABLE 为外部 DBA/运维动作，仓库无 migration 框架） | perm-cache-invalidation | design/permission-center-v3.5-design.md §9.2；design/permission-center/overview.md；implementation.md §5.1/5.2 | — | ✅ | ✓ |
| T-PERM-004 | 删除 4 处 permissionVersionDomainService.increment 调用 | perm-cache-invalidation | design/permission-center-v3.5-design.md §9.2 | T-PERM-003 | ✅ | ✓ |
| T-PERM-005 | 删除缓存目录 PermCacheCatalog.PERMISSION_VERSION + key 后缀 :{permissionVersion} | perm-cache-invalidation | design/permission-center-v3.5-design.md §9.2 | T-PERM-003 | ✅ | ✓ |
| [T-PERM-006](../archive/2026-06-28/tasks/T-PERM-006.md) | Gateway 订阅 perm:invalidate topic，按 tenant+serviceCodes/userIds evict 本地 INTERFACE_SNAPSHOT（roleIds-only 事件按租户级安全清理） | perm-cache-invalidation | design/permission-center-v3.5-design.md §7.2；design/services/gateway.md | T-PERM-018 | ✅ | ✓ |
| T-PERM-007 | 同步修订 overview/core-flows/implementation/api-contract/coding-standards §5（代码层一致性核对） | perm-cache-invalidation | design/permission-center/{overview,core-flows,implementation,api-contract}.md | T-PERM-003 | ✅ | ✓ |
| T-PERM-008 | Gateway 失效标记与订阅恢复策略（S-006 已设计，规范见 gateway.md §快照失效标记与订阅恢复） | perm-cache-invalidation | design/services/gateway.md §快照失效标记与订阅恢复 | T-GW-005（S-006 设计 ✅）| ✅ | ✓ |
| T-PERM-009 | scopeMode 4 态枚举(DENIED/INSTANCE/ALL/EMPTY) + QueryScopesResp 分类模型重构(按 resourceType×operation 分桶) | [scope-mode-migration](../archive/2026-06-28/scope-mode-migration-plan.md) | design/permission-center-v3.5-design.md §3 | T-PERM-003 | ✅ | ✓ |
| T-PERM-010 | api-contract.md §6.7 query-scopes 响应改造（scopeAll → scopeMode）— 范围已合并进 T-PERM-009 完成（§6.7 已回写 scopeMode 四态） | scope-mode-migration | design/permission-center/api-contract.md §6.7 | T-PERM-009 | ✅ | ✓ |
| T-PERM-011 | api-contract.md §6.4-6.10 / §10 第 8 条等约 30+ 处 scopeAll 全量推广到 scopeMode | scope-mode-migration | design/permission-center/api-contract.md | T-PERM-009 | ✅ | ✓ |
| T-PERM-012 | 管理端授权配置/排查页响应改造（role-resource-permission save/grant、permission-view） | scope-mode-migration | design/permission-center/api-contract.md | T-PERM-009 | ✅ | ✓ |
| T-PERM-013 | schema scope_all 字段保留（仅内部存储），协议层映射逻辑实现 | scope-mode-migration | design/schema/access-service.sql | T-PERM-009 | ✅ | ✓ |
| T-PERM-014 | 同步修订 api-contract.md 顶部 scopeMode 迁移注记（移除注记改为正式定义） | scope-mode-migration | design/permission-center/api-contract.md | T-PERM-010, T-PERM-011 | ✅ | ✓ |
| T-PERM-015 | 前端 ScopeMode 类型定义 + composable（hasPerms/Perms 不涉及 L2 数据权限，无需改造） | scope-mode-migration | design/permission-center-v3.5-design.md §3 | T-PERM-009 | ✅ | ✓ |
| [T-PERM-017](../archive/2026-06-28/tasks/T-PERM-017.md) | 条件权限 Gateway 侧重评（部分下发 gateway_evaluable + 未下发回退 check-interface） | perm-cache-invalidation | design/services/gateway.md；v3.5 §7.2 | T-PERM-002, T-PERM-018 | ✅ | ✓ |
| [T-PERM-018](../archive/2026-06-28/tasks/T-PERM-018.md) | 缓存下沉——移除 INTERFACE_SNAPSHOT(L2)/permissionVersion，激活 ROLE_PERM_SNAPSHOT engine 读缓存，扩展失效事件 serviceCodes | perm-cache-invalidation | v3.5 §5.1/§7.2；api-contract §6.x | T-PERM-002 | ✅ | ✓ |
| [T-PERM-019](../archive/2026-09-12/tasks/T-PERM-019.md) | 工作单 D：防呆机制（已收口 2026-09-07，终态见任务卡实现记录与 implementation §8） | [design-review-def-followup](../archive/2026-09-12/design-review-def-followup-plan.md)（已归档） | design-review §11；api-contract；core-flows；implementation §8；schema；admin sync | — | ✅ | ✓ |
| [T-PERM-020](../archive/2026-09-12/tasks/T-PERM-020.md) | 工作单 E：清理预设能力（死工厂删除 + domain_config/RocketMQ/auto-grant 口径收口） | [design-review-def-followup](../archive/2026-09-12/design-review-def-followup-plan.md)（已归档） | design-review §11；api-contract；core-flows；implementation；schema；architecture | — | ✅ | ✓ |
| [T-PERM-021](../archive/2026-09-12/tasks/T-PERM-021.md) | 工作单 F：文档准确性与代码简化（✅ 2026-09-12 收口：F1.a/b 定性（b 另立 T-PERM-065）、F1.c/e 文档面、F1.d requestId 单 ID 收敛+审计两列 NOT NULL、F1.f/g 代码卫生；双轨评审处置+全量回归含 E2E 全绿，终态见任务卡） | [design-review-def-followup](../archive/2026-09-12/design-review-def-followup-plan.md)（已归档） | design-review §11；api-contract；implementation；schema；admin sync；project-rules | — | ✅ | ✓ |
| T-PERM-022 | 2.2 角色管理后端（已收口 2026-08-28，终态见 design/frontend/role-manage.md §8） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | api-contract §5.2/§6.10.3；implementation §2.1；design/frontend/role-manage.md §8 | T-FE-002 | ✅ | ✓ |
| T-PERM-023 | 6.1 类型定义后端（已收口 2026-08-28，终态见 design/frontend/type-definition.md §8） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | api-contract §5.1；design/frontend/type-definition.md §8 | T-FE-003 | ✅ | ✓ |
| T-PERM-024 | 6.2 系统配置后端（已收口 2026-08-28，终态见 design/frontend/system-config.md §8） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | api-contract §5.8 | T-FE-004 | ✅ | ✓ |
| [T-PERM-025](../archive/2026-09-14/tasks/T-PERM-025.md) | 7.1 操作日志后端（已收口 2026-08-28，终态见 design/frontend/operation-log.md §8） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | api-contract §5.8/§6.10.6；implementation §2.3；design/frontend/operation-log.md §8 | T-FE-005 | ✅ | ✓ |
| [T-PERM-026](../archive/2026-09-14/tasks/T-PERM-026.md) | 5.1 业务域后端（已收口 2026-08-29，终态见 api-contract §5.1/§5.6 契约要点） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | api-contract §5.1/§5.6；implementation §2.7；design/frontend/biz-domain.md §8 | T-FE-006 | ✅ | ✓ |
| [T-PERM-027](../archive/2026-09-14/tasks/T-PERM-027.md) | 5.2 服务+接口映射后端（已收口 2026-08-29，终态见 api-contract §5.4 契约要点） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | api-contract §5.4/§6.3/§6.10.4；design/frontend/service-interface-mapping.md §7 | T-FE-007 | ✅ | ✓ |
| [T-PERM-028](../archive/2026-09-14/tasks/T-PERM-028.md) | 3.1 资源+操作定义后端（已收口，五项设计定案与终态见任务卡） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | api-contract §5.3/§6.2.2；implementation §2.9；design/frontend/resource-operation.md §8 | T-FE-008 | ✅ | ✓ |
| [T-PERM-029](../archive/2026-09-14/tasks/T-PERM-029.md) | 3.2 权限条件后端（已收口 2026-08-30，终态见任务卡） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | api-contract §5.6；implementation §2.5；design/frontend/permission-condition.md §8 | T-FE-009 | ✅ | ✓ |
| [T-PERM-030](../archive/2026-09-14/tasks/T-PERM-030.md) | 3.3 冲突规则后端（已收口 2026-08-30，终态见任务卡） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | api-contract §5.6；implementation §2.4；design/frontend/conflict-rule.md §4 | T-FE-010 | ✅ | ✓ |
| [T-PERM-031](../archive/2026-09-14/tasks/T-PERM-031.md) | 3.4 资源依赖后端（已收口 2026-08-30，终态见任务卡） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | api-contract §5.6/§6.9；core-flows §12 | T-FE-011 | ✅ | ✓ |
| T-PERM-032 | 7.2 变更日志后端（已收口 2026-08-29，终态见 design/frontend/permission-change-log.md §5） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | api-contract §5.8/§6.8；implementation §2.3 | T-FE-012 | ✅ | ✓ |
| [T-PERM-033](../archive/2026-09-14/tasks/T-PERM-033.md) | 4.2 权限查询后端（已收口 2026-08-29，终态见任务卡/设计回写） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | api-contract §6.6-§6.8；implementation；design/frontend/permission-query.md | T-FE-013 | ✅ | ✓ |
| [T-PERM-034](../archive/2026-09-14/tasks/T-PERM-034.md) | 4.1 权限授予后端（已收口 2026-08-30，终态见任务卡完成记录） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | api-contract §5.5/§6.4/§6.5/§6.5.1/**§6.5.2**；implementation §4/§7.7；core-flows §6；permission-grant.md §12；access-service.sql | T-PERM-031 | ✅ | ✓ |
| [T-PERM-035](../archive/2026-09-19/tasks/T-PERM-035.md) | 自动授权（旧口径）— ❌ cancelled 2026-09-19：被定稿设计取代，拆分承接见 T-PERM-070~073；§11 E4 暂缓随定稿解除 | — | docs/design/dependency-auto-grant.md（取代依据） | — | ❌ | — |
| [T-PERM-070](T-PERM-070.md) | 前置·公共服务认证模块（per-service 静态凭证）（✅ 2026-09-20 收口：service_credential 表+管理面四端点+ServiceAuthArbiter 五形态仲裁〔凭证优先/禁止降级/白名单单源〕+Gateway M2M 放行链+SDK 凭证拦截器〔注入面精确镜像+启动声明式 TLS 三态〕+20065~20068+固定图四行；七项拍板见 registry 同日行；双轨评审 P1×2+P0×5 全处置〔SDK 宽注入面收窄+五处既有锁随批更新〕+E2E 真实 sync 成功用例；全量回归含 E2E 绿） | —（T-PERM-035 实现序列前置卡） | docs/design/service-authentication.md；docs/design/access-service-api-contract.md §24 | — | ✅ | ✓ |
| [T-PERM-071](T-PERM-071.md) | 独立依赖声明与可选 SDK 协调（✅ 2026-09-21 收口：全量含 E2E 1842 项 0 失败；双轨+四任务内外评审处置完毕〔claude P3×3 全处置、风格转出 T-PERM-079、拍板与豁免登记见 registry 同日三行〕；迁移未在任何部署库执行） | —（自动授权实施序列） | [简化设计](../design/dependency-auto-grant.md) | T-PERM-070, T-PERM-078, T-PERM-074 | ✅ | ✓ |
| [T-PERM-072](T-PERM-072.md) | 自动授权物化与共享推导 | —（自动授权实施序列） | [简化设计](../design/dependency-auto-grant.md) | T-PERM-071 | ✅ | ✓ |
| [T-PERM-073](T-PERM-073.md) | 按需来源解释、授权界面与对账 | —（自动授权实施序列） | [简化设计](../design/dependency-auto-grant.md) | T-PERM-072 | ⚙️ | ⏳ |
| [T-PERM-036](T-PERM-036.md) | 动态数据权限端到端验证（scopeMode → SQL 映射链路）— ⚠️ design-review §11 Q7/B 暂缓（延后 example-service） | —（2026-09-14 脱出已归档计划，暂缓等 PM 重申） | 契约总册 §18.6；engine/core-flows；engine/implementation | T-FE-013, T-PERM-033 | ⚙️ | ⏳ |
| [T-PERM-037](../archive/2026-09-14/tasks/T-PERM-037.md) | 跨页共性接口改造 + api-contract 回写收尾（已收口 2026-08-31，审计型零代码变更，终态见任务卡完成记录） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | api-contract；implementation | T-PERM-022~034 | ✅ | ✓ |
| [T-PERM-038](../archive/2026-09-12/tasks/T-PERM-038.md) | 全局 TODO 收口（已收口 2026-09-12：附录 A 七条定性——3 条消解/2 条 T-PERM-035 预留保留/2 条失效注释删除；终态见任务卡处置记录） | [frontend-phase4](../archive/2026-09-12/frontend-phase4-plan.md) | —（工程改进，无设计回写） | — | ✅ | — |
| [T-PERM-039](../archive/2026-09-12/tasks/T-PERM-039.md) | 测试补充（已收口 2026-09-12：覆盖重估——access-service 157 测试文件/permission 域 33/36 实现类有直接测试，原「每接口 1+2」口径已被各任务回归锁实质取代，定性依据见任务卡） | [frontend-phase4](../archive/2026-09-12/frontend-phase4-plan.md) | —（工程改进，无设计回写） | T-PERM-037 | ✅ | — |
| [T-PERM-040](../archive/2026-09-14/tasks/T-PERM-040.md) | 4.1 权限授予单资源类型后端支持（已收口 2026-08-31，终态见任务卡完成记录） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | api-contract §5.3/§6.4/§6.5.1；core-flows §6；permission-grant.md §3.2/§12 | T-PERM-028, T-PERM-034 | ✅ | ✓ |
| [T-PERM-041](../archive/2026-09-14/tasks/T-PERM-041.md) | 主权限条件不变量（20041 不可转授 + 20042 启用状态） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | api-contract §6.5.1；core-flows §6；permission-grant.md §4/§12；access-service.sql | T-PERM-034 | ✅ | ✓ |
| [T-PERM-042](../archive/2026-08-27/tasks/T-PERM-042.md) | 权限引擎显式资源 API 与实例门禁修复 | product-vertical-slice（已归档） | api-contract；implementation；access-service-architecture | T-ACCESS-016, T-ACCESS-017 | ✅ | ✓ |
| [T-PERM-043](../archive/2026-08-27/tasks/T-PERM-043.md) | GROUP_ROLE 写入口删除与前端隐藏 | product-vertical-slice（已归档） | api-contract；implementation；frontend/role-manage；frontend/permission-grant | T-ACCESS-019, T-ACCESS-021 | ✅ | ✓ |
| [T-PERM-044](../archive/2026-09-12/tasks/T-PERM-044.md) | 四棵树（角色/组织/菜单/资源实体）move 并发成环窗口与递归 CTE 遇环不收敛统一加固（已收口 2026-09-04，终态见 architecture §17） | — | [T-PERM-044](../archive/2026-09-12/tasks/T-PERM-044.md) | — | ✅ | ✓ |
| [T-PERM-045](../archive/2026-09-12/tasks/T-PERM-045.md) | 内部管理门禁统一启用子级继承——❌ 已取消（2026-09-09 范围并入 T-PERM-057 统一引擎重构，终态设计 query-engine-unification.md） | — | query-engine-unification.md | — | ❌ | — |
| [T-PERM-046](../archive/2026-09-12/tasks/T-PERM-046.md) | 业务域后端三项加固（已收口 2026-09-09，执行定案与终态见任务卡） | — | schema；api-contract §5.1/§5.6；design/frontend/biz-domain.md §9 | — | ✅ | ✓ |
| [T-PERM-047](../archive/2026-09-12/tasks/T-PERM-047.md) | 操作定义缓存失效接线（OPERATION_PERMISSIONS_BY_TYPE 写路径 evict；T-PERM-028 收口登记） | — | implementation §5；dual-layer-cache-framework | — | ✅ | ✓ |
| [T-PERM-048](../archive/2026-09-12/tasks/T-PERM-048.md) | 权限条件实例投影与双轨制——管理页条件 vs 授权页内联条件（已收口 2026-09-11，五项定案与终态见任务卡完成记录与 registry 同日行） | — | api-contract §5.6；access-service.sql | — | ✅ | ✓ |
| [T-PERM-049](../archive/2026-09-12/tasks/T-PERM-049.md) | 全局操作概念整体退役（操作位空间按类型隔离 + DDL CHECK 焊死；外部复审 P1 越权根治） | — | api-contract §5.3；access-service.sql | — | ✅ | ✓ |
| [T-PERM-050](../archive/2026-09-12/tasks/T-PERM-050.md) | resource_type 删除级联清理与引用保护——预置操作定义孤儿根治（已收口 2026-09-09，级联定案与终态见任务卡） | — | schema；api-contract §5.1/§5.3 | — | ✅ | ✓ |
| [T-PERM-051](../archive/2026-09-12/tasks/T-PERM-051.md) | TYPE_DEFINITION 实例投影与业务键统一（已收口，定案与终态见任务卡） | — | api-contract §5.1；access-service.sql；access-service-architecture §12.3 | — | ✅ | ✓ |
| [T-PERM-052](../archive/2026-09-14/tasks/T-PERM-052.md) | 资源类型级所有权边界——类型声明门禁（已收口，定案与终态见任务卡） | [design-audit-followup](../archive/2026-09-14/design-audit-followup-plan.md)（已归档） | api-contract §5.1/§5.3/§6.2.2/§6.3.1；architecture §4.3；schema | — | ✅ | ✅ |
| [T-PERM-053](../archive/2026-09-14/tasks/T-PERM-053.md) | service-config 同步 ApiItem.operationCode 无效字段删除 | [design-audit-followup](../archive/2026-09-14/design-audit-followup-plan.md)（已归档） | api-contract §6.3；schema | — | ✅ | ✓ |
| [T-PERM-054](T-PERM-054.md) | 手工 API 映射绑定非 API 资源处置（暂缓——关联权限自动授权方向已定、方案未定，见任务卡） | —（2026-09-14 脱出已归档计划，暂缓等方案定案） | 契约总册 §12.2；schema | — | ⚙️ | ⏳ |
| [T-PERM-055](../archive/2026-09-12/tasks/T-PERM-055.md) | 域分类查询批量预载优化（matchesTypeCode 循环点查放大收敛；已收口 2026-09-11，终态见任务卡完成记录） | — | access-service-architecture §13；api-contract §3.4 | — | ✅ | ✓ |
| [T-PERM-056](../archive/2026-09-12/tasks/T-PERM-056.md) | user_type/role_type 删除零检查——主体/角色类型引用面保护（已收口 2026-09-09，删除保护定案与终态见任务卡） | — | schema；api-contract §5.1 | — | ✅ | ✓ |
| [T-PERM-057](../archive/2026-09-12/tasks/T-PERM-057.md) | 权限查询统一引擎重构——收编六套形态 + 目标模式三态 + 判定面继承 + 评估拉平（已收口 2026-09-09，三条实施定案与终态见任务卡实现记录；query-engine-unification 已并入 implementation §3） | [permission-query-unification](../archive/2026-09-12/permission-query-unification-plan.md) | implementation §3/§5.2；core-flows §7；overview 鉴权与查询入口节；api-contract §6.1 inheritMode；permission-query-pipeline skill 双副本；permission-center-coding-standards rule；runbook §3 | — | ✅ | ✓ |
| [T-PERM-058](../archive/2026-09-12/tasks/T-PERM-058.md) | depend_on 子权限单点门禁闭合——单点主资源上下文 + fail-closed + 四面排除（已收口 2026-09-10，四项定案与终态见任务卡实现记录） | [permission-query-unification](../archive/2026-09-12/permission-query-unification-plan.md) | implementation §3.1/§3.3；api-contract §6.1/§6.6 | T-PERM-057 | ✅ | ✓ |
| [T-PERM-059](../archive/2026-09-12/tasks/T-PERM-059.md) | 权限视图/排查删除重设计——删除收口（全删 8 端点+前端排查页；新设计方向另立任务，2026-09-10 三项定案） | [permission-query-unification](../archive/2026-09-12/permission-query-unification-plan.md) | api-contract §5.8；access-service-architecture §14；query-engine-unification.md §9 | — | ✅ | ✓ |
| [T-PERM-060](../archive/2026-09-12/tasks/T-PERM-060.md) | 工程健康小批次——权限视图域映射批量化 + T-PERM-055 误读观察撤回 + EXT-7/8 处置登记（已收口 2026-09-11，终态见任务卡实施记录） | — | implementation §2.7 | — | ✅ | — |
| [T-PERM-061](../archive/2026-09-12/tasks/T-PERM-061.md) | EXT-7：batchCheck 逐条 engine.query 收敛——A+ 形态「分组 + 请求级共享装载」（已收口 2026-09-11：queryBatch + 条件四态增量快照 + 互斥 ledger 聚合 + 回归锁①-⑪ 全绿；双轨评审处置与父段装载定案见 registry 实施收口行、遗留登记见任务卡遗留节；实施落点见 implementation §3.10） | — | implementation §3；api-contract §6.1 | — | ✅ | ✓ |
| [T-PERM-062](../archive/2026-09-12/tasks/T-PERM-062.md) | 新类型首笔授权生命周期种子——createType/createOperation 同事务种 AUTHORITY_ROOT 首授行（✅ 2026-09-12 收口：实施期四项补充定案（reason 仅自定义类型/无历史数据/owner 可变更+同事务迁移/不加角色删除守卫）+ 双轨评审处置，全量回归含 E2E 全绿） | — | api-contract §5.1/§6.5.1；schema；architecture §14.2；extension-guide §3.5 | — | ✅ | ✅ |
| [T-PERM-063](../archive/2026-09-12/tasks/T-PERM-063.md) | 角色互斥授权时校验——写路径拦截 + 存量立规守卫 + 双删日志与 detect 扩展（✅ 2026-09-12 收口：双轨评审 7+7 项全处置——含组角色间接持有盲区 P1；全量回归含 E2E 全绿，终态见任务卡完成记录） | — | api-contract §5.5/§5.6；core-flows §7 评估口径；implementation §2/§3 | — | ✅ | ✓ |
| [T-PERM-064](../archive/2026-09-12/tasks/T-PERM-064.md) | 角色互斥守卫通道补全——sync/full-sync BIND 逐条守卫 + 互斥规则拒 ORG/POSITION 对（✅ 2026-09-12 收口：claude 外评 P2-1 处置落地，全量回归含 E2E 全绿，终态见任务卡完成记录） | — | api-contract §5.6/§6.2.2.3；implementation §2.4；AGENTS.md | — | ✅ | ✓ |
| [T-PERM-065](../archive/2026-09-14/tasks/T-PERM-065.md) | Req DTO 副本双轨收敛——14 对同名 Req 与 perm-common 统一（T-PERM-021 F1.b 盘点定性后另立；✅ 2026-09-12 收口：换绑单源 + SDK 契约对齐 + ResourceUpdateReq 业务键形态修复 + 守卫重写注解签名快照；双轨评审全处置，两项拍板见 registry 同日行） | —（2026-09-12 用户拍板另立任务） | api-contract；project-rules | — | ✅ | ✓ |
| [T-PERM-066](../archive/2026-09-14/tasks/T-PERM-066.md) | operationCodeKey 族大小写口径统一——raw 严格化（Q-003 转出；✅ 2026-09-14 收口：入站 DTO @Pattern 大写 400/90001 + 定义侧锁死 + 授权域归一退役两域统一 raw，双轨评审全处置，全量回归含 E2E 全绿，终态见任务卡完成记录） | —（Q-003 单卡转出） | 契约总册 §2.4/§2.5；engine/implementation §8.2 | — | ✅ | ✓ |
| [T-PERM-067](../archive/2026-09-14/tasks/T-PERM-067.md) | USER 写入口自身豁免收窄——档案字段豁免保留、启停/删除不豁免、reset-password 定位自助改密通道（Q-002 转出；✅ 2026-09-14 收口：admin 轨 /user/update 自禁对齐硬禁 + perm 轨死分支语义统一 + characterization 改锁新语义，双轨评审全处置，全量回归含 E2E 全绿，终态见任务卡完成记录） | —（Q-002 单卡转出） | 契约总册 §4 表/§7.4/§7.7/§7.8/§21.2/§22.2 决策 13 | — | ✅ | ✓ |
| [T-PERM-068](../archive/2026-09-17/tasks/T-PERM-068.md) | 跨类型父子边收紧——sync/管理面同类型父边门禁 + 父字段缺省同类型回填（Q-007 转出；✅ 2026-09-17 收口：三定案落地 + 10 回归锁旧实现实证红 + 双轨评审处置 + 全量含 E2E 1716 项 0 失败；时序抖动遗留登记 Q-013；2026-09-17 单卡归档） | —（Q-007 单卡转出） | 契约总册 §19.1/§19.2/§12.1；engine/implementation §3；extension-guide §3.4 | — | ✅ | ✓ |
| [T-PERM-069](../archive/2026-09-18/tasks/T-PERM-069.md) | API 类型内部来源收紧——种子声明 SYNC+access-service，管理面资源 CRUD 20055（Q-008 转出；✅ 2026-09-18 收口：「仅 API 收紧」定案落地 + 回归锁旧种子实证红 + 双轨评审全处置 + 全量含 E2E 1721 项 0 失败；SERVICE 维持 MANAGED 零 writer 死局；2026-09-18 单卡归档） | —（Q-008 单卡转出） | schema §17 种子声明；契约总册 §5/§19.8/§12/§13/§19.1；access-service-architecture T-PERM-052 段；architecture 服务间交互矩阵；example-service；extension-guide | — | ✅ | ✓ |
| [T-PERM-079](T-PERM-079.md) | 自动授权批次风格与可读性清理（070/074/071 触达面）— 2026-09-21 四任务内外评审风格专项转出 | —（评审转出单卡） | —（工程改进，无设计回写） | — | ⚙️ | — |

### gateway（工作单 C 失联兜底）

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| T-GW-001 | gateway.permission.fail-mode 配置项（closed/open/stale-allow，默认 closed）+ stale-grace-seconds | [gateway-fail-mode](../archive/2026-06-28/gateway-fail-mode-plan.md) | design/permission-center-v3.5-design.md §7.2；design/services/gateway.md | — | ✅ | ✓ |
| T-GW-002 | fail-closed 实现：perm-center 不可达 → 403/503 拒绝 | gateway-fail-mode | design/services/gateway.md | T-GW-001 | ✅ | ✓ |
| [T-GW-003](../archive/2026-06-28/tasks/T-GW-003.md) | stale-allow 实现：用过期未驱逐快照续命，超 stale-grace-seconds 转 closed | gateway-fail-mode | design/services/gateway.md | T-PERM-001（快照模式）, T-GW-001 | ✅ | ✓ |
| T-GW-004 | 监控指标：unreachable.count / fallback.{closed,open,stale}.count + WARN + Prometheus 告警 | gateway-fail-mode | design/services/gateway.md | T-GW-002 | ✅ | ✓ |
| T-GW-005 | 失效标记与订阅恢复策略设计（S-006 规范产出，已完成） | gateway-fail-mode | design/services/gateway.md §快照失效标记与订阅恢复 | T-PERM-006（广播事件载荷）| ✅ | ✓ |
| [T-GW-006](../archive/2026-06-28/tasks/T-GW-006.md) | 集成测试基线："杀 permission-center → Gateway 应 503"（重新界定：不在项目内做集成测试，改为独立仓库测试服务） | gateway-fail-mode | — | T-GW-002 | ✅ | ✓ |
| [T-GW-007](../archive/2026-08-27/tasks/T-GW-007.md) | Gateway CORS 环境化与 actuator 暴露收口（origin 明确列表、credentials 禁 `*`、独立 management 端口） | product-vertical-slice（已归档） | design/services/gateway.md | T-ACCESS-021 | ✅ | ✓ |
| [T-GW-008](../archive/2026-09-12/tasks/T-GW-008.md) | Gateway XFF 清洗与客户端 IP 重建——IP 条件信任面收口（已收口 2026-09-10，实施定案与存量缺陷修正见任务卡实现记录与 registry） | — | security-standards；gateway.md；rebuild-runbook | — | ✅ | ✓ |
| T-GW-009 | Gateway 白名单纳入 /user/reset-password（会话入口族；T-FE-046 前置）（✅ 2026-09-19 收口：白名单 + SecurityWebMvcConfig 密钥豁免同源同步（双轨评审 P0——漏配致内部凭证分支 400 遮蔽）+ 配置锁/链路锁均旧实现实证红 + gateway.md/契约 §7.7 清单同步；claude 外评 P3×2 全采纳（javadoc 去枚举化+固定图 API 行「保行+标注失效」拍板）+锁补强×2，见 registry 同日处置行；遗留 Nacos 远端覆盖键 2026-09-20 归档批次复核完毕（Nacos 查 gateway.yml → config data not exist，远端无覆盖、白名单即本地清单，遗留收敛）；存量清扫面登记 Q-015） | [frontend-session-consistency](../archive/2026-09-20/frontend-session-consistency-plan.md) | gateway.md；api-contract §7.7 | — | ✅ | ✓ |

> 注：T-PERM-008（代码侧 Gateway 失效标记）依赖 T-GW-005（设计侧 S-006 规范）产出，二者构成"设计先行 → 代码落地"链。

### admin-service

_当前无未终态 T-ADMIN 任务。`T-ADMIN-001~019`（用户角色代理修复）已全部完成并归档，见下方"已完成"区；其余 T-ADMIN 任务卡均已随所属计划归档。_

> EXT-7（PermissionCheckAppServiceImpl.batchCheck 逐条 engine.query）已立项 [T-PERM-061](../archive/2026-09-12/tasks/T-PERM-061.md)（2026-09-11 核实仍在；设计定稿 v4 同日用户确认，同日实施完成——queryBatch A+ 形态落地，见 implementation §3.10）；EXT-8（SyncTaskDomainServiceImpl.enqueueAll 逐条 insert）已失效——宿主随内部同步子系统删除（T-ACCESS-005 同事务投影替代），2026-09-11 全仓实核零命中。

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| [T-ADMIN-020](../archive/2026-09-12/tasks/T-ADMIN-020.md) | access-service admin 域 CRUD 代码清理（已收口 2026-09-12：规范清单重排查零可清扫描项，零代码变更定性收口；痛点 #6 口径漂移见任务卡） | [frontend-phase4](../archive/2026-09-12/frontend-phase4-plan.md) | —（工程改进，无设计回写） | — | ✅ | — |
| [T-ADMIN-021](../archive/2026-09-14/tasks/T-ADMIN-021.md) | org-tree 扩展 includePositions（组织+岗位一体树，授权页主体树数据源；已收口 2026-09-03，终态见任务卡完成记录——T-FE-037 依赖解锁） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | design/frontend/permission-grant.md §9；admin-service-api-contract §4.2.1 | — | ✅ | ✓ |
| [T-ADMIN-022](../archive/2026-08-27/tasks/T-ADMIN-022.md) | 登录锁定临时化与账号状态语义统一 | product-vertical-slice（已归档） | admin-service-api-contract；schema/access-service.sql；default-org-tree-user-lifecycle | T-ORG-001, T-ACCESS-021 | ✅ | ✓ |
| [T-ADMIN-023](../archive/2026-08-27/tasks/T-ADMIN-023.md) | 文件服务安全加固（VIEW 门禁 + 路径安全 + 删除顺序） | product-vertical-slice（已归档） | admin-service-api-contract；access-service-architecture | T-ACCESS-021 | ✅ | ✓ |
| [T-ADMIN-024](../archive/2026-08-27/tasks/T-ADMIN-024.md) | 恒拒绝退役 API 直接删除（含 /role/revoke-menu 共 5 个） | product-vertical-slice（已归档） | admin-service-api-contract；org-user-permission-contract；access-service-architecture；architecture；default-org-tree-user-lifecycle | T-ACCESS-021 | ✅ | ✓ |
| [T-ADMIN-025](../archive/2026-08-27/tasks/T-ADMIN-025.md) | 文件夹级授权（bizType 即文件夹实例，全链路 CREATE/VIEW/DELETE） | product-vertical-slice（已归档） | admin-service-api-contract；access-service-architecture；schema/access-service.sql | T-ADMIN-023 | ✅ | ✅ |
| [T-ADMIN-026](../archive/2026-09-12/tasks/T-ADMIN-026.md) | XML 映射 mapper 的 Page 参数不生效族统一改造（已收口 2026-09-06，终态见任务卡完成记录） | — | admin-service-api-contract；rebuild-runbook | T-FE-022 | ✅ | ✓ |
| [T-ADMIN-027](../archive/2026-09-12/tasks/T-ADMIN-027.md) | 响应分页信封统一——admin 嵌套方言退役 + PaginatedResp 更名 PageResp + 裸数组收编 ItemsResp（已收口，终态见任务卡） | — | admin-service-api-contract；api-contract §3.3；project-rules §1.3 | — | ✅ | ✓ |

### 组织/用户与跨服务 API

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| [T-ORG-001](../archive/2026-08-27/tasks/T-ORG-001.md) | 统一本地主体 ID（B-lite：共享主体 ID，删除 OperatorSubjectResolver） | product-vertical-slice（已归档） | access-service-architecture；schema/access-service.sql；implementation；default-org-tree-user-lifecycle；access-service-rebuild-runbook | T-PERM-042 | ✅ | ✓ |
| [T-API-001](../archive/2026-08-27/tasks/T-API-001.md) | example 单受保护接口接入（Gateway 主线）与 Starter 名实对齐 | product-vertical-slice（已归档） | example-service；gateway；architecture | T-ACCESS-021 | ✅ | ✓ |
| [T-API-002](../archive/2026-09-14/tasks/T-API-002.md) | perm-sdk 补齐 auth/query-resources 与 auth/query-scopes 调用入口（已收口 2026-09-06，终态见任务卡完成记录；其内部 id 裁剪的 check 族部分 2026-09-09 被推翻，见 T-API-003 与 registry 已推翻节） | [design-audit-followup](../archive/2026-09-14/design-audit-followup-plan.md)（已归档） | core-flows §15；api-contract；perm-sdk | — | ✅ | ✓ |
| [T-API-003](../archive/2026-09-12/tasks/T-API-003.md) | check 族三端点结果记录全量回传（推翻 T-API-002 check 族裁剪；Query* 六字段维持；已收口 2026-09-10，终态见任务卡实现记录） | [permission-query-unification](../archive/2026-09-12/permission-query-unification-plan.md) | api-contract §6.1/§6.2/§6.6；core-flows §15；T-API-002.md | — | ✅ | ✓ |

### 前端（前端 Phase 1/3/4 拆分）

> 来源：`docs/archive/2026-08-27/improvement-plan.md` §4 各 Phase 拆分（roadmap 已归档，拆分产物即各 phase plan）。Phase 1 archived（2026-07-12 归档）；Phase 3 联调九任务 2026-09-04 全部收口、2026-09-07 物理归档（[archive/2026-09-07](../archive/2026-09-07/frontend-phase3-plan.md)）；Phase 4 五任务 2026-09-12 全部收口、同日随批次归档（[archive/2026-09-12](../archive/2026-09-12/frontend-phase4-plan.md)）。页面任务 design_refs 先指后端契约，UI 设计随任务回写到 `docs/design/frontend/<page>.md`。

> ⚠️ **权限授予页 v1/v2 产物废弃（2026-07-26）；T-FE-018 已于 2026-08-01 按 v3 恢复（2026-09-02 收口 done）**：因对现有交互不满意，v1（`permission-grant`）+ v2（`permission-grant-v2`）两套页面及专属代码（`PermissionSummaryCell` / `ChildPermissionInline` / `RePermissionCell` / `ReConditionPicker` / `permission-grant-types` / `api/permission-grant` / `mock/permission-grant`）已删除，4 份设计文档归档至 `archive/2026-07-26/`，两个 plan 归档至 `archive/2026-07-26/`。下表 T-FE-014 / T-FE-024~026 / T-FE-029~034 保持 ✅（历史完成事实）但产出代码已废弃；T-FE-027 / T-FE-028 / T-FE-035 标 ❌ cancelled（不再恢复）；**T-FE-018 已于 2026-08-01 按 v3 恢复、2026-09-02 收口 done（v3 设计 `design/frontend/permission-grant.md`，终态见表格行）**。注：`ReConditionEditor` / `condition-rules` 保留，仍被 `permission-condition` 页使用。

| ID | 标题 | 计划 | 设计引用 | 依赖 | 状态 | 回写 |
|---|---|---|---|---|---|---|
| [T-FE-001](../archive/2026-07-12/tasks/T-FE-001.md) | 跨页组件抽象池（清单维护 + 派生子任务） | [frontend-phase1](../archive/2026-07-12/frontend-phase1-plan.md) | design/frontend/README.md | — | ✅ | — |
| T-FE-002 | 2.2 角色管理页（5 种角色类型 CRUD，本页仅消费功能角色） | frontend-phase1 | api-contract §5.2/§6.10.3；design/frontend/role-manage.md | T-FE-001 | ✅ | ✓ |
| T-FE-003 | 6.1 类型定义页（type_definition code↔value 映射 CRUD） | frontend-phase1 | api-contract §5.1；design/frontend/type-definition.md | — | ✅ | ✓ |
| T-FE-004 | 6.2 系统配置页（租户级 key-value 配置字典；标题校正与端点口径见 design/frontend/system-config.md） | frontend-phase1 | api-contract §5.8；design/frontend/system-config.md | — | ✅ | ✓ |
| T-FE-005 | 7.1 操作日志页（只读查询：module/action 服务端分页筛选 + 抽屉详情；门禁/端点口径见 design/frontend/operation-log.md） | frontend-phase1 | api-contract §5.8；design/frontend/operation-log.md | — | ✅ | ✓ |
| T-FE-006 | 5.1 业务域页（主从：BizDomain CRUD 主表 + DomainConfig 域配置子表；门禁矩阵与 configType 收窄口径见 design/frontend/biz-domain.md） | frontend-phase1 | api-contract §5.1/§5.6；design/frontend/biz-domain.md | — | ✅ | ✓ |
| T-FE-007 | 5.2 服务+接口映射页（服务注册 + 接口同步 + API 映射；边界/门控口径见 design/frontend/service-interface-mapping.md） | frontend-phase1 | api-contract §5.4/§6.3/§6.10.4；design/frontend/service-interface-mapping.md | — | ✅ | ✓ |
| T-FE-008 | 3.1 资源+操作定义页（资源树 CRUD + 操作定义 + 关联） | frontend-phase1 | api-contract §5.3；design/frontend/resource-operation.md | — | ✅ | ✓ |
| T-FE-009 | 3.2 权限条件页（通用条件模板 CRUD；门禁史与收口口径见 design/frontend/permission-condition.md） | frontend-phase1 | api-contract §5.6；design/frontend/permission-condition.md | - | ✅ | ✓ |
| [T-FE-010](../archive/2026-07-12/tasks/T-FE-010.md) | 3.3 冲突规则页（ROLE_MUTEX 角色互斥/PERM_MUTEX 权限互斥 CRUD + 冲突检测对话框；门禁口径见 design/frontend/conflict-rule.md） | frontend-phase1 | api-contract §5.6；design/frontend/conflict-rule.md | T-FE-001 | ✅ | ✓ |
| [T-FE-011](../archive/2026-07-12/tasks/T-FE-011.md) | 3.4 资源依赖页（依赖 CRUD + 依赖图 echarts graph + 环检测；门禁/契约口径见 design/frontend/resource-dependency.md） | frontend-phase1 | api-contract §5.6/§6.9；design/frontend/resource-dependency.md | T-FE-001, T-FE-008 | ✅ | ✓ |
| [T-FE-012](../archive/2026-07-12/tasks/T-FE-012.md) | 7.2 权限变更日志页（diff 快照 + before/after + 影响评估） | frontend-phase1 | api-contract §5.8/§6.8；design/frontend/permission-change-log.md | T-FE-001 | ✅ | ✅ |
| [T-FE-013](../archive/2026-07-12/tasks/T-FE-013.md) | 4.2 权限查询/校验页（多维度查询 + 权限解释 + 拒绝原因） | frontend-phase1 | api-contract §5.7/§6.6/§6.7/§6.8；design/frontend/permission-query.md | T-FE-001 | ✅ | ✅ |
| [T-FE-014](../archive/2026-07-12/tasks/T-FE-014.md) | 4.1 权限授予页（选角色→勾资源树→操作矩阵→绑条件→批量保存） | frontend-phase1 | api-contract §5.5/§6.4/§6.5；design/frontend/permission-grant.md | T-FE-001, T-FE-002, T-FE-008 | ✅ | ✅ |
| [T-FE-015](../archive/2026-09-07/tasks/T-FE-015.md) | Phase 3 联调：组织与用户（2.1 mock→真实接口；已收口 2026-08-31，终态见任务卡完成记录） | frontend-phase3（已归档） | api-contract；admin-service-api-contract | T-PERM-037 | ✅ | ✓ |
| [T-FE-016](../archive/2026-09-07/tasks/T-FE-016.md) | Phase 3 联调：角色管理（2.2 mock→真实收口；已收口 2026-09-01，终态见任务卡完成记录） | frontend-phase3（已归档） | api-contract | T-FE-002, T-PERM-022 | ✅ | ✓ |
| [T-FE-017](../archive/2026-09-07/tasks/T-FE-017.md) | Phase 3 联调：资源/操作定义（3.1 mock→真实收口；已收口 2026-09-02，终态见任务卡完成记录） | frontend-phase3（已归档） | api-contract | T-FE-008, T-PERM-028 | ✅ | ✓ |
| [T-FE-018](../archive/2026-09-07/tasks/T-FE-018.md) | Phase 3 联调：权限授予（4.1）- 角色联调（首期）；已收口 2026-09-02，终态见任务卡完成记录 | frontend-phase3（已归档） | api-contract；design/frontend/permission-grant.md（v3） | T-FE-036, T-FE-038, T-FE-039, **T-FE-040**, T-PERM-040, T-PERM-041, T-PERM-034, T-PERM-022/028/029/031 | ✅ | ✓ |
| [T-FE-037](../archive/2026-09-07/tasks/T-FE-037.md) | Phase 3 联调：权限授予（4.1）- 组织联调（二期）；已收口 2026-09-04，终态见任务卡完成记录 | frontend-phase3（已归档） | api-contract；design/frontend/permission-grant.md（v3） | T-FE-018, T-ADMIN-021 | ✅ | ✓ |
| [T-FE-019](../archive/2026-09-07/tasks/T-FE-019.md) | Phase 3 联调：权限查询/校验（4.2）；已收口 2026-09-02，终态见任务卡完成记录 | frontend-phase3（已归档） | api-contract | T-FE-013, T-PERM-033 | ✅ | ✓ |
| [T-FE-020](../archive/2026-09-07/tasks/T-FE-020.md) | Phase 3 联调：条件/冲突规则（3.2/3.3）；已收口 2026-09-02，终态见任务卡完成记录 | frontend-phase3（已归档） | api-contract | T-FE-009, T-FE-010, T-PERM-029, T-PERM-030 | ✅ | ✓ |
| [T-FE-021](../archive/2026-09-07/tasks/T-FE-021.md) | Phase 3 联调：业务域配置（5.1）；已收口 2026-09-02，终态见任务卡完成记录 | frontend-phase3（已归档） | api-contract | T-FE-006, T-PERM-026 | ✅ | ✓ |
| [T-FE-022](../archive/2026-09-07/tasks/T-FE-022.md) | Phase 3 联调：系统/服务配置与日志（6.x/5.2/7.x）；已收口 2026-09-03，终态见任务卡完成记录 | frontend-phase3（已归档） | api-contract | T-FE-003, T-FE-004, T-FE-007, T-FE-005, T-FE-012, T-PERM-023, T-PERM-024, T-PERM-025, T-PERM-027, T-PERM-032 | ✅ | ✓ |
| [T-FE-023](../archive/2026-09-12/tasks/T-FE-023.md) | 扩展性验证 + 扩展指南（已收口 2026-09-12：重定调全景指南落 design/extension-guide.md + CustomResourceTypeSlicePgIT 全绿；产品发现=新类型首笔授权引导缺口见任务卡遗留节） | [frontend-phase4](../archive/2026-09-12/frontend-phase4-plan.md) | design/extension-guide.md（新）；architecture；api-contract | — | ✅ | ✓ |
| [T-FE-024](../archive/2026-09-12/tasks/T-FE-024.md) | ReConditionPicker + ReConditionEditor + ChildPermissionInline 条件/子权限组件抽取 | [frontend-phase4](../archive/2026-09-12/frontend-phase4-plan.md) | design/frontend/permission-condition.md；permission-grant.md | T-FE-001, T-FE-009, T-FE-014 | ✅ | ✅ |
| [T-FE-025](../archive/2026-07-26/tasks/T-FE-025.md) | 权限授予中栏资源权限概览与授权入口 | [permission-grant-ux-refactor](../archive/2026-07-26/permission-grant-ux-refactor-plan.md) | design/frontend/permission-grant.md §16.3/§16.8 | T-FE-014 | ✅ | ✅ |
| [T-FE-026](../archive/2026-07-26/tasks/T-FE-026.md) | 权限授予授权弹窗（批量授权任务） | [permission-grant-ux-refactor](../archive/2026-07-26/permission-grant-ux-refactor-plan.md) | design/frontend/permission-grant.md §16.4/§16.8 | T-FE-024, T-FE-025 | ✅ | ✅ |
| [T-FE-028](../archive/2026-07-26/tasks/T-FE-028.md) | 权限授予右栏本次变更记录 | [permission-grant-ux-refactor](../archive/2026-07-26/permission-grant-ux-refactor-plan.md) | ~~permission-grant.md §16.5/§16.8~~（已删） | T-FE-026 | ❌ | ⏳ |
| [T-FE-029](../archive/2026-07-26/tasks/T-FE-029.md) | 权限授予V2页面骨架+路由+三栏+角色树+能力门控 | [permission-grant-v2](../archive/2026-07-26/permission-grant-v2-plan.md) | design/frontend/permission-grant-{state-model,interaction}.md | - | ✅ | — |
| [T-FE-030](../archive/2026-07-26/tasks/T-FE-030.md) | 方案A前端模型（GrantVariantId+replay+聚合摘要） | permission-grant-v2 | design/frontend/permission-grant-state-model.md §0/§1/§2.2/§7.1 | T-FE-029 | ✅ | — |
| [T-FE-031](../archive/2026-07-26/tasks/T-FE-031.md) | V2中栏直接操作矩阵+单元格聚合摘要+分支列表就地展开 | permission-grant-v2 | design/frontend/permission-grant-interaction.md §2/§4.1/§4.2；state-model §6.2 | T-FE-030 | ✅ | — |
| [T-FE-032](../archive/2026-07-26/tasks/T-FE-032.md) | V2授权交互（点击/添加分支/逐分支编辑撤销/批量新增分支）+R11 | permission-grant-v2 | design/frontend/permission-grant-{interaction,state-model}.md §3.4/§4.1/§4.3 | T-FE-031 | ✅ | ✅ |
| [T-FE-033](../archive/2026-07-26/tasks/T-FE-033.md) | V2子权限矩阵展开（parentVariantId）+两步保存+条件清除wire | permission-grant-v2 | design/frontend/permission-grant-{state-model,interaction,error-flow}.md §4.2 | T-FE-032 | ✅ | ✅ |
| [T-FE-034](../archive/2026-07-26/tasks/T-FE-034.md) | V2保存前总览+失败两子态+STALE_WITH_CHILD_FAILURE+fetchBaseline+离开保护 | permission-grant-v2 | design/frontend/permission-grant-{state-model,error-flow,interaction}.md §2/§2.5/§4.5 | T-FE-033 | ✅ | ✅ |
| [T-FE-035](../archive/2026-07-26/tasks/T-FE-035.md) | 扩展V2 transport（多条件+失败模拟）+失格降级+回归验证+设计回写 | [permission-grant-v2](../archive/2026-07-26/permission-grant-v2-plan.md) | design/frontend/permission-grant-{error-flow,state-model}.md §2.8/§8 | T-FE-034 | ❌ | ⏳ |
| [T-FE-027](../archive/2026-07-26/tasks/T-FE-027.md) | 权限授予三栏状态整合、回归验证与设计回写 | [permission-grant-ux-refactor](../archive/2026-07-26/permission-grant-ux-refactor-plan.md) | ~~permission-grant.md §16.6~§16.9~~（已删） | T-FE-025, T-FE-026, T-FE-028 | ❌ | ⏳ |
| [T-FE-036](../archive/2026-09-14/tasks/T-FE-036.md) | 4.1 权限授予页重设计（v3：查看为主+操作中心授权弹窗+详情层+变更清单；范围与 DoD 见任务卡） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | design/frontend/permission-grant.md（v3）；api-contract §5.5/§6.4/§6.5/§6.5.1 | T-FE-001, T-FE-002, T-FE-008, T-FE-009 | ✅ | ✓ |
| [T-FE-038](../archive/2026-09-14/tasks/T-FE-038.md) | 4.1 权限授予页单类型矩阵上下文 | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | design/frontend/permission-grant.md §2.2/§3.1/§3.2/§3.5/§3.6/§11/§13.2/§13.4；api-contract §5.1/§5.3/§6.4 | T-FE-036 | ✅ | ✅ |
| [T-FE-039](../archive/2026-09-14/tasks/T-FE-039.md) | 4.1 矩阵图标正交状态模型与图标精简 | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | design/frontend/permission-grant.md §3.3/§6.2/§11（S2/S5/S6/S10/S11）/§13.5；api-contract §6.5.1（20041 配套） | T-FE-038 | ✅ | ✅ |
| [T-FE-040](../archive/2026-09-14/tasks/T-FE-040.md) | 4.1 授权弹窗 v3.1 记录级聚焦编辑（决策记录见任务卡；mock-first） | [frontend-phase2](../archive/2026-09-14/frontend-phase2-plan.md)（已归档） | design/frontend/permission-grant.md（v3.1）；api-contract §6.5.1/§6.5.2；archive/2026-09-12/permission-grant-record-level-editing-proposal.md | T-FE-039 | ✅ | ✓ |
| [T-FE-041](../archive/2026-08-27/tasks/T-FE-041.md) | 前端真实登录链路与默认导航收敛 | product-vertical-slice（已归档） | admin-service-api-contract；gateway；frontend/README；frontend/login | T-ACCESS-020 | ✅ | ✓ |
| T-FE-042 | ~~前端默认导航收敛~~（❌ cancelled 2026-08-23：范围并入 T-FE-041，同为前端发布面避免任务碎片化） | product-vertical-slice（已归档） | frontend/README | — | ❌ | — |
| [T-FE-043](../archive/2026-09-12/tasks/T-FE-043.md) | 权限排查页（permission-query）重做——暂停期问题与功能登记（已 cancel：页面随 T-PERM-059 删除，重做考虑事项随卡归档，新形态另立任务） | — | design/frontend/permission-query.md（已归档）；api-contract §6.7 | — | ❌ | — |
| [T-FE-044](../archive/2026-09-14/tasks/T-FE-044.md) | Phase 3 补遗联调：资源依赖（3.4 mock→真实收口 + Gateway 端点注册 + mock 终态化；Q-011/Q-012 转出；✅ 2026-09-14 收口：api 路径 /perm 前缀缺陷修复 + URL 契约锁，终态见任务卡完成记录） | —（Q-011 转出单卡任务） | api-contract §12.3；design/frontend/resource-dependency.md | T-FE-011, T-PERM-031 | ✅ | ✓ |
| [T-FE-045](../archive/2026-09-20/tasks/T-FE-045.md) | 登出真注销（接 /auth/logout）（✅ 2026-09-19 收口：logout() 显式携 Authorization 头+http 请求白名单纳入防过期分支递归；logOut 服务端注销优先/本地清理无条件/串行防抖；回归锁×7 红跑实证；login.md 新增登出流程节；claude 外评 P2×1 维持现状+P3×1 登记 Q-016，见 registry 处置行） | [frontend-session-consistency](../archive/2026-09-20/frontend-session-consistency-plan.md) | design/frontend/login.md | — | ✅ | ✓ |
| [T-FE-046](../archive/2026-09-20/tasks/T-FE-046.md) | 强制改密闭环（自助改密页+阻断）（✅ 2026-09-20 收口：登录写 userId/forceResetPwd 入 userKey+守卫 8 路径放行清单阻断 redirect /change-password+/change-password 页（8-32 位无旧密码）+改密成功清标记会话保留+登录 warning 分支退役；双轨评审 P2×2（/redirect 死项删+补锁、契约/后端注释滞后顺改）+P3×2 全处置；两项拍板（toast 维持现状、E2E 登记计划归档时跑）；红跑 6 红实证；端到端验收（acceptance 第 5 条）2026-09-20 归档批次执行通过（六项判据见归档计划当前进度末行）。claude 外评处置完毕（2026-09-20 模型 deepseek-flash[1m]：P0-P2=0、P3×2 全采纳直修——纯空白密码 whitespace 规则+锁（后端 isBlank 随机替换锁死用户）+登录落点三处文案同步（MemberTab×2 退役口径+半成功「进入系统后」限定）、存量×6 处置（改密页无登出入口注记入卡）；vitest 325/325；定案与明细见 registry 同日处置行） | [frontend-session-consistency](../archive/2026-09-20/frontend-session-consistency-plan.md) | design/frontend/login.md | — | ✅ | ✓ |
| [T-FE-047](../archive/2026-09-20/tasks/T-FE-047.md) | 用户删除二次确认与错误反馈（✅ 2026-09-19 收口：handleDelete 确认弹窗+失败错误反馈，hook 层两入口全覆盖；红跑 3 锁全红实证；claude 外评 P0-P2=0/P3×1 已修+存量×2 处置（Q-017+T-FE-051 扩行），见 registry 处置行） | [frontend-session-consistency](../archive/2026-09-20/frontend-session-consistency-plan.md) | — | — | ✅ | — |
| [T-FE-048](../archive/2026-09-20/tasks/T-FE-048.md) | 会话权限热刷新（403 触发+手动入口）（✅ 2026-09-20 收口：`refreshSessionCapability` 统一入口（权限串+侧栏原子更新，T-FE-056 挂接点预留）四调用方共用（initRouter/403 自动/顶栏手动/retryLoadDeps）；http 拦截器 403 分支 10s 窗口去重+失败静默+不重放+排除 user-menu 自身；顶栏「刷新权限」成功/失败双 message 口径；红跑 8 红/9 绿实证（三把验收锁全红）；双轨评审 P0-P2 代码轨=0/文档轨=P2×1（registry 补登记即本批完成）+P3 处置×5（三项拍板接受/登记 Q-016、重试按钮补 loading 采纳）；claude+codex 双通道外评处置完毕（2026-09-20：claude deepseek-flash[1m] P0-P2=0/P3×1、codex luna max P2×2，逐条亲核成立；两项拍板均登记不修——同会话并发覆盖 Q-016 两变体扩行、mix/horizontal 布局缺口修法定向抽公共组件暂不实施；零代码改动；定案与明细见 registry 同日处置行） | [frontend-session-consistency](../archive/2026-09-20/frontend-session-consistency-plan.md) | design/frontend/login.md；design/frontend/permission-grant.md | — | ✅ | ✓ |
| T-FE-049 | 登录半成功语义修正（✅ 2026-09-19 收口：登录提示按菜单状态两态——半成功/零权限 warning 诚实告知（非阻断，`resolveLoginMessages` 唯一出口，T-FE-046 阻断分支扩展点）+ store `menuLoadFailed` 空侧栏两态事实来源 + 占位项/`/menu-retry` 着陆页两态（失败可重试 vs 无可用菜单引导联系管理员）；红跑 9 红实证；双轨评审处置（P2 registry 补行+P3×3+Q-020）+ claude 外评处置（P2×1 占位项未注册 name 删+补锁、P3×2 mock 注释/meta.title 中性化、存量图标 2 枚顺带注册+Q-021 登记）；vitest 303/303 全绿） | [frontend-session-consistency](../archive/2026-09-20/frontend-session-consistency-plan.md) | design/frontend/login.md | — | ✅ | ✓ |
| [T-FE-050](../archive/2026-09-20/tasks/T-FE-050.md) | findParentOrgName 递归 bug 修复（仅 user/index.vue）（✅ 2026-09-19 收口：未找到返 null 不剪兄弟子树，提取 utils/orgTree.ts；红跑 4 红 1 绿实证；Q-017 拍板留给 T-FE-051；双轨评审 P0×1+P3×3 处置（rule §2 对齐工具链/Q-018/051 锚点）；claude 外评 P0-P3=0+存量×2（Q-018 注记/Q-019），见 registry 定案行） | [frontend-session-consistency](../archive/2026-09-20/frontend-session-consistency-plan.md) | — | — | ✅ | — |
| [T-FE-051](../archive/2026-09-20/tasks/T-FE-051.md) | 列表加载统一错误处理+请求代际（composable 化）（✅ 2026-09-19 收口：useListLoad/usePagedList 两层 composable + toErrorMessage 统一错误文案（非 2xx 优先后端 body message，service-interface 局部副本退役）；16 加载面顺扫/收敛（user 页试点+八页代际+reqSeq/configReqSeq/treeRequestSeq 三先例收敛，matrixToken 矩阵面不收敛）；Q-017 就地化拍板收敛+启停移入 hook 透后端文案；红跑 user 7红/config 2红/pcl 1红实证；失败语义统一保留旧数据（pcl/resource-dependency 两页+biz-domain 子表刷新路径变更）；vitest 279/279 全绿；claude 外评 P0-P2=0+P3×3 全采纳处置，见 registry 处置行） | [frontend-session-consistency](../archive/2026-09-20/frontend-session-consistency-plan.md) | — | — | ✅ | — |
| [T-FE-052](../archive/2026-09-20/tasks/T-FE-052.md) | 跨层级拖拽+角色禁用加二次确认 | [frontend-session-consistency](../archive/2026-09-20/frontend-session-consistency-plan.md) | design/frontend/role-manage.md | — | ✅ | ✓ |
| T-FE-053 | 路由守卫 next() 后补 return+删 meta.roles 死分支（✅ 2026-09-19 收口：两项拍板=②+③组合（删 roles 死分支+全部 next 站点统一 return，触发面为零实证）+新建守卫 spec 首次可测化（红锁「声明 meta.roles 不再拦 403」旧实现 1 红+5 特征锁绿实证；vitest 309/309+typecheck/lint/build 全 0）；utils.ts filterNoPermissionTree 维持现状；双轨代码轨全零/文档轨 P3×1 回写；claude 外评 P0-P2=0+P3×1 头注表述限定直修+守卫单注册补锁，见 registry 定案/处置行） | [frontend-session-consistency](../archive/2026-09-20/frontend-session-consistency-plan.md) | — | — | ✅ | ✓ |
| [T-FE-054](../archive/2026-09-20/tasks/T-FE-054.md) | token 过期拦截器短路+过期提示（✅ 2026-09-20 收口：四项拍板〔Q-016 fire-and-forget 收口/Q-020 initRouter 无凭证分支/双分支统一提示/页面层照弹〕落地；红跑 10 红/28 绿实证；vitest 348/348+typecheck/lint/build 全 0；双轨评审代码轨 P3×3+文档轨 P3×5 逐条亲核全成立直接修；Q-016/Q-020 随卡收敛；定案见 registry 同日行。claude 外评处置完毕（2026-09-20 模型 deepseek-flash[1m]：P0-P1=0、P2×1+P3×3 全成立直修——判据单源 isSessionTerminated〔含 cookie 清+userKey 残留形态〕+守卫 then 连带补 catch/代际守卫仅「另一活会话」才拦/短路 SessionExpiredError+classifySaveError 识别/401 令牌仍在才提示；红跑 5 红/73 绿；vitest 352/352；定案见 registry 同日处置行）。codex sol 复评处置完毕（2026-09-20 模型 gpt-5.6-sol xhigh：P0-P1=0、P2×2 直修〔均 claude 处置轮次生缝隙〕——终结后成功响应不回写〔sessionAlive/sessionReplaced 拆分〕+无凭证请求同短路〔无条件单源判据〕；过度设计检查可裁剪项=0〔用户叮嘱，8 机制全判定必要且最小〕；NaN fail-closed 建议不采纳〔不可达形态防御〕；红跑 2 红/30 绿+vitest 354/354；定案见 registry 同日处置行）） | [frontend-session-consistency](../archive/2026-09-20/frontend-session-consistency-plan.md) | design/frontend/login.md | — | ✅ | ✓ |
| T-FE-055 | 授予入口按钮按 MANAGE/VIEW 分文案（✅ 2026-09-20 收口：三入口文案二分「权限授予」/「查看权限」——grant-entry.ts 单源、门禁维持 ROLE:VIEW、canGrant 同批换绑授予页 SSOT 值等价；GrantMatrixPanel 授权按钮 view 态 v-if 隐藏→禁用态+tooltip 对齐 §10 表原文；三项拍板与维持现状处置见 registry 同日行；spec 模块缺失红起+两态锁；vitest 356/356+typecheck/lint/build 全绿；双轨评审 P0-P2=0、P3×5（重叠计 4+1）逐条亲核属实全直修、过度设计可裁剪项=0。claude+codex sol 外评处置完毕（2026-09-20：claude deepseek-flash[1m] P0-P2=0/P3×2、codex sol xhigh P2×1/P3×2 逐条亲核——**codex P2 capability 快照不随权限热刷新重算经用户拍板根因修=canManage 响应式派生**〔§6.5「仅由门禁派生」忠实实现，grant-store 删快照字段/action，未选主体/升权虚假 tooltip 与降权可操作三形态全消；红跑 stash 快照 2 红实证〕；claude P3-1 tooltip 判据不对称随派生化消解；文案位漏扫四处直修〔quickstart 按 extension-guide 先例类推〕；看板行承载用户拍板维持惯例；存量注释×2 顺手修；过度设计=0；vitest 358/358+typecheck/lint/build 全 0；定案与明细见 registry 同日处置行）。claude 复评处置完毕（2026-09-20 复评轮 deepseek-flash[1m]：P0-P2=0/P3×3 逐条亲核——上轮五项处置逐项核实闭合；P3-2 红跑锁判别力批评成立重写为响应式桩三态翻转锁〔回退形态 1 红实证〕+P3-3 canManage 死解构直修+P3-1 降权弹窗暂态拍板登记 §6.5 边界句+存量类推 resource-dependency 四门控 computed 化〔全仓唯一孤例〕；vitest 357/357；定案见 registry 同日复评处置行） | [frontend-session-consistency](../archive/2026-09-20/frontend-session-consistency-plan.md) | design/frontend/permission-grant.md；design/frontend/role-manage.md | — | ✅ | ✓ |
| [T-FE-056](../archive/2026-09-20/tasks/T-FE-056.md) | menus 派生路由级 UX 门禁（✅ 2026-09-20 收口：gate.ts 三源判定〔menus path 集响应式派生 ∪ remaining.ts 全量白名单含参数路由前缀化 ∪ /perm/grant 显式映射 ROLE:VIEW〕+守卫状态机 uninitialized/loading/loaded/failed〔uninitialized/loading 等待初始化防冷启动深链绕过、failed fail-open、刷新失败维持 loaded 用旧 path 集〕+拦截落点全屏 /access-denied〔用户拍板，弃 /error/403 带壳〕；红跑 10 红实证+vitest 383/383+typecheck/lint/build 全 0；十册「路由可达性」口径清扫+代码注释四处〔T-PERM-037「路由可达、后端 403 兜底」退役为门禁+后端双层〕；双轨评审两轨 P0-P2=0、P3×7 全直修〔menu-retry 预清 menus×刷新失败门禁空集锁死形态用户拍板直接修——提取 menu-retry-reload.ts 失败回滚旧菜单集，主锁红跑实证〕、过度设计可裁剪项=0；定案与明细见 registry 同日行） | [frontend-session-consistency](../archive/2026-09-20/frontend-session-consistency-plan.md) | design/frontend/login.md 等 10 册（含 conflict-rule/permission-condition/resource-operation/resource-dependency 四册随卡补入，全清单见任务卡 design_refs） | T-FE-053 | ✅ | ✓ |

---

## 建议执行顺序

依据：①评审定级（A/B/C 为 P0）②依赖解锁价值 ③验收闭环优先 ④无依赖可立即并行。

### product-vertical-slice（✅ 2026-08-27 收口归档：里程碑 A + B 全部达成，18 项任务全 done；计划见 [archive/2026-08-27](../archive/2026-08-27/product-vertical-slice-plan.md)）

**里程碑 A（核心可运行）：**

1. `T-ACCESS-016` 设计定稿 ∥ `T-ACCESS-017` 窄回归安全网 + 最小 CI（无依赖，可并行）
2. 模型收敛串行：`T-PERM-042` 显式资源 API → `T-ORG-001` 统一主体 ID → `T-ACCESS-018` 类型收敛 → `T-ACCESS-019` USER/ROLE 投影（各自独立提交；投影以最终主体 ID + 最终类型码一次到位，无过渡转换层）
3. `T-ACCESS-020` bootstrap（双角色双用户模型：首管理员 + 管理用功能角色按管理 API 清单双层最小授权，含授权页读接口；不向目标角色/用户预授目标 API）→ `T-FE-041` 前端真实登录 + 导航收敛（里程碑 A 只显示冒烟通过的页面）
4. `T-ACCESS-021` E2E 垂直切片验收 + README 回写（计划总目标载体，测试全绿不替代；目标用户与普通 BASIC_ROLE 在场景内经管理链路创建）

**里程碑 B（试点加固，不反向阻塞 A 的达成声明；硬门禁：全部 B 任务 depends_on T-ACCESS-021，A 未完成不启动 B）：**

5. `T-ADMIN-022` / `T-PERM-043` / `T-ADMIN-023` / `T-GW-007` / `T-ACCESS-024` / `T-ACCESS-025` / `T-ADMIN-024`（B 内互不阻塞，避免与同链路任务并发；T-ADMIN-022 另依赖 T-ORG-001、T-PERM-043 另依赖 T-ACCESS-019）
6. `T-API-001` example 接入（Gateway 主线）→ `T-ACCESS-026` 验证证据与文档状态收口（依赖全部 B 任务 + T-API-001；含 access-post-merge-plan 归档，CI 已由 T-ACCESS-017 前置落地）

跨计划：63 位掩码精度归 `T-PERM-028`（frontend-phase2），建议在步骤 5 前完成以免前端授权页联调返工。T-FE-042 已 cancelled（范围并入 T-FE-041）。

### access-service 归并（✅ 完成 2026-08-22，主链 T-ACCESS-001~012 全部 done）

1. 归并主链已全部完成并归档（`docs/archive/2026-08-22/access-service-merge-plan.md`）；后续强化（T-ACCESS-013 OAuth2 资源服务器、T-ACCESS-015 菜单写链路收口）已完成并归档（`docs/archive/2026-08-27/access-post-merge-plan.md`）。
2. 后端门禁解除：重基线后的 T-PERM/T-ADMIN 任务按各自 `depends_on` 推进（T-PERM-022~041、T-ADMIN-020/021）。
3. 前端真接口联调（T-FE-015~022）后端依赖已就绪（T-PERM-037 收口 2026-08-31，逐页后端 T-PERM-022~034/037/040/041 全部完成；T-FE-015/016 已收口 2026-08-31/09-01——menus 后端派生接线/默认树/固定图扩容落地，后续各页联调按 T-FE-015 模式扩 Gateway 端点清单；暂缓项 T-PERM-036 另行定夺（T-PERM-035 已定稿拆卡 070~073）；T-ADMIN-021 已收口 2026-09-03，T-FE-037 二期依赖全部就绪）；纯 mock/UI 任务不受影响。
4. access-post-merge 准入前置已由 T-ACCESS-017 最小 CI 落地关闭（GitHub Actions 两 job 以退出状态判定成功；原登记 40 项与 68 项实测均为历史口径，不维护计数同步）。

### access-service 能力包融合（2026-09-13 立项）

执行顺序以 [access-capability-fusion-plan](../archive/2026-09-14/access-capability-fusion-plan.md) 的「建议执行顺序」节为唯一权威（计划已归档，顺序已执行完毕）（032 设计定稿 → 033 机械迁移 → 040 契约先行 → 收敛项并行 → 041 收尾）；依赖关系以各任务卡 frontmatter `depends_on` 为准。

### P0 — 验收闭环（✅ 已完成 2026-06-20）

`T-ADMIN-001~016`：16 项验收 + 设计回写完成，转 ✅ done。计划 `user-role-proxy-fix` 满足归档条件（待执行归档至 `docs/archive/`，看板清理 16 项至"已完成"区）。无新开发。

### P1 — 工作单 A 缓存失效（安全风险，第 1 周首位）

按依赖解锁顺序：

1. `T-PERM-001` 快照模式 — ✅ done（枢纽，解锁 002/006/T-GW-003）
2. `T-PERM-003` 删 permission_version — ✅ done（解锁 004/005/007）
3. `T-PERM-002` AOP afterCommit — ✅ done
4. `T-PERM-006` Redis 广播+订阅器 — ✅ done（消费 T-PERM-018 serviceCodes 载荷；解锁 T-GW-005 设计）
5. `T-PERM-004` 删 increment — ✅ done
6. `T-PERM-005` 删缓存目录条目 — ✅ done
7. `T-PERM-007` 文档一致性核对 — ✅ done（2026-06-27）
8. `T-PERM-008` 失效标记代码 ← T-GW-005 ✅（已完成：stale store / InvalidationMarker / in-flight 去重 / 订阅恢复全清）

### P2 — 工作单 B scopeMode（数据泄露风险，与 A 完全并行）

1. `T-PERM-009` 枚举+响应结构 — ✅ done（枢纽）
2. `T-PERM-010` §6.7 改造 — ✅ done（范围合并进 T-PERM-009）
3. `T-PERM-011` 30+处全量推广 → ✅ done
4. `T-PERM-012` 管理端/排查页 → ✅ done
5. `T-PERM-013` schema 映射逻辑 ← 009 → ✅ done
6. `T-PERM-015` 前端 ScopeMode 类型 + composable ← 009 → ✅ done
7. `T-PERM-014` 迁移注记→正式定义 → ✅ done

### P3 — 工作单 C Gateway 兜底（依赖 A）

1. `T-GW-001` fail-mode 配置 — 无依赖，可与 A 并行启动
2. `T-GW-002` fail-closed ← 001
3. `T-GW-003` stale-allow ← T-PERM-001(快照)+T-GW-001（A 落地后才能做）
4. `T-GW-004` 监控指标 ← 002
5. `T-GW-005` S-006 设计 ← T-PERM-006(广播载荷) → ✅ done（2026-06-28）
6. `T-GW-006` 集成测试 ← 002（重新界定：不在项目内做集成测试，改为独立仓库测试服务）
7. `T-PERM-008` 失效标记代码 ← T-GW-005（已完成，回到 A 链收尾）

### P4 — 工作单 D/E/F（✅ 全收口 2026-09-12：D/E/F 三任务全 done，计划随 T-PERM-021 收口归档 archive/2026-09-12）

1. `T-PERM-019` 工作单 D 防呆机制 — ✅ 已收口（2026-09-07）：D1 已随 T-PERM-023/api-contract 收口（原 DESIGN_DRIFT 不存在）、D2 BusinessKeyUtil 收敛、D3 三方一致性核对落地。完成记录见任务卡。
2. `T-PERM-020` 工作单 E 清理预设 — ✅ 已收口（2026-08-28）：删除零调用 `forResourceQuery`/`forResourceCheck`（`forValidate` 保留，有生产调用）；domain_config schema 表头注释、AGENTS/copilot MQ 口径同步；auto-grant 禁用态核实已收敛。完成记录见任务卡。
3. `T-PERM-021` 工作单 F 文档准确性与代码简化 — ✅ 已收口（2026-09-12）：执行门禁三项拍板（registry 同日行）后七子项全处置——F1.a 清残留活计数定性收口、F1.b 盘点另立 T-PERM-065、F1.c 两列归属口径钉死、F1.d requestId 单 ID 收敛+审计两列 NOT NULL（含回归锁 6 用例）、F1.e ops runbook 新建、F1.f 死分支删除、F1.g 四副本抽取；双轨评审 P2×3+P3×13 处置；全量回归含 E2E 全绿。完成记录见任务卡。

### P5 — 前端 Phase 1（archived，2026-07-12 归档）

> D/E/F 重启前提「前端 Phase 1 收尾」的关键路径。按已归档 improvement-plan（archive/2026-08-27/）§4.1 三批次（简单→复杂）推进。Phase 1 **不改后端**，各页前端任务在 API 核对中产出 🔧❌ 清单，登记为 Phase 2 后端任务 T-PERM-022~034。

**第 1 批 🟢🟡**：`T-FE-002` 角色管理 → `T-FE-003` 类型定义 → `T-FE-004` 系统配置 → `T-FE-005` 操作日志
**第 2 批 🟡**：`T-FE-006` 业务域 → `T-FE-007` 服务+接口 → `T-FE-008` 资源+操作 → `T-FE-009` 权限条件
**第 3 批 🔴**：`T-FE-010` 冲突规则 → `T-FE-011` 资源依赖 → `T-FE-012` 变更日志 → `T-FE-013` 权限查询 → `T-FE-014` 权限授予

配套：`T-FE-001` 跨页组件抽象池（贯穿，2+ 页确认后派生组件子任务）。后端任务 T-PERM-022~034 归 Phase 2（depends_on 对应前端，等清单产出）。

### P6 — 前端 Phase 2/3/4（Phase 2 逐页后端已完成 2026-08-31、计划 2026-09-14 归档，暂缓项 T-PERM-035/036 脱出挂任务表；Phase 3 联调 9/9 全部收口——T-FE-015~022 已收口 2026-08-31~09-03、T-FE-037 组织联调二期收官 2026-09-04）

- `T-PERM-022~034` 逐页后端接口改造 ← 各 Phase 1 前端任务（🔧❌ 清单）
- `T-PERM-037` 跨页共性接口改造 + api-contract 回写收尾 ← T-PERM-022~034（不重复逐页改造）
- `T-PERM-036` 动态数据权限 — ⚠️ design-review §11 暂缓，需 PM 重申（T-PERM-035 自动授权已于 2026-09-19 定稿并拆卡 T-PERM-070~073，暂缓解除）
- `T-FE-015~022` Phase 3 联调 ← Phase 1 + Phase 2
- `T-FE-023` 扩展验证 / `T-ADMIN-020` 代码清理 / `T-PERM-038` TODO 收口 / `T-PERM-039` 测试补充 — Phase 4（✅ 全部 done，2026-09-12 随计划归档）

### P7 — 权限授予授权弹窗与右栏变更重构（❌ 已取消 2026-07-26）

> 页面交互不满意，v1+v2 两套整体删除重做。T-FE-024~026 保持 done（产出已废），T-FE-027/028 cancelled；**T-FE-018 已于 2026-08-01 按 v3 恢复（不再 cancelled）、2026-09-02 收口 done（见表格行）**。详见上方"前端"段废弃说明。plan 已归档至 `archive/2026-07-26/permission-grant-ux-refactor-plan.md`。

1. `T-FE-024` 条件/子权限组件抽取（含 ChildPermissionDrawer 内联化）← T-FE-014（外部前置，可与 T-FE-025 并行）
2. `T-FE-025` 中栏资源权限概览 + 授权入口 ← T-FE-014
3. `T-FE-026` 授权弹窗（批量授权任务）← T-FE-024, T-FE-025
4. `T-FE-028` 右栏本次变更记录 ← T-FE-026
5. `T-FE-027` 三栏状态整合、回归与设计回写 ← T-FE-025, T-FE-026, T-FE-028
6. `T-FE-018` 真接口联调 ← T-FE-027, T-PERM-034（**被 2026-08-01 重设计决策取代**：T-FE-018 已按 v3 恢复，实际依赖 = T-FE-036 + T-PERM-034（已 done，2026-08-30）+ T-PERM-022/028/029/031；组织二期 T-FE-037 另依赖 T-ADMIN-021，见 phase3-plan；T-PERM-031 已 done——原「全局操作阶段 2-4」划分被 T-PERM-028/040 覆盖，2026-08-30 清扫）

准入门禁：~~`design/frontend/permission-grant.md` §16.8 R1~R11~~（设计文档已归档至 `docs/archive/2026-07-26/`）。plan 已归档取消（2026-07-26），T-FE-024~028 随页面删除废弃/取消。

### P8 - 权限授予 V2（方案A多条件分支模型，❌ 已取消 2026-07-26）

> 页面交互不满意，v1+v2 两套整体删除重做。T-FE-029~034 保持 done（产出已废），T-FE-035 cancelled。详见上方"前端"段废弃说明。plan 已归档至 `archive/2026-07-26/permission-grant-v2-plan.md`。

1. `T-FE-029` V2 页面骨架+路由+三栏+角色树+能力门控 ✅ ← -
2. `T-FE-030` 方案A前端模型（GrantVariantId+replay+聚合摘要） ✅ ← T-FE-029
3. `T-FE-031` 中栏矩阵+单元格聚合摘要+分支列表就地展开 ✅ ← T-FE-030
4. `T-FE-032` 授权交互（点击/添加分支/逐分支编辑撤销/批量新增分支）+R11 ← T-FE-031
5. `T-FE-033` 子权限矩阵（parentVariantId）+两步保存+条件清除wire ← T-FE-032
6. `T-FE-034` 保存前总览+失败两子态+STALE_WITH_CHILD_FAILURE+fetchBaseline+离开保护 ← T-FE-033
7. `T-FE-035` 扩展V2 transport（多条件+失败模拟）+失格降级+回归验证+设计回写 ← T-FE-034

准入门禁：~~已关闭~~（plan 已归档取消，2026-07-26）。详见 [归档 plan](../archive/2026-07-26/permission-grant-v2-plan.md)。v1+v2 两套页面整体删除重做，T-FE-029~034 保持 done（产出废弃）、T-FE-035 cancelled。

### P9 — 产品定位落地（2026-08-28 立项，定位定案：开源通用 IAM）

> 设计定案（2026-08-28）：产品定位 = 开源通用 IAM（通用多租户访问控制平台）；暂缓能力维持暂缓（自动授权写入口 20048 预留禁用、动态数据权限延后 example-service，等 PM 重申重启）。计划已归档（[archive/2026-09-07](../archive/2026-09-07/product-positioning-landing-plan.md)）。【2026-09-19 更新：自动授权已定稿立项（T-PERM-070~073，20048 随 auto_grant 列退役）；仅动态数据权限（T-PERM-036）维持暂缓】

1. `T-ACCESS-028` perm-data 空装配模块删除 — ✅ done（2026-08-28）
2. `T-ACCESS-027` 文档三档叙事整改 — ✅ done（2026-08-28：三档口径入口落位 design/README、architecture §4.2/§4.3 演示模块重写三档标注、决策过程标注清扫 27 处；计划已 completed，物理归档待后续批次）

后续节奏（2026-08-28 定案，所列批次现已全部完成）：「简单页后端改造 T-PERM-022~025」2026-08-28 收口、「权限授予主链 T-PERM-034/040/041」2026-08-30~31 收口、Phase 3 联调 2026-09-04 全部收口；当前待办以任务板各分区状态为准。

### 不排期（待立项）

_（暂无——原登记 EXT-7 已立项 T-PERM-061、EXT-8 已失效，2026-09-11 随 T-PERM-060 处置）_

### 已完成的三个枢纽

`T-PERM-001`（A 链根）+ `T-PERM-002`（AOP afterCommit）+ `T-PERM-003`（A 链删version根）+ `T-PERM-004/005`（删 increment / 缓存目录残留）+ `T-PERM-006`（A 链广播订阅）+ `T-PERM-007`（文档一致性核对）+ `T-PERM-008`（Gateway 失效标记与订阅恢复代码）+ `T-PERM-009`（B 链根）+ `T-PERM-011`（scopeMode 契约全量推广）+ `T-PERM-012`（管理端/排查页响应改造）+ `T-PERM-013`（协议层 scopeAll→scopeMode 映射）+ `T-PERM-014`（scopeMode 正式定义收尾）+ `T-PERM-015`（前端 ScopeMode 类型 + composable）均已完成。`T-GW-005`（S-006 失效标记设计）已完成。

## 依赖告警（dangling）

> 当被依赖的任务 `cancelled` 或设计被 `superseded` 时，下游任务在此登记，等待重连。

_（暂无）_

## 设计变更待核对


> 当设计文件 `status` 变为 `superseded` 或章节实质变更时，`design_refs` 指向它的任务在此登记，等待核对验收与回写目标是否仍成立。

| 设计变更 | 受影响任务 | 核对状态 | 处理要求 |
|---|---|---|---|
| [自动授权简化方案](../design/dependency-auto-grant.md)采纳及 M1/M2/M3/M5 已定边界 | T-PERM-071/072/073/078 | 已核对（2026-09-20），design_refs 与验收已更新 | 078 设计校准已完成，071 前置门禁通过；071～073 按直接依赖实施 |
| `docs/design/schema/` 四份旧 DDL（admin-service.sql / permission-center.sql / seed-admin-operations.sql / seed-perm-operations.sql）标记 superseded，权威 DDL 为 access-service.sql（2026-08-12，T-ACCESS-002） | T-ACCESS-012；T-PERM-019/020/021/034/041（proposed 待重基线） | ✅ 已收口（2026-08-22，T-ACCESS-012：四文件物理归档 `docs/archive/2026-08-22/schema/`；全仓活引用切换 access-service.sql；T-PERM-019/020/021/034/041 已重基线） | 实现与测试以 `schema/access-service.sql` 为唯一依据（已达成；T-PERM-013 为 done 历史事实不改） |
| `design/access-service-architecture.md` §9 与 `design/project-rules.md` §1.2 明确归并后的错误码归属（2026-08-12） | T-ACCESS-001、T-ACCESS-011、T-ACCESS-012 | ✅ 全部收口（T-ACCESS-011 已验收；T-ACCESS-012 已收口设计一致性，admin-service-api-contract 错误码措辞对齐 §1.2） | 既有管理域 `1xxxx`、权限域 `2xxxx` 原值保留并继续按领域新增；`access.application` 按对外入口所属领域取码，公共技术失败使用 `9xxxx`；禁止合并枚举、重编号或新增 `4xxxx` 段。T-ACCESS-001 保留领域枚举，T-ACCESS-011 扫描验收，T-ACCESS-012 收口设计一致性 |
| `design/access-service-architecture.md` §7.2/§10 补充授权失效后的陈旧回填防护（2026-08-12） | T-ACCESS-008、T-ACCESS-011、T-ACCESS-012 | ✅ 全部收口（T-ACCESS-008 已实施 2026-08-21；T-ACCESS-011 已验收（容器部分待 CI）；T-ACCESS-012 已收口设计一致性） | 授权 L2 miss 在数据库读取前记录单调时钟起点，回填只能使用从该起点计算的剩余 catalog TTL，预算耗尽不写入，批量/重试不得重置；T-ACCESS-008 扩展受 catalog 上限约束的单次 TTL SPI 并补闩锁竞态测试，实施完成时同步两份缓存 skill，T-ACCESS-011 验收，T-ACCESS-012 收口设计一致性 |
| `design/access-service-architecture.md` §6.1 明确平台用户会话与服务身份认证边界（2026-08-12） | T-ACCESS-003、T-ACCESS-004、T-ACCESS-011、T-ACCESS-012 | ✅ 全部收口（T-ACCESS-003/004 已实施；T-ACCESS-011 已验收（容器部分待 CI）；T-ACCESS-012 已收口设计一致性） | Sa-Token 只承载平台用户会话，固定 2 小时绝对有效期和 30 分钟无操作有效期，Gateway 与 access-service 共享兼容且唯一的 Token/会话配置与键命名空间（T-ACCESS-003 落实：token-name=Authorization、token-prefix=Bearer、token-style=uuid、timeout=7200、active-timeout=1800；会话键 `Authorization:login:*`；两端一致性由部署配置约束，代码不实现跨进程启动校验——设计定案；LoginResp.expiresIn 单一来源=sa-token.timeout——评审 P2；Gateway 配置由 bootstrap.yml 迁移 application.yml + spring.config.import，修复 7 个启动缺陷——评审 P1）；OAuth2 客户端令牌保留客户端自定义有效期，perm-sdk、sync/full-sync 继续使用服务签名或内部凭证。T-ACCESS-004 落实安全矩阵，T-ACCESS-011 验收，T-ACCESS-012 收口设计一致性 |
| `design/access-service-architecture.md` §7.2/§10 将授权陈旧窗口预算由 `15+15` 修订为包含回源时间的 `10+5+15`（2026-08-11） | T-ACCESS-008、T-ACCESS-011、T-ACCESS-012 | ✅ 全部收口（T-ACCESS-008 已实施 2026-08-21；T-ACCESS-011 已验收（容器部分待 CI）；T-ACCESS-012 已收口设计一致性） | T-ACCESS-008 实现并校验授权 L2≤10秒、Gateway 全链路回源截止≤5秒、Gateway L1≤15秒，整个回源流程及重试共享截止时间，超时不写缓存且 fail-closed；T-ACCESS-011 注入接近/超过5秒延迟验证边界；T-ACCESS-012 收口设计一致性 |
| `design/access-service-architecture.md` §7.2 取消 `design/services/gateway.md` 的可切换 fail-mode、open 与 stale-allow（2026-08-11） | T-GW-001、T-GW-003、T-GW-004、T-GW-005、T-PERM-008 | T-ACCESS-008 已实施并回写 gateway.md（2026-08-21） | done 保持历史完成事实、不重开；T-ACCESS-008 已删除 `gateway.permission.fail-mode`、open/stale-allow 实现、stale store 及相关指标和告警，Gateway 权限回源失败固定 fail-closed；保留失效代际、回源并发防护和订阅重连全量清空等仍有效能力。T-GW-002 与 T-PERM-001/006/017 的 fail-closed、快照、广播、本地重评主体语义经扫描仍有效，实施时核对广义 `gateway.md` 引用 |
| `design/services/admin-service-api-contract.md` §3、§4 各写接口同步动作/当前差距及 §6/§7 的 `sys_sync_task` 契约被 `design/access-service-architecture.md` §4 取代（2026-08-10） | T-ACCESS-005、T-ACCESS-011 | ✅ 已收口（T-ACCESS-005 已回写 2026-08-15；T-ACCESS-011 已验收（容器部分待 CI）） | T-ACCESS-005 已改为同事务本地权限投影并退役 `/admin/sync-task/*`；T-ACCESS-011 对其余外部 API 做兼容回归并对退役接口做不存在负向验收。 |
| `design/cross-service/admin-permission-sync.md` 被 `design/access-service-architecture.md` 取代（2026-08-10） | T-PERM-019、T-PERM-021 | ✅ 已重基线（2026-08-22，T-ACCESS-012：T-PERM-019 移除 D4、design_refs 改指 access-service-architecture；T-PERM-021 design_refs 同步替换、F1.e 收窄为外部 sync runbook） | 旧内部同步、SyncHandler/ownership/full-sync runbook 范围不得继续实施（已达成） |
| `design/frontend/permission-grant.md` §16 取代中栏矩阵直接编辑和右栏双 Tab（2026-07-12） | T-FE-014 | 历史基线已核对 | T-FE-014 保持 done，§15 保留其验收与实现记录，不重新打开任务 |
| 同上 | T-FE-018 | ~~已重连，执行前待确认~~ **被 2026-08-01 重设计决策取代** | ~~`depends_on` 已增加 T-FE-027；联调验收必须以 §16 新交互为准，不得回退旧矩阵~~（v3 重设计后 T-FE-018 恢复待排期，实际依赖 = T-FE-036 + T-PERM-034（已 done，2026-08-30）+ T-PERM-022/028/029/031；组织二期 T-FE-037 另依赖 T-ADMIN-021，见 phase3-plan；§16 为 v1 旧章节，已归档） |
| 同上 | T-FE-024 | 已确认 | T-FE-024 先于 T-FE-026；`AdditionalSettingDialog` 组件抽取 + `ChildPermissionDrawer` 内联化归 T-FE-024，授权弹窗编排归 T-FE-026 |
| 同上 | T-FE-025~028 | 已确认 | §16.8 R1~R11 全部已确认；R10 子权限逐项配置、R11 重叠语义已回写；plan 已 active |
| `design/frontend/permission-grant.md` 及三份补充整体归档（2026-07-26） | T-FE-014/018/024~028/029~035 | 已废弃 | 页面交互不满意，v1+v2 两套删除重做；设计文档归档至 `archive/2026-07-26/`，plan 归档；done 任务保持历史事实，未 done 任务 cancelled。详见"前端"段废弃说明 |
| 重设计立项（2026-08-01） | T-FE-036 | 已立项 | 15 项决策评审收敛（根因：查看与授予任务混淆 + 继承关系未体现），新设计回写 `design/frontend/permission-grant.md`（v3）；T-FE-018 恢复待排期（depends_on=T-FE-036, T-PERM-034）；T-PERM-034 范围更新（+grantSource/grantedBits 暴露） |

## 已完成（终态历史摘要，仅作索引）

### user-role-proxy-fix（已归档 2026-06-20）

`T-ADMIN-001~016`（M1-M13 + S1-S3）：用户角色代理修复，全部 ✅ done + ✓ 回写。计划已归档至 [../archive/2026-06-20/](../archive/2026-06-20/)。验收：代码级核验 + 247 tests 0 failures；设计回写：M2 补 api-contract、M13 补 admin-permission-sync §11.1，其余经核对已涵盖。

| ID 区间 | 内容 |
|---|---|
| T-ADMIN-001~004 | M1-M4 DTO 放宽 + 跨字段校验 + 门禁码 ROLE:MANAGE |
| T-ADMIN-005~007 | M5-M7 业务键导向 + 删 parseRoleId + 透传 validFrom/To |
| T-ADMIN-008~010 | M8-M10 UserOrgKeys helper 收敛 relationKey |
| T-ADMIN-011~012 | M11-M12 OrgVisibilityService 可见性裁剪 |
| T-ADMIN-013 | M13 user_role 孤儿延迟补偿 |
| T-ADMIN-014~016 | S1-S3 前端 perm 串 + 种子核实 + 契约测试 |

### user-role-proxy-fix-round2（已归档 2026-06-20）

4 项 P1/P2 修复全部 ✅ done + ✓ 回写。计划归档至 [../archive/2026-06-20/](../archive/2026-06-20/)。测试：247 tests 0 failures。

| ID | 内容 |
|---|---|
| T-ADMIN-017 | P1-1 删除 assign/revoke 重复 ROLE:MANAGE 预检（交 perm 兜底）|
| T-ADMIN-018 | P1-2 getUser 加组织可见性裁剪 |
| T-PERM-016 | P2-1 UserRolesResp 增 relationExternalId + getUserRoles 批量解析 |
| T-ADMIN-019 | P2-2 deleteUser 批量 orgMap 消除 N+1 |

---

## 字段说明

分区表可使用「ID、标题、状态、直接依赖」四列简表，与既有七列表并存；任务链接保留，计划、设计引用及回写状态等详细字段以任务卡为准。

- **设计引用**：任务将改动的 `docs/design/...#章节` 锚点；任务 `done` 前必须回写这些章节。
- **依赖**：`depends_on` 的前置任务 ID；下游任务在前置 `done`/`cancelled` 前不应进 `done`。
- **状态**：`proposed`(⚙️) / `in-progress`(🔨) / `review`(👀) / `done`(✅) / `cancelled`(❌) / `archived`
- **回写**：设计回写状态 `pending`(⏳) / `done`(✓)；`done` 是任务 `done` 的前置条件。

## 新建任务流程

1. 取领域下一编号，计数器 +1，ID 冻结。
2. 总表加行。
3. 复杂任务（多步/独立决策/多验收条目）→ 开 `docs/tasks/<ID>.md` 独立文件，套用任务 frontmatter 模板（见 skill §2.3）。
4. 填 `plan` / `design_refs` / `depends_on`（防循环）/ `acceptance`。
5. 在所属 plan 的 `tasks:[]` 加该 ID，同步计划正文任务清单快照。
6. 若声明了 `depends_on`，检查无循环依赖。
