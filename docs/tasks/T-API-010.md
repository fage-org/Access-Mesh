---
doc_type: task
id: T-API-010
title: 分页口径统一 200+hasNext
status: proposed
plan: docs/plans/usage-review-remediation-plan.md
domain: cross-service
design_refs:
  - docs/design/project-rules.md（分页规范段——本任务将改写）
  - docs/design/access-service-api-contract.md（§2.3 分页/§10.3 示例/§13.1/§14.1）
  - docs/design/decision-registry.md（定案取代登记）
depends_on: []
blocks: []
acceptance:
  - "7 个专用 DTO 上限改 200；全部约 41 个分页端点超限行为一致（400 拒绝或截断+hasNext 二选一，实现定稿后全端点一致并有锁定用例）"
  - "分页响应统一带 hasNext（尾页/满页/空页三态正确用例）"
  - "取代「专属分页上限优先」定案的登记完成（decision-registry+project-rules 分页段同步改写）"
  - "契约 §2.3/§10.3/§13.1/§14.1 四处文本互恰（消除 ≤100 与示例 200 的矛盾）"
  - "全量枚举 hasNext 循环为标准写法入契约（前端 loadAllRoles 先例推广）；full-sync items 豁免（2026-09-21 拍板）不受影响"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-05
---

# T-API-010 分页口径统一 200+hasNext

## 背景

分页超限两种相反行为并存：7 个专用 DTO @Max(100) 走 400（11 端点）；约 30 端点走 PageUtil 200 静默截断（Math.min，无截断标记）——大于 200 的列表静默漏数据（全量脚本以为拉全了）。契约 §2.3 写 ≤100、§10.3 示例写 pageSize:200、§13.1/§14.1 写上限 200，三处矛盾。project-rules 既有「专属分页上限优先」定案。

## 范围

全端点统一 200+hasNext、契约与规范文本对齐、定案取代登记。前端 magic number 收敛的常量部分归 T-API-011 同批（分页默认值散落）。

## 当前口径

统一 200+hasNext（2026-10-05 拍板 D5=B，**取代**「专属上限优先」定案——登记后生效）；超限行为形态（硬拒绝 vs 截断+hasNext）实现时定稿并锁用例，二选一须全端点一致。

## 验收对照

- [ ] 上限统一 200+行为一致
- [ ] hasNext 三态用例
- [ ] 定案取代登记完成
- [ ] 契约四处互恰
- [ ] hasNext 循环标准写法入册；full-sync 豁免不动

## 非目标 / 遗留

- full-sync items 豁免维持（清单完整性是协议语义，2026-09-21 拍板）。
