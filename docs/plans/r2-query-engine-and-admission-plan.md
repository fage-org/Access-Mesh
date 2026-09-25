---
doc_type: plan
title: R2 权限查询引擎统一与操作准入（方案 A）
status: proposed
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md
tasks:
  - T-PERM-080
  - T-PERM-081
  - T-PERM-082
  - T-PERM-083
  - T-PERM-084
  - T-PERM-085
  - T-PERM-086
  - T-PERM-087
  - T-PERM-088
  - T-PERM-089
  - T-PERM-090
  - T-PERM-091
  - T-PERM-092
  - T-PERM-093
  - T-PERM-094
  - T-ACCESS-056
  - T-ACCESS-057
  - T-ACCESS-058
  - T-ACCESS-059
  - T-ACCESS-060
  - T-ACCESS-061
  - T-ACCESS-062
  - T-PERM-054
acceptance: "两个完成条件各自闭合：①T-PERM-092（旧执行体与四旧 DTO 退出）可在仍有 LEGACY_API 服务时完成——legacy 语义经新 execute 表达；②T-ACCESS-062（全服务迁完、API 独立授权与 legacy 协议退役）。设计 §11 最终完成定义逐条有对应项目测试与运行证据"
last_updated: 2026-09-25
---

# R2 权限查询引擎统一与操作准入（方案 A）

> 设计依据：[r2-unified-query-and-admission.md](../design/r2-unified-query-and-admission.md)（v3.1，adopted，2026-09-25 定稿——三项拍板〔时区不处理 / 角色互斥 S/H/D / configGeneration 限定语义〕见 decision-registry 同日行）。
> 立项说明：统一设计稿的报告临时编号（R2-T01~15 / ADM-T01~07）按看板计数器转为正式任务 ID，映射见下表；T-PERM-054 解除暂缓归入本计划。

## 目标

- 唯一权限查询执行主体（一个 execute：规范化 → 共享装载 → 分集合评估），真实消费者全部迁移，旧执行体与四个旧引擎 DTO 退出（设计 §1.1）。
- T-PERM-054 方案 A：接口检查操作准入（OPERATION_ADMISSION），业务服务检查具体实例；API 不再独立授权（设计 §7/§8）。
- 顺带修复已代码级核实的缺陷：PQ-01（getDenied* 整批互斥跨 item 过拒）、PQ-02/03（辅助装载逐类型/逐 item 扫描）、PQ-05（空规则仍装载操作、审计按冲突端点反推规则）、PQ-06（角色互斥顺序依赖 → S/H/D 定案）。

## 非目标

- 不重建 role_resource_permission、不改变 scopeAll/操作位/MANUAL/AUTO_DEP 真值、不改 depend_on 语义、查询时不写授权、不建通用策略编排平台（设计 §1.3）。
- 普通外部 HTTP/SDK 契约（auth/check、batch-check、query-scopes 等）默认不变；准入与同步协议独立版本化。
- IMP 写侧解耦与继承触发依赖不在本计划（设计稿前言：不因本版重开或自动实施）。

## 准入条件

- 设计已定稿（registry 2026-09-25 行）；T-PERM-081 语义基线先行（红跑取证）再动核心实现。
- 核心任务收口跑全量回归（含 E2E/heavy，测试运行纪律见 AGENTS.md）。

## 任务清单

### R2 系列（引擎统一，T-PERM-080~094）

