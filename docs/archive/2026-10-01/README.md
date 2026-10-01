# 2026-10-01 归档批次：R2 权限查询引擎统一与操作准入

**内容**：[r2-query-engine-and-admission-plan](r2-query-engine-and-admission-plan.md)（2026-09-25 立项 → 2026-10-01 收口归档，`status: archived`）+ 24 张任务卡（`tasks/`：T-PERM-080~095 R2 系列〔含 095 基线补卡〕、T-ACCESS-056~062 ADM 系列、归入卡 T-PERM-054）。

**收官卡 T-PERM-094**（灰度、故障、缓存与发布演练）四项拍板：监控「计数＋执行时长＋超限细分」档 / 暴露面同 gateway 先例（actuator+prometheus 独立管理端口 9101 回环默认）/ 性能预算实测基线登记（不承诺生产 SLA）/ 随卡全收口。完成记录含灰度差异五类归类、上线门槛八项证据、失效链路演练四载体、dev 冒烟指标实测。

**稳定结论去向**（归档条件兑现，2026-09-25 拍板）：

- 查询执行/迁移/观测 → `design/engine/implementation.md`（§3 族终态 + §3.11 观测落地与上线门槛）
- 准入协议（方案 A）→ `design/access-service-api-contract.md` §25、`design/services/gateway.md`
- `design/r2-unified-query-and-admission.md` 转 **superseded**（保留原位，交叉链接可解析；`superseded_by` 指向引擎实现）

**连带更新**：看板 R2 节（链接改指本目录）、`plans/README`、`docs/README`、`AGENTS.md` 权限面规范定位行、`decision-registry` 主题路由、`pending-problems` Q-045 关联锚。

**已知边界**：本批次含一处 e2e 既有夹具缺陷修复（`ExampleBusinessFinalCheckE2EIT.step8` 父行定位 `LIMIT 1` 无序+JOIN 未按位过滤——执行计划变化选中无 VIEW 位行致 depend_on 指错父；修法=JOIN 补位过滤，HEAD 基线复跑同红定性非 094 回归，修复后 11/11 绿）。
