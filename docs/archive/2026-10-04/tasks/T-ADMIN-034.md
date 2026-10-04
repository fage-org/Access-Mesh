---
doc_type: task
id: T-ADMIN-034
title: OAuth2 委托链租户与用户状态校验定案
status: done
plan: docs/archive/2026-10-04/pending-problems-clearance-plan.md
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
  status: done
last_updated: 2026-10-04
---

# T-ADMIN-034 OAuth2 委托链租户与用户状态校验定案

## 背景

承接 [Q-029](../../../pending-problems.md#q-029)（合并 Q-030）：两缺口——① OAuth2AppServiceImpl.authorize 全局解析 clientId 不比对客户端租户与会话租户，码/令牌租户取会话；② RequestContextInterceptor.authenticateOAuth2Jwt 动态检查客户端启用但不查用户状态，授权码兑换与 refresh 也未查记录中的用户有效性。分别影响委托租户隔离与撤权即时性；开放资源路径默认仅 userinfo，配置扩展会扩大暴露面。

## 范围

定案卡：两子项分别拍板+契约 §6 修订；实施视拍板范围另立。

## 当前口径

当前链路核对（2026-10-04；尚未据此改变契约）：

| 入口 | 当前校验及租户来源 | 用户状态与影响 |
|---|---|---|
| authorize | 平台会话；全局查启用 clientId，校验授权类型、redirectUri、scope；授权码租户取会话，未比对客户端所属租户 | 无独立授权前用户状态复核；仓内无 authorize 前端消费者 |
| token | 匿名，客户端凭据与授权码 clientId 绑定、redirect/PKCE、一次性消费；租户取授权码 | 不复核用户启用/删除状态 |
| refresh | 匿名，全局查客户端并验证记录中的 clientId，一次性消费旧刷新令牌；租户取刷新记录 | 不复核用户状态；每次成功可签发新 access/refresh |
| JWT 资源访问 | 签名/到期、撤销黑名单、客户端动态有效性、路径 scope/audience；租户取 JWT | 认证层不复核用户状态；默认开放 userinfo 仅查有效行，删除用户业务查不到，但禁用用户仍可返回资料；扩展开放路径须单独考虑 |

实现锚点：OAuth2AppServiceImpl.authorize/tokenByAuthorizationCode/refreshToken/getClientUserInfo、RequestContextInterceptor.authenticateOAuth2Jwt。

目标：仅允许同租户委托；authorize、token/refresh 及每次 JWT 资源访问都检查同租户有效启用用户，无正向状态缓存，禁用/删除后下一请求拒绝（2026-10-04 确认）。匿名 token/refresh 仍全局查客户端再比对可信记录租户。实施由 [T-ADMIN-035](../../../tasks/T-ADMIN-035.md) 承接，本卡不声称当前实现已收紧。

## 非目标 / 遗留

- OAuth2 客户端字段清空语义（Q-043 域内）。

## 验收对照

- [x] 授权、兑换、刷新、资源访问链路逐场景盘点。
- [x] 同租户委托定案，保留匿名入口全局查客户端前提。
- [x] 用户状态采用逐请求动态检查，明确不接受禁用后的 TTL 窗口。
- [x] 契约 §6.1 区分目标与当前差异，T-ADMIN-035 承接实施。

## 完成记录

2026-10-04：代码轨只读核实当前各入口与用户查询 SQL；文档轨核对两项定案、一次性消费边界和实施载体。无本卡运行时代码变更，业务回归不适用于定案交付；实际行为验收归 T-ADMIN-035。
