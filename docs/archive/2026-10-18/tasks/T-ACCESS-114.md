---
doc_type: task
id: T-ACCESS-114
title: 服务凭证 SHA-256 验密与旧哈希退役
status: done
plan: —
domain: access-service
design_refs:
  - docs/design/service-authentication.md#service-credential-digest
  - docs/design/access-service-api-contract.md#contract-section-24-1
  - docs/design/schema/access-service.sql
  - docs/ops/deployment.md#service-credential-digest-upgrade
depends_on: []
acceptance:
  - 服务端随机 secret 以带版本的 SHA-256 摘要存储，完整 UTF-8 字节参与验密，固定长度摘要恒定时间比较
  - BCrypt、未知版本及损坏摘要统一拒绝 20065，无兼容分支、认证缓存或查询内迁移
  - 凭证与服务状态逐请求读取，既有错误码顺序、身份派生、吊销及 SDK 协议保持
  - 定向单测与真实 PG 验证、含 E2E/heavy 的全量回归、双轨评审及文档回写完成
design_writeback:
  required: true
  status: done
last_updated: 2026-10-18
---

## 背景

承接 [Q-076](../../../pending-problems.md#q-076)。改造前高熵服务 secret 每次用 BCrypt 验密，算法探针显示约 64ms CPU 成本，影响高频运行查询。

## 范围

复用服务凭证领域服务完成签发与验密替换；适配已有凭证测试；回写服务认证、契约、schema 注释及部署换发说明。任务不归属跨任务计划。

## 当前口径

采用[服务凭证摘要设计](../../../design/service-authentication.md#service-credential-digest)。当前无正式部署，旧凭证直接重新签发，不保留 BCrypt 兼容，不自动转换旧哈希。数据库摘要不是可提交的凭证。新旧服务端须协调切换，不承诺混合版本互验。

## 验收对照

- [x] 签发、验密与退役边界。
- [x] 生命周期、真实存储与 SDK/E2E 接线。
- [x] 定向与全量回归。
- [x] 代码轨、文档轨评审与权威回写。

## 非目标 / 遗留

用户密码、OAuth2 客户端密钥、SDK 协议、权限引擎和数据库查询结构不变；不增加认证成功缓存、HMAC 独立密钥或访问令牌机制。算法探针不作为部署容量承诺；实际吞吐由部署规格与端到端负载决定。

## 完成记录

2026-10-17 定向单测 47 项、PG/Schema/服务删除并发 49 项、业务接入 E2E 12 项通过；旧实现关键反例 3 项实证失败。2026-10-18 根目录 `mvn test -T 1C` 2963 项通过，0 失败/错误/跳过，包含 E2E/heavy，耗时 18 分 18 秒。代码与文档双轨评审发现均已处置，权威设计与部署说明已回写。命令、证据及源码清单见[验收记录](evidence/T-ACCESS-114/verification.md)。
