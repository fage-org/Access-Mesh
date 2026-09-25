---
doc_type: task
id: T-ACCESS-057
title: （ADM-T02）OPERATION_ADMISSION 阶段与新结果
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §5.3/§7.2/§7.3/§7.4
depends_on:
  - T-PERM-083
  - T-PERM-084
  - T-PERM-085
  - T-PERM-086
  - T-ACCESS-056
blocks: []
acceptance:
  - "ADMISSION_CANDIDATES 阶段：从已匹配路由要求取 type-operation→新鲜完整操作定义计算精确覆盖掩码（要求未知/损坏=接口层配置错误，不回退任意操作）；同批要求合并 type-mask 一次/分块读取 ALL+实例候选（新 selectAdmissionCandidatesByTypeMasks，不重载空 entityIds 含义）；子候选集中批量父结构核查（非法结构排除+诊断、CONTEXT_DEFERRED 标注、不评父条件不伪造父 matchedPermissionIds）"
  - "在线 ADMISSION 存在性短路仅因本用途无 PERM_MUTEX 才允许；拒绝项穷尽候选；FACTS 不按当前环境删条件分支（完整收集范围/条件身份/候选类别）；AdmissionResult 恒 finalCheckRequired=true；准入与普通目标/GRANT_LIST 混批按首版限制拒绝"
  - "N02/N03/N06~N10/N13 全绿（N12 本地投影×在线一致性归 T-ACCESS-059 端到端验收，不设本卡门槛）；N20：准入构建与在线只读新鲜操作定义目录/已批准安全目录，不消费旧 GRANT_LIST 评估结果与长 TTL 掩码缓存残留投影；来源语义（MANUAL/AUTO_DEP/AUTHORITY_ROOT 按真实覆盖参与、无来源特权、无条件与失败条件 OR 分支独立保留）锁定"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-25
---

# T-ACCESS-057 （ADM-T02）OPERATION_ADMISSION 阶段与新结果

## 背景

设计 §7.2~§7.4（报告临时编号 ADM-T02）。准入=「存在结构有效的覆盖候选且本行条件通过」，不是「存在已完整允许的实例」；不做跨实例/同实例最终 PERM_MUTEX 判定（N04/N05 反例锁死）。

## 范围

- 新阶段接入唯一 execute（复用原始事实结构、角色与条件能力）；审计事件独立标注 OPERATION_ADMISSION、不写「业务互斥已通过」（复用 T-PERM-088 证据结构）。
- 首版完整批量读候选再逻辑短路；流式 EXISTS 优化后置（须保留覆盖/结构/条件回源语义）。

## 非目标 / 遗留

- 端点/快照/网关在 T-ACCESS-059；本地可下发条件分支的快照装配同在该卡。
