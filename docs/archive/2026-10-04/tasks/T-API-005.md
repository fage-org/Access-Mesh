---
doc_type: task
id: T-API-005
title: 可选字段显式清空协议逐域定案
status: done
plan: docs/archive/2026-10-04/pending-problems-clearance-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-api-contract.md §2（通用协议：可选字段清空 §2.7）
  - docs/design/project-rules.md §7.6
depends_on: []
blocks: []
acceptance:
  - "五字段族逐域拍板清空语义（decision-question-protocol 举例上报）：condition.description、menu.path/icon 等、OAuth2 client 的 grantTypes/redirectUris/scopes/audiences 等、system-config description（现状 null 跳过且 update(entity) 忽略 null、内存组装响应与重查不一致）、role/resource extra 空白输入语义（契约 §2.7 挂接本问题的后续收敛项——空白拒绝维持既有宽松，不可遗漏）"
  - "OAuth2 客户端字段逐项核对安全后果（清空 redirectUris=锁死授权、清空 grantTypes=禁用流程类）后定各字段清空安全语义"
  - "system-config 契约 §17.2 未定义清空语义的现状确认——不能预定为空串协议（T-API-004 六字段「空串拒 400」口径是否延伸由本卡定）"
  - "拍板结论落契约 §2.7 逐字段族表（可清空/拒绝/有后果警示），role/resource extra 空白语义同批回写并解除 §2.7 的「随 Q-043 后续收敛」挂接；实施（DTO extraClear 类通道、真实 NULL 写入、表单、契约）另立新任务——本卡只定案不实施"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-04
---

# T-API-005 可选字段显式清空协议逐域定案

## 背景

承接 [Q-043](../../../pending-problems.md#q-043)（含外部报告 B-11）：可选字段缺显式清空通道——condition/menu 族 null 跳过无清空通道；OAuth2 client 族清空通道与各字段清空安全语义待明确；system-config hook 将空描述发 null、upsertSystemConfig 以 update(entity) 回写（MyBatis-Flex 默认忽略 null），清空提交可能成功却保留旧值。另契约总册 §2.7 明确把 role/resource 既有 extraClear 的空白拒绝宽松形态挂接本问题「随 Q-043 同型矩阵后续收敛」——该族是问题清单原登记遗漏的半边（codex 复评 P1 补齐），本卡一并承载。T-API-004 已交付范围（六字段+perm 轨 extra）不扩撤；Q-018 同型矩阵登记维持关联。

## 范围

定案卡：四族清空语义逐域拍板+契约 §2.7 修订；实施通道（DTO/NULL 写入/表单）另立新号。

## 当前口径

参考既有先例：T-API-004 的「冲突全端点拒绝 / type extraClear 拒清 / 空串拒 400」六字段口径与 extraClear 通道形态；逐域按此模式定，不盲推空串协议。

条件 description、系统配置 description 采用 descriptionClear；null=不改、空白拒绝、值与 Clear 同传拒绝、Clear 真实写 NULL。role/resource.extra 沿既有 extraClear，空白输入同样拒绝（2026-10-04 确认）。

菜单仅新增 iconClear；path 与 resourceType/resourceCode 关联保持不可清空，不增路径清空或整对解绑通道（2026-10-04 确认）。

OAuth2 redirectUris/scopes/audiences 可经各自 Clear 置 NULL；grantTypes、名称与密钥不提供 Clear，提供的新字符串拒绝空白，null 保持不更新。清空可空配置不撤销既有授权码/令牌，停用仍使用 status=0（2026-10-04 确认）。全部实施由 [T-API-006](../../../tasks/T-API-006.md) 承接。

## 非目标 / 遗留

- 实施与前端表单改造（定案后另立）。

## 验收对照

- [x] 条件/配置描述沿 descriptionClear；系统配置保存与重查差异明确归实施卡修复。
- [x] 菜单仅 iconClear，路径与资源关联不清空。
- [x] OAuth2 可空字段逐项说明后果，必需字段不可清空。
- [x] role/resource.extra 空白拒绝目标明确，退出原待评估安排。
- [x] 契约 §2.7 与工程规范 §7.6 回写，T-API-006 承接实施，现状与目标区分。

## 完成记录

2026-10-04：纯定案交付。代码轨核对 DTO、实际更新分支、菜单投影及 OAuth2 消费路径；文档轨核对字段表、清空后果和既有协议边界，无未处理存疑。未在本卡改运行时清空行为，测试与真实 NULL 落库验证归 T-API-006。
