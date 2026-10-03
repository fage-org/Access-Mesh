---
doc_type: task
id: T-PERM-104
title: 用户角色分配/撤销入口角色定位键碰撞收口（入口 @Pattern + 键元组化）
status: done
plan: docs/plans/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md §10.4（user-role 管理与同步端点：条目字段格式约束与空串拒语义）
depends_on: []
blocks: []
acceptance:
  - "assign/revoke 条目（UserAssignRoleReq.AssignItem / UserRoleBatchRevokeReq.RevokeItem）的 subjectTypeCode/roleTypeCode/domainCode 补 @Pattern（^[A-Z][A-Z0-9_]*$，与建域/建类型入口同款；domainCode 可 null 放行、空串 400 对齐 T-API-004 空串拒先例）；batch-assign 请求级同名字段一致性对齐"
  - "UserManageAppServiceImpl 分组键 roleTypeDomainKey 与回读键 roleKey/subjectKey 改 record 元组键（T-PERM-096 ResourceTripleKey 先例），split(\":\") 反解删除；BusinessKeyUtil 三键随批退役（含 parity 锁同步）"
  - "回归锁：碰撞对用例（domainCode 含冒号滑移构造）旧实现下静默错配红、新实现显式拒绝；@Pattern 畸形域码旧实现放行红、新实现 400；PermCommonReqContractTest 快照同步"
  - "契约 §10.4 落账：条目字段格式约束、domainCode 空串改拒 400 的语义变化、内部键元组化注记"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-03
---

# T-PERM-104 用户角色分配/撤销入口角色定位键碰撞收口

## 背景

承接 [Q-057](../pending-problems.md#q-057)：`assignRole`/`revokeRolesBatch` 以 `roleTypeDomainKey`（roleTypeCode:domainCode）拼串分组后 `split(":")` 反解参数、`roleKey`（三段拼串）回读。domainCode 含 `:` 时分组反解静默改写请求参数（域不存在检查被滑移后参数绕过）、回读键滑移撞键——同批可构造 `(BASIC_ROLE,"finance","admin:x")` 与 `(BASIC_ROLE,"finance:admin","x")` 同键，后者静默取到前者解析出的 roleId，授权结果与请求不符且无报错；操作日志按请求原文记录，事后审计不可发现。roleExternalId 含 `:` 为可达数据形态（RoleCreateReq.externalId 无格式约束）。受 ROLE:MANAGE 门禁约束（对实际命中角色），定性 P2 数据正确性/审计可信度。

核实修正登记口径两处：subjectKey 侧无静默碰撞（subjectTypeCode 含冒号类型解析必 miss → 显式报错）；batch-assign 链路无暴露（请求级单值直传无拼接反解）——暴露面收敛为 assignRole 与 revokeRolesBatch 两条链路的 roleKey/roleTypeDomainKey 侧。

## 范围

perm-common 两 DTO（UserAssignRoleReq/UserRoleBatchRevokeReq）条目三标识码字段补 @Pattern；access-service 本地 UserRoleBatchAssignReq 请求级同名字段一致性对齐；UserManageAppServiceImpl 三链路内部键 record 化；BusinessKeyUtil.subjectKey/roleKey/roleTypeDomainKey 退役；契约 §10.4 落账。

## 当前口径

2026-10-03 用户拍板修法 C（双管齐下）：入口 @Pattern（畸形值进门即 400，报错清晰指向格式）+ 内部键元组化（结构根除拼接碰撞，不依赖入口校验纪律）。subjectKey 虽无实际碰撞仍随批元组化（三键同款退役，消除拼串键类）。subjectExternalId/roleExternalId 保持自由文本（schema/建入口均无约束，位于尾段无滑移面）。domainCode 空串从「视为 null 分组」改拒 400（对齐 T-API-004 空串拒先例，契约落账语义变化）。check 族（AuthCheckReq 等）只读判定面碰撞无危害（解析 miss 即 deny），不扩散。

## 非目标 / 遗留

- RoleCreateReq.externalId 格式约束（自由文本为既有语义；本卡 @Pattern 锁定类型码/域码后，尾段 externalId 含冒号无滑移面）。
- check 族/查询面 subjectTypeCode 的 @Pattern（只读面无错配危害）。
- Q-056 apiRouteResourceKey 同型碰撞（已登记，随清单批次另行排期）。

## 完成记录

2026-10-03 收口。实现终态：

- 入口层：`UserAssignRoleReq.AssignItem` / `UserRoleBatchRevokeReq.RevokeItem`（perm-common 单源）与 `UserRoleBatchAssignReq`（请求级字段）的 `subjectTypeCode`/`roleTypeCode`/`domainCode` 补 `@Pattern("^[A-Z][A-Z0-9_]*$")`，message 与建域/建类型入口同款；`domainCode` null 放行（功能角色全局域）、空串 400（T-API-004 先例）；externalId 保持自由文本。
- 服务层：`UserManageAppServiceImpl` 新增类内 `RoleTypeDomainKey`/`RoleKey`/`SubjectKey` 三 record，assignRole/assignRolesBatch/revokeRolesBatch 三链路的分组与回读全部改元组键，`split(":")` 反解删除；`BusinessKeyUtil.subjectKey/roleKey/roleTypeDomainKey` 退役（全仓消费方清零，parity 锁三组用例随删并补退役注记）。
- 回归锁：`UserManageAppServiceImplTest` 碰撞对 2 用例（A=(BASIC_ROLE,FIN,admin:x) 与 B=(BASIC_ROLE,FIN:admin,x) 构造滑移；旧实现红形态=「期望 BizException 但未抛」即静默错配落库/删错实证）+ `UserRoleRelationIdValidationTest` @Pattern 3 用例；`PermCommonReqContractTest` 快照同步。红跑双证：stash 回退五实现文件后旧实现下 5 用例红（2026-10-03，surefire 报告）；恢复后定向 25+6+2、perm-common 18 全绿。
- 兼容面核实（双轨评审代码轨实证通过项）：前端 assignRole/revokeRole 封装不传 domainCode（省略→null）、subjectTypeCode 字面量 LOCAL_USER；e2e 三 E2EIT 全 `putNull("domainCode")`；SDK feign 透传 DTO 签名未动；`strict-domain-check=false` 灰度分支无空串兜底路径；batch-assign 无内部调用方。缺陷 P0-P3=0、过度设计可裁剪项=0。
- 契约回写：§10.4 新增「标识码格式锁」段（碰撞背景/双层修复/空串语义变化/SDK 单源同步）+ §10 前置说明的跨字段校验段补「为空=显式 null，空串 400」句。
- 全量回归：`mvn test -T 1C`（含 E2E/heavy）BUILD SUCCESS，9 fork 汇总合计 2448 项 0 失败 0 错误（与类级明细总数一致，2026-10-03，surefire 报告为准）。
