---
doc_type: plan
title: 权限查询统一引擎重构
status: proposed
domain: permission-center
design_refs:
  - docs/design/permission-center/query-engine-unification.md
  - docs/design/permission-center/implementation.md
tasks:
  - T-PERM-057
  - T-API-003
  - T-PERM-058
  - T-PERM-059
acceptance: "T-PERM-057/T-API-003/T-PERM-058/T-PERM-059 全部 done 或 cancelled；全仓权限查询无引擎外独立管线（canGrant 直查/query-resources AppService 展开/query-scopes AppService 自评管线/getDenied* 手写管线收编清零）；implementation §3 重写为统一引擎版，T-PERM-058/059 design_refs 已重连 implementation §3，query-engine-unification.md 转 superseded"
last_updated: 2026-09-09
---

# 权限查询统一引擎重构

> 状态：proposed（2026-09-09 grill 定案立项；定案正文见 [query-engine-unification.md](../design/permission-center/query-engine-unification.md)，本计划只做编排）
> 关联：T-PERM-045 已取消（范围并入 T-PERM-057）；T-PERM-054 维持暂缓（方向已定、方案未定）。

## 目标

- 权限查询收敛为**一个引擎、一套入参、一个结果模型**，消除六套执行形态分叉（query() 六工厂 / getDenied\* 手写管线 / query-resources AppService 展开 / deleteRoles 局部规则 / canGrant 直查管线 / query-scopes AppService 自评管线）。
- 判定面继承（目标闭包「父授权覆盖子」）在引擎层落地并按默认值矩阵启用；目标模式三态判别（TYPE_LEVEL/INSTANCE/LIST）消除「无实例目标」二义。
- 管理面门禁条件评估拉平；冲突过滤入参化；两级互斥过滤点统一归属。

## 非目标

- API 授权模式改造（接口权限由操作权限关联派生）——方向已定（T-PERM-054），方案与实施后置。
- depend_on 单点闭合的具体语义——独立设计任务 T-PERM-058。
- 权限视图/排查的新设计——T-PERM-059 只定删除边界与重设计范围。
- 快照架构改造（保留）、scopeMode 四态、query-scopes 契约形态（维持）。

## 准入条件

- 已满足：2026-09-09 定案登记（decision-registry 五行）+ 终态设计落盘（query-engine-unification.md）。

## 任务清单

| ID | 标题 | 状态快照 |
|---|---|---|
| [T-PERM-057](../tasks/T-PERM-057.md) | 权限查询统一引擎重构（收编六套形态 + 目标模式三态 + 判定面继承 + 评估拉平） | ⚙️ proposed |
| [T-API-003](../tasks/T-API-003.md) | check 族三端点结果记录全量回传（推翻 T-API-002 check 族裁剪） | ⚙️ proposed |
| [T-PERM-058](../tasks/T-PERM-058.md) | depend_on 子权限单点门禁闭合设计 | ⚙️ proposed |
| [T-PERM-059](../tasks/T-PERM-059.md) | 权限视图/排查删除重设计（范围待定） | ⚙️ proposed |

> 顺序建议：T-PERM-057 → T-API-003（非硬依赖，但先统一引擎可避免 check 管线二次触碰）→ T-PERM-058（依赖 057）→ T-PERM-059（独立，可并行）。

## 归档条件

四个任务全部 done/cancelled；implementation §3 重写为统一引擎版；T-PERM-058/059 的 design_refs 重连 implementation §3 后，query-engine-unification.md 转 superseded；关联 skill 双副本与 rule 同步完成。

## 当前进度

- 2026-09-09：grill 定案（Q1-Q15）+ D1/D2 补充定案；现状断言经代码级核验修订后落盘设计（目标模式三态化、codeType 归位目标三元组、query-scopes 自评管线补计为第六套形态、evolution superseded 时点定于计划收口）。
