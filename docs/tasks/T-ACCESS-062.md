---
doc_type: task
id: T-ACCESS-062
title: （ADM-T07）API 独立授权与 legacy 协议退役
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §6.6/§9.2/§9.4
depends_on:
  - T-ACCESS-061
  - T-PERM-092
blocks: []
acceptance:
  - "全服务迁完后受控清理系统来源 API:ACCESS 授权与旧快照缓存（N30：AUTHORITY_ROOT 等受保护行走受控迁移、不在普通授权页硬删；不误删业务授权或 API 登记目录；回滚数据可追踪）"
  - "API 授权生产入口退役：bootstrap 固定图删除 API:ACCESS 类型级 GrantSpec（BootstrapGraphDefinition T-API-001 行）与 apiRoutes 派生实例授权——删种子行使已初始化库重启呈固定图 fail-fast（沿 T-ADMIN-029/T-ACCESS-054 先例，处置=受控清理先行或重建库，runbook 记账）；授权写入口（apply-grant-plan/保存）对 API 类型拒绝（类型门禁先例形态）；前端授权页资源树 API 分支退役；登录权限串 API:ACCESS 段随授权清零自然收敛（无单独改造）"
  - "负向验收：空库启动后 role_resource_permission 零 API 类型授权行；写入口对 API 类型拒绝的错误码与 reason 回归锁"
  - "legacy checkInterface 在线/快照协议退役（旧命名空间、在途加载、负缓存一并处理）；API 登记目录保留（资源/接口元数据继续经 resource_api_mapping 与 API 资源行维护）"
  - "N29 终态确认：无永久双执行——R2 已完成（T-PERM-092）且全部服务已迁新模式，旧协议与旧授权模式退役闭环；计划两个完成条件全部闭合"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-25
---

# T-ACCESS-062 （ADM-T07）API 独立授权与 legacy 协议退役

## 背景

设计 §9.2 第二个完成条件（报告临时编号 ADM-T07）：本卡完成才表示 API 独立授权模式退役——不能用「R2 入口统一了」证明 T-PERM-054 完成。

## 范围

- 清理脚本/迁移受控通道与回滚账本；契约总册 legacy 章节退役标注；注册 API 保留目录。
- API 授权生产入口退役（bootstrap 种子行、写入口类型门禁、前端授权页分支）——API 登记目录与映射维护保留不动。

## 非目标 / 遗留

- 不删除 resource_api_mapping 的 API 登记功能（方案 A 仍消费登记实体与路由映射）。
