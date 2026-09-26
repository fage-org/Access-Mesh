---
doc_type: task
id: T-PERM-080
title: （R2-T01）全仓调用与语义清点
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §6.5/§9.1
depends_on: []
blocks:
  - T-PERM-081
  - T-PERM-082
acceptance:
  - "全部旧执行体消费点（query/queryBatch、四便捷入口、computeInstanceDenied/passesScopeAll 的直接调用、方法引用、反射、缓存序列化、测试夹具、文档引用）清点成册，区分「旧执行体消费点」与「LEGACY_API 业务模式消费点」两类"
  - "每个实际调用点有唯一迁移目标（新 Selection/ResultForm 组合，或显式退役）"
  - "外部 batch 上限与内部 getDenied 容量分别盘点落账；清点结论回写设计 §6.5 迁移矩阵增补"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-25
---

# T-PERM-080 （R2-T01）全仓调用与语义清点

## 背景

统一设计稿 §6.5 消费者迁移矩阵只列主要消费者，不代替完整引用清单（设计 §6.5 末句）；实施前必须先成册。报告临时编号 R2-T01（映射见计划卡）。

## 范围

- rg 全仓盘点旧执行体符号（PermQuery/PermBatchQuery/PermResult/PermBatchResult、query/queryBatch、hasPermissionByCode/getDeniedResourceCodes/hasPermissionByEntityId/getDeniedEntityIds、computeInstanceDenied/passesScopeAll），含 mock 层、测试夹具、文档引用（`-g '!docs/archive/**'`）；清点维度六面：调用/语义/输出形态/事务边界/缓存序列化/协议（设计 §9.2 R2-T01 口径）。
- 按消费语义分两类登记：旧执行体消费点（迁新 execute）与 LEGACY_API 业务模式消费点（保持共同集合语义迁入新 execute）。
- 盘点外部 batch 上限与内部 getDenied 容量（分属不同约束面）。

## 非目标 / 遗留

- 不改任何实现代码；本卡是清点与迁移目标表产出。

## 完成记录（2026-09-25）

- 清点册落盘：计划文件附录 A（A.0 口径与零命中声明 / A.1~A.6 生产消费点与迁移目标 / A.7 测试夹具分组 / A.8 活文档与指令面清扫面 / A.9 容量盘点）；清点基线 HEAD `320d16a87`，方法=rg 程序化生成全量命中+逐行人工核实调用形态。
- 设计 §6.5 回写：矩阵后增补块（清点册指针+四消费面勘误——「资源依赖」「权限树 ID 轨」两面不存在）+矩阵 getDenied 行行内勘误指针。
- 双轨评审处置：代码轨 P1×2（A.2 漏登 ConditionAppServiceImpl:103/188+计数错；「matched 截断 30」误读实为 cacheTtlSeconds=30s 缓存有效期且 matched 全量回传）+P2×3（Operation ×5、A.3 括号注解、A.1#4 工厂方法 forUserView）+P3×5，文档轨 P1×1（同代码轨）+P2×2+P3×4，全部核实成立并直修；service-interface-mapping.md:127 补入 A.8。
- claude 外评处置（同日第二轮，模型=本机默认 deepseek-flash[1m]）：P0-P1=0、P2×1（A.0/A.8 清扫面漏登指令面与注释面——首轮 rg 漏扫 `.claude`/`.agents` 目录；A.8 增列 rules/skills 指令面+T-PERM-036 活跃卡、A.0 补 Java Javadoc/前端注释站点并统一处置口径）+P3×2（去计数化残留清理；A.1#5 queryScopes 补 EVALUATE＋ENFORCE 对齐设计 §6.2）全核实成立直修；处置定案见 [历史定案原文](../archive/2026-09-26/decision-registry-before.md) 同日行。
- 三项收口拍板与 AGENTS.md perm-entity 事实修正见 [历史定案原文](../archive/2026-09-26/decision-registry-before.md) 2026-09-25 行；零代码变更（纯文档任务，无测试面）。
