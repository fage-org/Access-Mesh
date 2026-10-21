---
doc_type: task
id: T-ACCESS-149
title: example 专属字段与通用范围接入
status: in-progress
plan: docs/plans/resource-association-plan.md
domain: cross-service
design_refs:
  - docs/design/resource-association.md#example
  - docs/design/example-permission-demo.md#field-permissions
  - docs/design/example-permission-demo.md#dynamic-policy
  - docs/design/example-service-api-contract.md
  - docs/design/schema/example-service.sql
depends_on:
  - T-ACCESS-147
  - T-ACCESS-148
acceptance:
  - "A/B 同名字段由业务维护且身份不同，读取/编辑操作定义复用；通用 X 仅一份 IAM 实例，A/B 各自发布对其类型的整体引用，实例级用户授权仍可不同。"
  - "初始化脚本和 demo-scope/发布队列不再同步改写 MANAGED 通用实例；本地映射不是资源实例的另一权威源，错误和重试可定位。"
  - "查询/写入/导入/快照/后台任务中适用路径消费有效关系，不能只更新页面或前台查询；远端调用不进入本地数据库写事务。"
  - "共享实例的停用/关系解绑能按定稿退出当前报表范围，删除 A 不影响 B；缺目录/映射/关系和远端故障按契约拒绝。"
  - "准备脚本、API 样例、example 契约与必要 DDL 回写；无业务页面、无额外双向同步或新 SQL 平台。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-22
---

## 背景

当前 example 把字段和范围均作为业务同步实例；要展示通用实例由 IAM 管理，必须调整准备、发布与业务映射职责，不能只增加关联字段。

## 范围

专属字段发布主资源归属；通用范围由 IAM 管理链路准备，example 只维护业务谓词映射和引用发布；调整 demo-scope、可靠发布、脚本、SDK 调用与使用样例。

## 当前口径

复用两张报表与行列 SQL 解释器，保留报表 DATA_EDIT 门槛。金额用读权限证明隔离，实际编辑继续使用 remark；不将本卡扩成金额编辑或行列配对能力。

详细规则以[资源关系设计](../design/resource-association.md)及上列 design_refs 为准。发布协议与运行时已有实现，按用户要求推进不依赖待决项的接入；报表整类引用与字段归属沿既有冻结事件链路实现。

待用户明确：退役 example 本地通用目录后，策略映射保存是否允许先于 IAM 实例准备，或必须增加服务身份下的远程批量目录核验。现行策略保存要求本地实例已登记/启用/发布，不能把直接删除该检查当作已获确认。待决定期间保持这段旧保存路径，不擅自放宽或新增远程核验契约。

报表引用、字段归属与 SDK 发布已实现并完成定向验证，见[实施验证](evidence/T-ACCESS-149/verification.md)。准备脚本的目标类型和 IAM 目录准备已调整，但策略保存分支尚未闭合，脚本未作为完整流程执行，E2E 夹具与旧目录退役仍待接续。

## 验收对照

- [ ] A/B 同名字段由业务维护且身份不同，读取/编辑操作定义复用；通用 X 仅一份 IAM 实例，A/B 各自发布对其类型的整体引用，实例级用户授权仍可不同。
- [ ] 初始化脚本和 demo-scope/发布队列不再同步改写 MANAGED 通用实例；本地映射不是资源实例的另一权威源，错误和重试可定位。
- [ ] 查询/写入/导入/快照/后台任务中适用路径消费有效关系，不能只更新页面或前台查询；远端调用不进入本地数据库写事务。
- [ ] 共享实例的停用/关系解绑能按定稿退出当前报表范围，删除 A 不影响 B；缺目录/映射/关系和远端故障按契约拒绝。
- [ ] 准备脚本、API 样例、example 契约与必要 DDL 回写；无业务页面、无额外双向同步或新 SQL 平台。

验证层：复用 example 定向单测与 PG SQL 测试；端到端组合由 T-ACCESS-150 在 e2e 模块完成，不在 example 内新建跨服务轨。

## 非目标 / 遗留

不恢复历史 BI 页面，不扩展编辑列、不支持单元格规则和历史数据迁移。
