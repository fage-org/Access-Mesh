---
doc_type: task
id: T-ADMIN-023
title: 文件服务安全加固（VIEW 门禁 + 路径安全 + 删除顺序）
status: proposed
plan: docs/plans/product-vertical-slice-plan.md
domain: admin-service
design_refs:
  - docs/design/services/admin-service-api-contract.md
  - docs/design/access-service-architecture.md
depends_on: [T-ACCESS-021]
blocks: [T-ACCESS-026]
acceptance:
  - "detail/page/download 全部补 VIEW 门禁（对齐 upload/delete 已有校验，资源类型按收敛后类型码）"
  - "统一路径安全函数（规范化后必须位于存储根目录内）应用于上传、读取、下载、删除四条路径，替换仅 upload 有检查的现状；filePath 虽源于 DB 仍做纵深防御"
  - "删除顺序反转：先同事务提交元数据软删除，事务提交成功后再物理清理文件；物理清理失败保留孤儿文件并记 WARN（孤儿文件优于丢失有效文件），不因文件失败回滚已提交的软删"
  - "单实例本地存储约束显式化：配置说明 + 架构文档登记（多实例部署下本地盘不可共享为已知限制），不建对象存储抽象层"
  - "单测 + PG 用例：越权 403、路径穿越拒绝、软删成功后文件清理失败不影响元数据状态"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-22
---

# T-ADMIN-023 文件服务安全加固

## 背景

已核实三处缺陷：getFile/pageFiles/downloadFile 均无 permissionValidator 校验（对比 upload/delete 有）；download/delete 直接 Paths.get(storagePath, filePath) 无 normalize+startsWith 根目录检查（路径穿越纵深防御缺失）；delete 先物理删文件后软删元数据——@Transactional 回滚只能恢复 DB 行，已删文件不可恢复。存储默认 ${user.home}/accessmesh-files 本地盘，服务却支持多实例。

## 范围

- 三接口 VIEW 门禁、四路径统一安全函数、删除顺序调整（事务提交后清理，用事务同步机制）。
- 单实例约束文档化。

## 当前口径

- 修复档位为安全最小修复：不做对象存储抽象、不做通用清理调度平台、不做分布式文件锁。
- 物理清理失败容忍孤儿文件（可观测、可人工清），不可容忍误删有效文件。

## 非目标 / 遗留

- 不实现共享存储/对象存储（多实例部署场景未来立项）。
- 不做孤儿文件自动回收调度（仅记录）。
