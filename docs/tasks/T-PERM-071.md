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
  - "编译源/目标同 owner、资源与操作存在性、自依赖/联合图环校验；资源 status 停用不等于缺失、不据此拒编或移除贡献，来源服务启用门禁保持；MANIFEST 为唯一声明写入来源，无 ADMIN_UI 或跨 owner/内部目标 override。"
  - "SDK 复用现役凭证，独立 manifest 发布与可选协调；资源 FULL 与 manifest FULL 按各自 scope 校验发布源递增代次，旧代次在写入/缺失删除前拒绝，重试沿用代次及快照；不以 git revision、资源项 syncVersion 或本机 now() 替代发布排序。"
  - "按 M1 验证混用 FULL scope 的增量/FULL 共同排序与双向防护，纯增量旧协议保持；覆盖跨键乱序、代次并发与同代次冲突、部分失败重试、dirty 重判及存量客户端迁移；动态刷新和前置部分失败按其最终协议验收。"
  - "资源 FULL 接受明确完整空 items=[]，身份/所有权/代次通过后仅清本同步范围，其他维护来源与其他 scope 保留；旧代次空清单零副作用拒绝，缺失/null 非法，读取失败/分页未完/Provider 异常不得提交空快照；自动授权回收由 072 挂接。"
  - "batch-sync、DEPENDENCY:SYNC、autoGrant 全字段链及 bootstrap/前端 perms/routes/mock/SDK/契约锁步退役；交付目标库只读盘点与原表保全迁移，真实旧 schema 夹具验证各来源/软删行完整保留，有效历史 AUTO_DEP 在修改前中止；不得将测试库报告当部署存量结论。"
  - "manifest M2M 精确白名单与服务端所有权校验闭合，不新增用户 API 固定图授权行；关闭 create/update/remove 服务端写入口并同步清理前端调用与固定图引用，验证不能直接请求绕过；读门禁保持。"
  - "编译接入共同资源树锁前，统一混合类型删除现有 RESOURCE_ENTITY→ABSTRACT_ROLE 反序及同模式调用点，按目标全序验证与组织/角色写入的确定性交错。"
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

声明数据层、编译器、独立 manifest 通道、SDK 发布/可选协调、管理写入口关闭及存量迁移、契约和前端兼容收口。管理只读边界按设计 §5，旧 autoGrant=false 边不得静默转为自动授权声明。资源 sync/full-sync 原有独立使用方式保持，相关协议修正按 M1 已定内容实施；依赖物化回收挂接归 072。

## 验收对照

见 frontmatter acceptance。以同服务月报/模板为最小业务闭环，同时用无依赖的纯资源接入证明未引入强制依赖。新端点/形状只在正式契约登记，不由任务卡重定义。

## 非目标 / 遗留

物化与解释分别归 072/073；跨系统依赖、类型级种子、注解、异步队列不进入本卡。M3 管理只读与迁移保全边界按设计 §5/§10.1，M1/M4 按 §4.4.1/§8 实施；对未知部署库不自动执行迁移或清理。
