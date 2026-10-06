---
doc_type: task
id: T-ACCESS-090
title: 租户条件接线与容量基线
status: done
plan: docs/archive/2026-10-06/usage-review-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-architecture.md（数据隔离/租户机制）
  - docs/ops/deployment.md（容量基线表）
  - docs/design/project-rules.md（多租户约束段）
depends_on: []
blocks: []
acceptance:
  - "yml 配置 mybatis-flex.global-config.tenant-column: tenant_id（1.11.7 全局租户列，按列名匹配实体属性，无需逐实体注解）"
  - "TenantFactory 无租户上下文即抛错（现空数组返回=TableInfo.buildTenantCondition 静默跳过条件 fail-open，字节码实证——必须换掉）"
  - "BootstrapSeedWriterImpl 等启动期无上下文路径走 TenantManager.withoutTenantCondition 显式豁免；XML 手写显式约束全部保留，双机制并存语义文档化"
  - "BaseMapper 插入自动填充与现有显式 set tenantId 的叠加回归；DualTenantSameCodeIsolationPgIT 扩展+无上下文抛错用例（fail-fast 锁）"
  - "无上下文 Flex 路径全量清点（QueryWrapper/Db+BaseMapper 全部消费点，含启动与调度路径）+豁免清单为实现前置步骤并随卡记录"
  - "Javadoc/README 改真实口径（「自动为所有 SQL 追加」→按机制如实描述）"
  - "user-menu 读路径缓存评估（档位按 CacheCatalogEntry 目录）与 HikariCP/双调度器容量基线表进 deployment.md（结论至少登记）"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-06
---

# T-ACCESS-090 租户条件接线与容量基线

## 背景

MybatisFlexTenantConfig Javadoc 承诺「自动为所有 SQL 追加 tenant_id 条件」，但 MyBatis-Flex 自动租户要求实体 @Column(tenantId=true)（全仓零命中）**或全局配置 tenant-column（本仓 yml 未配）**——工厂注册永不生效，是死配置；真实隔离≈320 处 mapper XML 手写（恒等值判断，null 时 =NULL 匹配零行=天然 fail-closed）+双租户同码特征测试。README 宣传「行级隔离底座已实现」与实现形态不符。另：user-menu 每次全租户菜单全量加载逐用户推导不缓存（认证关键路径）；HikariCP 默认 10 连接等容量参数生产零配置。

## 范围

租户条件接线（全局配置+工厂 fail-fast+启动豁免）、Javadoc/README 口径、菜单缓存与容量参数评估。

## 当前口径

接线全局配置（2026-10-05 拍板 D8=B）：一行 yml+工厂无上下文抛错（空数组的静默跳过=fail-open 不可接受）+启动期显式豁免+插入填充回归；XML 手写约束不迁移（双机制并存，语义文档化）。接线影响面=Flex 全部生成 SQL：QueryWrapper/Db 类查询 4 文件+**BaseMapper 生成方法消费面实测 ≥40 文件**（含启动期 BootstrapSeedWriterImpl/AccessBootstrapInitializer 与调度等非请求路径）——**无上下文 Flex 路径全量清点+豁免清单为实现前置步骤**。

## 验收对照

- [x] 全局配置+工厂抛错落地（fail-fast 用例）
- [x] 启动豁免路径可用
- [x] 插入填充回归绿
- [x] PgIT 扩展通过
- [x] Javadoc/README 口径一致
- [x] 菜单缓存与容量基线评估登记

## 非目标 / 遗留

- 手写 XML 约束迁移到自动机制：不做（320 处显式保留）。

## 接线前盘点

已清点 44 个生成 SQL 候选调用文件（含 4 处 QueryWrapper、BaseMapper 增改查入口，未发现 Db 调用）。逐文件入口与无上下文处理见 [Flex 调用清单](evidence/usage-review-20261006/flex-tenant-inventory.md)。启动固定图为唯一生产豁免边界；任务执行已绑定 TASK 上下文，扫描走自定义 SQL，审计/登录/作业日志显式带租户插入，不扩大豁免。


## 完成记录

2026-10-06：实现与设计回写完成。`mvn test -T 1C` 2658 项，0 失败/错误/跳过，包含 E2E 与 heavy；前端 508 项、lint/typecheck/build 与 35 组 DTO 对账通过。任务对应行为证据、失败处置和本地双轨复审见 [最终验收](evidence/usage-review-20261006/final-verification.md)。
