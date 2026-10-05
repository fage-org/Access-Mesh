---
doc_type: task
id: T-API-013
title: 契约册导航与错误码索引
status: proposed
plan: docs/plans/usage-review-remediation-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-api-contract.md（册首导航/错误码索引）
depends_on: []
blocks: []
acceptance:
  - "册首指针强化：未成册五族+四零散端点「读哪里」导航一并完成（五族补册本身不做，等接口完全稳定）"
  - "org-user-permission-contract 与总册双向锚链接"
  - "错误码索引表（码→章节锚点，约 41 处定义全覆盖）+覆盖率测试（全部定义行有索引项）"
  - "login-log 单端点形状表随 T-ACCESS-085/T-FE-065 落地补入（转正为产品面，不违背五族不补写）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-05
---

# T-API-013 契约册导航与错误码索引

## 背景

契约总册明示五族不在册内补写（auth 登录族/dict/notice/job/login-log，T-ACCESS-040 登记取舍）+四个零散端点，合计 36 端点无契约形状文档——消费者唯一出处是 Java DTO 源码；唯一幸存的 permission-view 端点完整契约住在另一册（org-user-permission-contract.md），按主册索引会漏；总册 3227 行错误码定义散布约 41 处无索引——查「20069 是什么」只能全文搜索。

## 范围

导航与索引（不新增接口形状承诺）。

## 当前口径

五族补册等接口完全稳定再做（2026-10-05 拍板 D9）；本卡只做导航件：册首指针、跨册双向锚链、错误码索引表（测试锁覆盖率）。

## 验收对照

- [ ] 册首导航完成
- [ ] 跨册双向锚链
- [ ] 索引表覆盖率测试绿
- [ ] login-log 形状表补入（联动卡）

## 非目标 / 遗留

- 五族+零散端点全量形状表：Q-062（接口稳定后）。
