# 定案入口

## 使用说明

当前规则、有效例外及修订/评审协议以[文档治理](project-rules.md#decision-governance)为准。按任务 design_refs、修改及调用范围、业务概念定位下列主题的相关章节；此入口不存逐项决定，也不要求整读目标文档。新决定当轮归所属规范或任务。

报告前核对当前依据；恢复机制、调整范围、已知取舍或冲突须追溯来源。已修记录不豁免回归，任务 done 不证明例外解除。治理切换另须通过[增量核对门槛](project-rules.md#decision-migration-gate)。

## 主题路由

| 主题/触发场景 | 当前权威位置 |
|---|---|
| 报文、分页、分层、对象、时间、业务键 | [工程规范](project-rules.md) §1–14（分页 §1.3：统一上限 200、超限 400，取代专属上限优先）；[业务键](engine/implementation.md#business-keys) |
| 权限查询、互斥、继承、范围、执行与观测 | [引擎实现](engine/implementation.md#permission-query) §2.4/§3（R2 已收口：§3.11 观测落地与上线门槛；[查询技能](../../.agents/skills/permission-query-pipeline/SKILL.md)）；旧 R2 实施设计（superseded，历史追溯）见 [r2-unified-query-and-admission.md](r2-unified-query-and-admission.md#r2-migration) 与 [归档计划](../archive/2026-10-01/r2-query-engine-and-admission-plan.md) |
| 类型/资源所有权、授权根、条件生命周期、业务域分类 | [类型契约](access-service-api-contract.md#type-lifecycle)、[条件契约](access-service-api-contract.md#condition-lifecycle)、[授权契约](access-service-api-contract.md#grant-contract)、[域分类契约](access-service-api-contract.md#domain-classify)、[引擎域分类](engine/implementation.md#domain-classify) |
| 依赖发布、自动授权、预览、来源、对账 | [自动授权](dependency-auto-grant.md#architecture) §3–8/11–13 |
| 主体投影、树锁、默认组织、岗位、目录准入 | [服务架构](access-service-architecture.md#tree-write-lock) §4/12/17；[组织生命周期](default-org-tree-user-lifecycle.md#default-tree)；[目录准入](access-service-api-contract.md#resource-directory) |
| 服务身份、接口准入、网关、部署 | [服务认证](service-authentication.md) §2–3；[准入协议契约](access-service-api-contract.md#operation-admission-protocol) §25（T-ACCESS-056 落账：错误码 20070/20071、reason 词表、迁移门槛）；[Gateway](services/gateway.md)对应信任头/白名单/CORS章节 |
| 前端会话、菜单、路由、加载上下文 | [会话设计](frontend/login.md#session-permissions)、[路由门禁](frontend/login.md#route-gate)、[列表约束](../../.claude/rules/frontend-coding-standards.md#list-context)；页面设计见[前端索引](frontend/README.md) |
| 租户开通、独立平台身份与运营边界 | [平台与租户边界](services/gateway.md#platform-tenant-boundary)；[租户生命周期](tenant-lifecycle.md)；[运营契约](access-service-api-contract.md#contract-section-26)；[部署与恢复](../ops/tenant-operations.md) |
| 缓存、失效、TTL、滚动别名 | [缓存技能](../../.agents/skills/dual-layer-cache-framework/SKILL.md)；[架构读取边界](access-service-architecture.md#cache-boundaries) |
| 测试轨道、容器隔离、时序 | [测试规范](../../.claude/rules/testing-standards.md#test-tracks) §10；[运行命令](../../AGENTS.md#常用命令开发阶段预估) |
| 任务/计划/问题、收口、本地及外评 | [生命周期技能](../../.agents/skills/design-plan-task-lifecycle/SKILL.md)、[本地评审](../../.agents/skills/dual-track-local-review/SKILL.md)、[外评](../../.agents/skills/external-review/SKILL.md)；治理正文见上方使用说明 |

## 历史追溯

[2026-09-26迁移批次](../archive/2026-09-26/README.md)保存迁移前[现行册](../archive/2026-09-26/decision-registry-before.md)、[历史册](../archive/2026-09-26/decision-registry-history.md)、[压缩前原文](../archive/2026-09-26/decision-registry-precompression.md)及一次性语义对照。实施基线 `f8dee10f9`，压缩前 `c6270d034`；历史协议已被当前治理替代，不能作为当前消费指令。后续历史优先沿当前规则来源或 `git log/show` 定位，必要时读取相关归档上下文。
