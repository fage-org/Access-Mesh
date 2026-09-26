---
doc_type: task
id: T-PERM-087
title: （R2-T08）投影、展示与范围四态
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §3.2/§3.3/§6.2/§6.4
depends_on:
  - T-PERM-086
blocks: []
acceptance:
  - "QueryProjector：GrantFact 与 PresentationEntry 分离——展示继承/操作覆盖展开不改 GrantFact.resourceEntityId、不将展示 INHERITED 写成真实 grantSource；hasCondition 与 conditionId 不一致时诊断而非降级为无条件"
  - "范围四态 G02~G05：raw 有覆盖 retained 无→EMPTY；无覆盖/目标 op 未知→DENIED；retained 含 scopeAll→ALL（不展开全量实例）；原有实例全部失效→EMPTY，而非空 INSTANCE"
  - "A05 执行覆盖部分：scopeAll 短路保持 INSTANCE=SKIPPED 且投影不补查；TRACE 输出与门禁整体归 T-PERM-088，本卡 trace=true 仍明确拒绝；OutputSpec 不能关闭判定必需计算；extraOperationKeys 不扩大 Selection"
  - "接入 T-PERM-084 读取部件的输出入口，在整体投影链验证 I02/I03/I06；extraOperationKeys 采用 Set<TypeOperation>，无授权时仍可补全目标定义"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-26
---

# T-PERM-087 （R2-T08）投影、展示与范围四态

## 背景

设计 §3.2/§3.3 真值/阶段事实/展示三分与 §6.2 范围四态（报告临时编号 R2-T08）。ScopeCoverageProjector 只消费结果和已装载定义，不再查授权/条件/规则。

## 范围

- rawAfterContext 定义（上下文绑定处理后、条件/互斥前）；Details 用 loadedSections 区分「没请求」与「请求后为空」；内部父 matchedPermissionIds 即使不展示也计算。
- 展示父子展开/有效操作投影与现行 expandByPresentMode/EffectiveOperationEntry 语义对齐（判定后克隆不改判定）。

## 非目标 / 遗留

- TRACE 输出及敏感字段门禁（IP 规则/角色 ID 仅受权诊断）随 T-PERM-088 审计卡收口；本卡只验执行覆盖与不补查，见设计 §3.3（2026-09-26 用户确认）。
- queryScopes 等生产适配和新引擎 Bean 接线由 T-PERM-089～091 承接，本卡不迁移旧执行体消费者。

## 当前口径

- 展示方向为 NONE/PARENTS/CHILDREN/BOTH；PresentationEntry 与有效操作条目引用源权限/角色，原 GrantFact 的资源、条件、父绑定与 grantSource 保持不变。
- 全部阶段评估完成后合批装载描述与新鲜操作定义；raw 超集保留筛空项的操作说明，extraOperationKeys 只参与输出。描述使用不可变记录，输出不返回 ORM/缓存对象；loadedSections 区分未请求与已请求为空。
- ScopeCoverageProjector 为纯结果投影，消费完整 raw/kept、资源描述与额外操作定义；类型/操作未知或 raw 无覆盖为 DENIED，有覆盖但清空/资源失效为 EMPTY，有类型级覆盖为 ALL，其他以 (codeType, code) 去重返回非空 INSTANCE。
- 条件引用不一致时保留诊断；PRESERVE 原样保留事实，EVALUATE 失败关闭。TRACE 不提前开放；类型级充分判定后的投影不补跑 INSTANCE。

## 验收对照

- 事实/展示隔离：四方向参数化验证源字段保持、不同条件/父绑定来源不合并、有效操作覆盖；真实 PG 树展开与 Redis 快照验证无展示克隆回填。
- G02～G05：条件筛空 EMPTY、无覆盖/未知类型与操作 DENIED、scopeAll 优先 ALL 且零全量树展开、实例软删除 EMPTY；资源元组去重及不可变描述验证。
- A05 执行覆盖：scopeAll 充分判定在输出描述后仍记录 INSTANCE=SUFFICIENT_DECISION，实例授权 SQL 零调用；TRACE 未实现请求明确拒绝，输出不重评条件。
- I02/I03/I06：20 类型整体 execute 单次多类型装载、最小输出不读辅助定义/描述、缓存操作覆盖与新鲜输出定义分桶；NO_ROLE/无目标授权仍可按输出要求补全 extraOperationKeys。

## 完成记录

- 2026-09-26：事实/展示分离、四态展示方向、有效操作与不可变描述、范围四态纯投影已实现；新执行器仍不注册 Bean、无生产消费者。两项用户决定已归设计 §3.3，TRACE 输出与门禁的后续责任已同步 T-PERM-088。
- 反例验证：初始两项描述输出用例在原实现下均以未实现异常失败；实现后定向单测 141 项通过。真实 PG/Redis 定向 15 项中，软删除用例改用生产 DomainService/Mapper 写通道后隔离复跑通过；原 JdbcTemplate 旁路更新不能使同事务 MyBatis 一级缓存失效，生产实现未因此改动。全部用例由下述全量统一覆盖。
- 收口回归：2026-09-26 18:14，`mvn test -T 1C`，11 模块 BUILD SUCCESS；共 2212 项，零失败/错误/跳过，其中 access-service 单测 1550、容器 384（含 heavy）、E2E 16。完整输出落盘后按各 execution 聚合汇总，耗时 16:33。
- 代码轨实证核对通过：源授权/展示隔离、方向独立、raw 操作定义保留、条件损坏诊断与失败关闭、额外操作不扩大选择、无角色输出、批量读取与同源记忆、缓存载荷不污染、技术异常传播及只读投影；无未解决缺陷、待决策项或过度设计可裁剪项。
- 文档轨实证核对通过：设计 §3.2/§3.3/§6.2/§6.4、迁移期边界、087/088 验收归属、任务/计划/看板状态及相对链接一致；元数据与注释已同步，无未解决问题。所属计划仍活跃，本卡保留原位。
