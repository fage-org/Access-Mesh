---
doc_type: task
id: T-ADMIN-035
title: OAuth2 同租户委托与用户动态有效性实施
status: done
plan: —
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md §6.1
depends_on:
  - T-ADMIN-034
blocks: []
acceptance:
  - "authorize 校验客户端租户等于平台会话租户；token/refresh 保留全局客户端查询并校验客户端、码/刷新记录的租户绑定"
  - "authorize/token/refresh 及 JWT 资源请求均经同源用户检查确认同租户、未删除、status=1，不采用正向状态缓存"
  - "JWT 的客户端与载荷租户一致；禁用/删除后的下一次委托请求拒绝，既有客户端动态校验、黑名单、scope/audience、一次性消费保持"
  - "覆盖跨租户、用户禁用/删除、状态查询技术故障、正常委托；复用现有 OAuth2 与拦截器测试，按最低可信层验证"
  - "回写契约中的待实施标记与发布边界，不新增令牌全局扫描、撤销广播或另一套会话机制"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-04
---

# T-ADMIN-035 OAuth2 同租户委托与用户动态有效性实施

## 背景

[T-ADMIN-034](T-ADMIN-034.md) 完成链路盘点与两项定案，原实现未统一执行这些检查，本卡已补齐同源用户有效性校验。

## 范围

OAuth2AppServiceImpl 的授权/兑换/刷新与 RequestContextInterceptor 的 JWT 认证路径，复用现有用户领域查询。原清单计划只完成定案，本卡另行承接实施。

## 当前口径

客户端只接受本租户用户委托；各签发/刷新及每次 JWT 资源请求检查同租户有效启用用户，禁用后下一请求拒绝，无正向状态缓存（2026-10-04 确认）。匿名 token/refresh 仍全局定位客户端，不能改为依赖平台会话；随后核对可信记录租户。错误回复沿现有客户端/码/令牌无效及认证失败语义，不泄露用户记录详情。

## 非目标 / 遗留

- 不新增 OAuth2 授权前端页面或跨租户同意机制。
- 不改变 access/refresh TTL 配置、一次性消费、scope/audience 与客户端绑定。

## 验收对照

- [x] authorize 校验客户端租户等于平台会话租户；token/refresh 保留全局客户端查询并校验客户端、码/刷新记录的租户绑定
- [x] authorize/token/refresh 及 JWT 资源请求均经同源用户检查确认同租户、未删除、status=1，不采用正向状态缓存
- [x] JWT 的客户端与载荷租户一致；禁用/删除后的下一次委托请求拒绝，既有客户端动态校验、黑名单、scope/audience、一次性消费保持
- [x] 覆盖跨租户、用户禁用/删除、状态查询技术故障、正常委托；复用现有 OAuth2 与拦截器测试，按最低可信层验证
- [x] 回写契约中的待实施标记与发布边界，不新增令牌全局扫描、撤销广播或另一套会话机制

## 完成记录

2026-10-04：实现、设计回写与本地代码/文档双轨评审完成。`mvn test -T 1C` 全量 2521 项，0 失败/错误/跳过（含 E2E 29 项及 heavy）；前端 `pnpm test` 492 项、build/typecheck/lint 通过。定向回归、反例实证、退役测试映射及部署边界见[综合验收](evidence/checklist-followup/final-audit.md)。