| ID | 标题（报告编号） | 状态 |
|---|---|---|
| [T-PERM-080](../tasks/T-PERM-080.md) | 全仓调用与语义清点（R2-T01） | ⚙️ |
| [T-PERM-081](../tasks/T-PERM-081.md) | PQ-01/06 反例与正常语义基线（R2-T02） | ⚙️ |
| [T-PERM-082](../tasks/T-PERM-082.md) | 新请求/结果模型与合法组合（R2-T03） | ⚙️ |
| [T-PERM-083](../tasks/T-PERM-083.md) | 角色互斥 S/H/D 确定化与纯互斥计算（R2-T04） | ⚙️ |
| [T-PERM-084](../tasks/T-PERM-084.md) | QueryReadSupport 与读来源分桶（R2-T05） | ⚙️ |
| [T-PERM-085](../tasks/T-PERM-085.md) | TYPE_GRANT/INSTANCE 单一阶段主体（R2-T06） | ⚙️ |
| [T-PERM-086](../tasks/T-PERM-086.md) | 父受控子项与 GRANT_LIST 完整事实（R2-T07） | ⚙️ |
| [T-PERM-087](../tasks/T-PERM-087.md) | 投影、展示与范围四态（R2-T08） | ⚙️ |
| [T-PERM-088](../tasks/T-PERM-088.md) | 根审计、TRACE 与故障证据（R2-T09） | ⚙️ |
| [T-PERM-089](../tasks/T-PERM-089.md) | 迁移 check/batch/管理门禁/getDenied（R2-T10） | ⚙️ |
| [T-PERM-090](../tasks/T-PERM-090.md) | 迁移范围与 LEGACY_API 接口集合（R2-T11） | ⚙️ |
| [T-PERM-091](../tasks/T-PERM-091.md) | 迁移旧快照、转授、视图与配置（R2-T12） | ⚙️ |
| [T-PERM-092](../tasks/T-PERM-092.md) | 删除旧执行体与四旧 DTO（R2-T13） | ⚙️ |
| [T-PERM-093](../tasks/T-PERM-093.md) | 候选/规则索引与性能测量（R2-T14） | ⚙️ |
| [T-PERM-094](../tasks/T-PERM-094.md) | 灰度、故障、缓存与发布演练（R2-T15） | ⚙️ |

### ADM 系列（操作准入方案 A，T-ACCESS-056~062）

| ID | 标题（报告编号） | 状态 |
|---|---|---|
| [T-ACCESS-056](../tasks/T-ACCESS-056.md) | 准入定案回写与协议落账（ADM-T01） | ⚙️ |
| [T-ACCESS-057](../tasks/T-ACCESS-057.md) | OPERATION_ADMISSION 阶段与新结果（ADM-T02） | ⚙️ |
| [T-ACCESS-058](../tasks/T-ACCESS-058.md) | 映射模型、服务模式与同步/管理面（ADM-T03） | ⚙️ |
| [T-ACCESS-059](../tasks/T-ACCESS-059.md) | 新端点、快照与 SDK/网关链路（ADM-T04） | ⚙️ |
| [T-ACCESS-060](../tasks/T-ACCESS-060.md) | 失效、TTL 边界与在途代次（ADM-T05） | ⚙️ |
| [T-ACCESS-061](../tasks/T-ACCESS-061.md) | 逐服务业务最终检查与模式切换（ADM-T06） | ⚙️ |
| [T-ACCESS-062](../tasks/T-ACCESS-062.md) | API 独立授权与 legacy 协议退役（ADM-T07） | ⚙️ |

### 归入卡

| ID | 标题 | 状态 |
|---|---|---|
| [T-PERM-054](../tasks/T-PERM-054.md) | 手工 API 映射绑定非 API 资源处置——方案 A 落地收口（2026-09-25 解除暂缓归入） | ⚙️ |

## 归档条件

全部任务 done/cancelled；稳定结论沉淀 `docs/design/`：R2 部分并入 `engine/implementation.md` 相应章节（T-PERM-092 完成时取代其被替代章节），准入部分沉淀契约总册新章与 `services/gateway.md`。

## 当前进度

2026-09-25 立项，全部 proposed，未开工。依赖主线：T-PERM-080 → 081/082 → 083/084 → 085 → 086/088 → 087/089 → 090 → 091 → 092/093 → 094；ADM 支线：T-ACCESS-056 → 057/058 → 059 → 060 → 061 → 062；T-PERM-054 收口于 T-ACCESS-058/061 之后。
