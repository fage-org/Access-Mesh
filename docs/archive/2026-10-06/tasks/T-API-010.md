---
doc_type: task
id: T-API-010
title: 分页口径统一 200+hasNext
status: done
plan: docs/archive/2026-10-06/usage-review-remediation-plan.md
domain: cross-service
design_refs:
  - docs/design/project-rules.md（§1.3 分页规范）
  - docs/design/access-service-api-contract.md（§2.3 分页/§10.3 示例/§13.1/§14.1）
  - docs/design/services/example-service.md（报表列表分页）
  - docs/design/decision-registry.md（定案取代登记）
depends_on: []
blocks: []
acceptance:
  - "7 个专用 DTO 上限改 200；全部约 41 个分页端点超限行为一致（统一 HTTP 400 拒绝，全端点一致并有锁定用例）"
  - "分页响应统一带 hasNext（尾页/满页/空页三态正确用例）"
  - "取代「专属分页上限优先」定案的登记完成（decision-registry+project-rules 分页段同步改写）"
  - "契约 §2.3/§10.3/§13.1/§14.1 四处文本互恰（消除 ≤100 与示例 200 的矛盾）"
  - "全量枚举 hasNext 循环为标准写法入契约（前端 loadAllRoles 先例推广）；full-sync items 豁免（2026-09-21 拍板）不受影响"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-06
---

# T-API-010 分页口径统一 200+hasNext

## 背景

分页超限两种相反行为并存：7 个专用 DTO @Max(100) 走 400（11 端点）；约 30 端点走 PageUtil 200 静默截断（Math.min，无截断标记）——大于 200 的列表静默漏数据（全量脚本以为拉全了）。契约 §2.3 写 ≤100、§10.3 示例写 pageSize:200、§13.1/§14.1 写上限 200，三处矛盾。project-rules 既有「专属分页上限优先」定案。

## 范围

全端点统一 200+hasNext、契约与规范文本对齐、定案取代登记。前端 magic number 收敛的常量部分归 T-API-011 同批（分页默认值散落）。

## 当前口径

统一 200+hasNext（2026-10-05 拍板 D5=B，**取代**「专属上限优先」定案——登记后生效）；超限统一 HTTP 400（2026-10-06 用户选择 A）：pageSize > 200 明确拒绝，不再静默截断；所有专用 DTO 与 PageUtil 同口径，响应 hasNext 指引调用方逐页读取，full-sync 豁免不变。2026-10-06 用户选择 B：example-service 的 report/list 同样改 pageNum/pageSize 并复用公共 PageResp，移除 page/size 和自定义 ListResp，不保留旧字段别名；示例上限与超限行为同为 200/HTTP 400。

## 验收对照

- [x] 上限统一 200+行为一致
- [x] hasNext 三态用例
- [x] 定案取代登记完成
- [x] 契约四处互恰
- [x] hasNext 循环标准写法入册；full-sync 豁免不动

## 非目标 / 遗留

- full-sync items 豁免维持（清单完整性是协议语义，2026-09-21 拍板）。


## 完成记录

2026-10-06：实现与设计回写完成。`mvn test -T 1C` 2658 项，0 失败/错误/跳过，包含 E2E 与 heavy；前端 508 项、lint/typecheck/build 与 35 组 DTO 对账通过。任务对应行为证据、失败处置和本地双轨复审见 [最终验收](evidence/usage-review-20261006/final-verification.md)。
