---
doc_type: task
id: T-ADMIN-034
title: OAuth2 委托链租户与用户状态校验定案
status: proposed
plan: docs/plans/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md §6（auth 能力：OAuth2）
depends_on: []
blocks: []
acceptance:
  - "两子项分别拍板（decision-question-protocol 举例上报，合并承载不替代各子项验收）：① authorize 租户约束——全局解析 clientId 不比对客户端租户与会话租户（其他租户合法客户端可得到本租户用户委托）的入口约束定案；② 委托用户有效性——资源访问/兑换/刷新链对禁用/删除用户的状态校验与 TTL 接受边界（现状：禁用用户已签令牌 TTL 内可用、刷新链可延长）"
  - "① 须保持 token/refresh 匿名入口全局查客户端的前提（不能一律改按会话租户过滤）；实际入口为平台会话发起的 API、仓内无 authorize 前端消费者的现状写明"
  - "② 撤权即时性与 TTL 的取舍结论（校验点、可接受窗口、开放资源路径扩展的影响面）落契约 §6；实施若超出单卡范围另立新号"
  - "拍板前先产出逐场景链路盘点（入口→链路→消费形态）供用户指认（T-PERM-045 先例：现状与预期不符时先盘点再决策）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-01
---

# T-ADMIN-034 OAuth2 委托链租户与用户状态校验定案

## 背景

承接 [Q-029](../pending-problems.md#q-029)（合并 Q-030）：两缺口——① OAuth2AppServiceImpl.authorize 全局解析 clientId 不比对客户端租户与会话租户，码/令牌租户取会话；② RequestContextInterceptor.authenticateOAuth2Jwt 动态检查客户端启用但不查用户状态，授权码兑换与 refresh 也未查记录中的用户有效性。分别影响委托租户隔离与撤权即时性；开放资源路径默认仅 userinfo，配置扩展会扩大暴露面。

## 范围

定案卡：两子项分别拍板+契约 §6 修订；实施视拍板范围另立。

## 当前口径

按记忆纪律（设计决策前先对齐现状认知）：拍板前先出链路盘点防「现状与预期不符」整体否决。

## 非目标 / 遗留

- OAuth2 客户端字段清空语义（Q-043 域内）。
