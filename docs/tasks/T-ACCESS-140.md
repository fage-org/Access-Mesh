---
doc_type: task
id: T-ACCESS-140
title: 动态策略配置与真实 SQL 权限过滤
status: proposed
plan: docs/plans/example-permission-demo-plan.md
domain: cross-service
design_refs:
  - docs/design/example-permission-demo.md#field-permissions
  - docs/design/example-permission-demo.md#dynamic-policy
  - docs/design/example-permission-demo.md#operations
depends_on:
  - T-ACCESS-139
acceptance:
  - "提交完整配置一次校验并按版本原子生效；无独立草稿生命周期，失败不改变旧版，历史内容恢复生成新版本。"
  - "共享行列执行器可在导入之前用 PG 夹具验收；SQL 数据集过滤、明确投影和写列约束使用同一固定版本。"
  - "行谓词与字段 SELECT/写列同时限制，报表同名字段不串权；隐藏列筛选/排序拒绝，无字段权不兜底全部列。"
  - "本人/团队/部门由配置实现；新增已有 regionCode 的范围只改配置/授权，Java 与部署不变。"
  - "无权限修改策略、非法字段/值/谓词、未知范围绑定均拒绝；失败发布不替换旧版，查询固定版本。"
  - "权限谓词覆盖列表/详情/total/写入；DATA_READ 不替代 DATA_EDIT，混合批量无权整批无副作用。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-18
---

## 背景

本人、团队、部门应由运行时配置表达，业务代码只实现统一的安全 SQL 解释器。

## 范围

字段目录、操作绑定、范围谓词、完整配置校验/版本 CAS/原子生效/审计；列表、详情、批量查看和单条/批量备注修改共同消费配置。不提供独立草稿存储、保存草稿或分步发布；校验失败和并发版本冲突均保留旧版本。

实现报表上下文的字段范围查询、授权字段目录、SQL 明确列投影及编辑列校验；与行范围共同消费四态。

本卡先于 CSV 导入交付共享行列授权执行器，导入复用它而不复制字段门禁。定向 PG 验证用测试夹具准备记录，不依赖生产导入 API；实现就绪后由 T-ACCESS-138 完成可重复导入接线。

为导入、在线读写和异步快照提供同一授权结果/行谓词/列投影入口，不分散复制规则。

## 当前口径

实现只使用有限通用字段/谓词/值来源，不按角色名或业务维度硬编码。租户/有效状态不可配置关闭，SQL 参数化。仅在本计划及 T-PERM-036 实施准入明确后启动。 详细规则见[权限演示设计](../design/example-permission-demo.md)；计划为 proposed，本次只设计。

## 验收对照

- [ ] 提交完整配置一次校验并按版本原子生效；无独立草稿生命周期，失败不改变旧版，历史内容恢复生成新版本。
- [ ] 共享行列执行器可在导入之前用 PG 夹具验收；SQL 数据集过滤、明确投影和写列约束使用同一固定版本。
- [ ] 行谓词与字段 SELECT/写列同时限制，报表同名字段不串权；隐藏列筛选/排序拒绝，无字段权不兜底全部列。
- [ ] 本人/团队/部门由配置实现；新增已有 regionCode 的范围只改配置/授权，Java 与部署不变。
- [ ] 无权限修改策略、非法字段/值/谓词、未知范围绑定均拒绝；失败发布不替换旧版，查询固定版本。
- [ ] 权限谓词覆盖列表/详情/total/写入；DATA_READ 不替代 DATA_EDIT，混合批量无权整批无副作用。

## 非目标 / 遗留

不写任意 SQL、SpEL/脚本执行、通用规则平台或分析聚合。
