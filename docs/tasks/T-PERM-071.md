---
doc_type: task
id: T-PERM-071
title: 独立依赖声明与可选 SDK 协调
status: proposed
plan: —（无所属计划；自动授权实施序列）
domain: access-service
design_refs:
  - docs/design/dependency-auto-grant.md#architecture
  - docs/design/dependency-auto-grant.md#data-model
  - docs/design/dependency-auto-grant.md#integration
  - docs/design/dependency-auto-grant.md#compiler
  - docs/design/access-service-api-contract.md
  - docs/design/schema/access-service.sql
  - docs/design/frontend/resource-dependency.md
  - docs/design/extension-guide.md
depends_on:
  - T-PERM-070
  - T-PERM-078
  - T-PERM-074
blocks: []
acceptance:
  - "declaration/manifest_sync 与编译图落地，RESOLVED/REJECTED、同键并集聚合、反向索引和 dirty 重判符合采用设计；不建 support 表。"
  - "资源单条 sync 与按类型 scope FULL 独立可用；仅资源接入无需 manifest/registration starter/空依赖；更新依赖无需重传全部资源。"
  - "manifest 独立 FULL：静态 JSON/动态 Provider、完整空依赖清单、语义 hash 规范化与四条件幂等、description 更新、逐项失败诊断；不得把 HTTP 200 当全部成功。"
  - "编译源/目标同 owner、资源与操作存在性、自依赖/联合图环校验；无跨 owner/内部目标 override，MANIFEST 管理面拒改删；同 owner 管理写入口按 M3 结论实施。"
  - "SDK 复用现役凭证，独立 manifest 发布与可选协调；不强制大清单，不把 git revision 当资源 syncVersion；动态刷新、发布排序、空资源集和前置部分失败按 M1 验收。"
  - "batch-sync、DEPENDENCY:SYNC、autoGrant 全字段链及 bootstrap/前端 perms/routes/mock/SDK/契约锁步退役；实际存量来源与边迁移有核查记录。"
  - "manifest M2M 精确白名单与服务端所有权校验闭合，不新增用户 API 固定图授权行；管理通道如保留则按 M3 完成门禁与契约，不以 UI 隐藏替代。"
  - "真实接口验收分别覆盖仅资源接入、仅依赖更新、可选两步协调失败重试；编译并发协议沿 M4，正式字段/错误码与前端设计回写完成。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-20
---

# T-PERM-071 独立依赖声明与可选 SDK 协调

## 背景

自动授权简化方向已采纳，[设计稿](../design/dependency-auto-grant.md)为实施依据。资源同步保持独立；无需自动授权的接入方不承担依赖清单成本。

## 当前口径

T-PERM-078 先收敛设计 §16 的实施协议，T-PERM-074 先闭合既有资源同步重试问题。来源不再落逐路径 support，API 派生仍由 T-PERM-054 承接且维持暂缓。本卡 proposed，不表示新 manifest 端点已交付。

## 范围

声明数据层、编译器、独立 manifest 通道、SDK 发布/可选协调、管理写入口迁移、契约和前端兼容收口。资源 sync/full-sync 原有独立使用方式保持，相关协议修正按 M1 已定内容实施；依赖物化回收挂接归 072。

## 验收对照

见 frontmatter acceptance。以同服务月报/模板为最小业务闭环，同时用无依赖的纯资源接入证明未引入强制依赖。新端点/形状只在正式契约登记，不由任务卡重定义。

## 非目标 / 遗留

物化与解释分别归 072/073；跨系统依赖、类型级种子、注解、异步队列不进入本卡。M1/M3/M4 没有稳定结论不得自行猜测接口、迁移或门禁行为。
