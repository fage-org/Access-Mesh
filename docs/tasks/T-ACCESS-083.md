---
doc_type: task
id: T-ACCESS-083
title: 认证链防护收敛（防枚举/验证码计数/SMS 下线/密码复杂度）
status: proposed
plan: docs/plans/usage-review-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md（§6.1 登录族/§7.7 密码策略）
  - docs/design/services/access-service.md（认证）
depends_on: []
blocks: []
acceptance:
  - "用户不存在与密码错误的 code 与 message 均不可区分（只改 message 不改 code 仍可枚举——两者都要统一）"
  - "验证码失败计入锁定计数的回归锁（旧实现下失败）"
  - "SMS 登录通道下线：login/sms 端点+Gateway 白名单条目+契约段落删除，全量绿"
  - "PASSWORD_TOO_WEAK 错误码落地+复杂度校验覆盖管理员重置与自助改密两路径；弱密码被拒用例"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-05
---

# T-ACCESS-083 认证链防护收敛

## 背景

登录错误消息可区分（10001「用户不存在」vs 10005「账号或密码错误」，`AuthAppServiceImpl.java:189,208`）构成用户名枚举通道；图形验证码 4 位纯数字且验证码失败不计锁定（recordLoginFail 只在用户不存在/密码错误两处调用）——防护名义两层实际一层；SMS 校验端点活着但签发端不存在（`sms:code:` 前缀全仓只读不写）且 smsLogin 无失败锁定，是预埋的无锁定认证旁路；契约 §7.7 承诺 PASSWORD_TOO_WEAK 但全仓无此错误码、无复杂度校验（唯一约束 @Size(8,32)）。

## 范围

登录失败口径统一、验证码失败计数、SMS 通道下线、密码复杂度校验实现。验证码替代通道（视障无障碍）不在本卡。

## 当前口径

SMS 下线（2026-10-05 拍板）：删端点+白名单+契约段；将来实现短信登录须连发送端/失败锁定/限流一起重新立项。密码复杂度实现（拍板）：新增 PASSWORD_TOO_WEAK+复杂度规则（至少字母+数字两类、禁纯数字，细则实现时定稿），覆盖 resetPassword 与自助改密。

## 验收对照

- [ ] code+message 双统一（防枚举）
- [ ] 验证码失败计数回归锁
- [ ] SMS 三件删除全量绿
- [ ] PASSWORD_TOO_WEAK 两路径生效用例

## 非目标 / 遗留

- 验证码替代通道（音频/行为式）与图形验证码强度升级：登记，随产品化评估。
