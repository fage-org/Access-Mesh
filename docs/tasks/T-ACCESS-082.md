---
doc_type: task
id: T-ACCESS-082
title: 密码重置会话吊销与凭据代际闭环
status: proposed
plan: docs/plans/usage-review-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md（§6.1 OAuth2 / §7.7 重置密码）
  - docs/design/access-service-architecture.md（认证/会话相关章节）
depends_on: []
blocks: []
acceptance:
  - "resetPassword 非自身分支同事务吊销目标用户全部会话；旧会话随后续请求即失效"
  - "OAuth2 授权码存储与 RefreshTokenData 均携带密码哈希指纹+链首次签发时间；三处校验点（授权码兑换/refresh 轮换/access JWT 使用入口）全部校验代际"
  - "改密前签发的授权码在改密后兑换被拒；改密前旧刷新链拒绝（滚动续期场景用例：改密前持续刷新→改密→旧链拒绝）"
  - "旧凭据缺代际字段=拒绝，部署断存量旧链为预期并在发布说明注明；种子客户端 refresh TTL 2592000 与 DTO @Max(604800) 对齐"
  - "事实源不使用 sys_user 加列（DDL=销毁重建）与 user.updated_at（非改密写路径污染，会过度失效合法链）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-05
---

# T-ACCESS-082 密码重置会话吊销与凭据代际闭环

## 背景

resetPassword 只更新密码哈希与 force_reset_pwd 标记，无任何会话吊销（`UserAppServiceImpl.java:350-391` 止于 return，全文无 StpUtil.logout/kickout）；force_reset_pwd 的阻断只在前端路由守卫（自述「UX 层非安全层」）。OAuth2 侧 refresh 每次轮换签发新 token 并重新给足整段 TTL，链上唯一闸门是用户 status=1（`OAuth2AppServiceImpl.java:279-309`）；种子客户端单 refresh token 30 天（超 DTO 上限）；授权码兑换（`:496-523`）只查用户状态——改密前已签发的授权码与 access JWT 无失效规则，凭据吊销闭环缺失。

## 范围

会话吊销（sa-token 原生 logout(loginId)）+ OAuth2 凭据代际沿授权链传递与三处校验点；种子 TTL 对齐。

## 当前口径

resetPassword 非自身分支同事务 `StpUtil.logout(loginId)`。统一定义密码代际：授权码存储与 `RefreshTokenData` 保存**密码哈希指纹**（BCrypt 哈希前段）+**链首次签发时间**；校验点覆盖授权码兑换、refresh 轮换、access JWT 使用入口（`RequestContextInterceptor.authenticateOAuth2Jwt` 现无代际检查，与 jti 黑名单联动或代际校验）；指纹与当前 user.password 不符或超绝对上限即拒绝；旧凭据缺字段按拒绝处理（部署即断存量旧链为预期，发布说明注明）。管理员强制下线端点不实施（resetPassword 吊销落地后场景收窄，登记延后）。

## 验收对照

- [ ] resetPassword 非自身分支同事务吊销目标用户全部会话；旧会话随后续请求即失效
- [ ] 授权码与 RefreshTokenData 携带代际字段；三处校验点全部生效
- [ ] 改密前授权码兑换被拒；改密前旧刷新链拒绝（滚动续期用例）
- [ ] 旧凭据缺字段拒绝语义+发布说明；种子 TTL 对齐
- [ ] 事实源避开 sys_user 加列与 user.updated_at

## 非目标 / 遗留

- 管理员强制下线端点（若日后实施=固定图加行，排 T-ACCESS-086 后）。
- 验证码替代通道（视障无障碍，随认证链强化另行评估）。
