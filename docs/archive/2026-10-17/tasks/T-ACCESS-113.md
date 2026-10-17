---
doc_type: task
id: T-ACCESS-113
title: OAuth2密钥写入字节上限
status: done
plan: docs/archive/2026-10-17/permission-lifecycle-boundaries-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md
depends_on: []
acceptance:
  - 创建和更新拒绝超限，72字节边界及多字节输入正确
  - 拒绝时不保存或覆盖旧密钥，PUBLIC 与不更新密钥保持
  - 存量验密不变，接口错误提示与契约一致
design_writeback:
  required: true
  status: done
last_updated: 2026-10-17
---

## 背景

承接 [Q-080](../../../pending-problems.md#q-080)，实施范围已于 2026-10-17 确认。

## 范围

新增及更新 clientSecret 拒绝超过 72 个 UTF-8 字节；保持已有凭据验密及 PUBLIC 无密钥规则，不自动轮换存量密钥；提示明确字节限制。

## 当前口径

以 design_refs 中的现行契约及本任务范围为准；新增设计取舍必须由用户决策。

## 验收对照

- [x] 创建和更新拒绝超限，72字节边界及多字节输入正确
- [x] 拒绝时不保存或覆盖旧密钥，PUBLIC 与不更新密钥保持
- [x] 存量验密不变，接口错误提示与契约一致

## 非目标 / 遗留

现有前端没有 OAuth2 客户端密钥管理表单；本卡不新增页面，由 API 校验反馈明确字节限制。其他事项不扩入本卡。


## 完成记录

2026-10-17：后端根命令 `mvn test -T 1C` 2944 项通过，零失败/错误/跳过（含 E2E/heavy）；前端 611 项与类型、lint、45 组 DTO 对账、构建通过。双轨评审及定向证据见[验收记录](evidence/lifecycle-boundaries/verification.md)。
