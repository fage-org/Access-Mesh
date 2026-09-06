---
doc_type: task
id: T-ADMIN-027
title: 响应分页信封统一——admin 嵌套方言退役 + PaginatedResp 更名 PageResp + 裸数组收编
status: proposed
plan: —
domain: admin-service
design_refs:
  - docs/design/services/admin-service-api-contract.md
  - docs/design/permission-center/api-contract.md#33-分页结构
  - docs/design/project-rules.md#13-分页入参与响应规范
depends_on: []
blocks: []
acceptance:
  - "perm-common `PaginatedResp` 更名 `PageResp`（线格式零变化）：8 个 perm 域端点、SDK `PermissionFeignClient` 签名、前端 TS 类型与导出、api-contract §3.3 类名同步"
  - "admin 12 端点（user×2/config/dict/file/job×2/loginlog/notice/oauth2client/org/orgtreeconfig）响应迁扁平 `PageResp`，约 36 处 `new PaginatedResult`/`PaginationMeta` 装配点改写"
  - "5 处 `R<List<…>>` 裸数组端点（Dict×2/Menu/Org/UserOrg）收编 `ItemsResp{items}`（project-rules §1.3 既有规则）"
  - "`PaginatedResult`/`PaginationMeta` 类删除，全仓（代码/测试/文档/前端 TS）无嵌套分页方言与裸数组残留"
  - "前端 `user-manage.ts`：`PaginatedResult` TS 类型删除，用户分页与组织树配置两处改扁平读取"
  - "HttpApiPathSnapshotTest 等快照/契约锁同步；全量 mvn test 与前端 typecheck/vitest 零失败"
  - "设计回写：admin-service-api-contract 全部分页/列表端点行、api-contract §3.3 类名、project-rules §1.3 类名指引"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-06
---

## 背景

全仓现存两种分页响应方言：admin 域嵌套 `PaginatedResult{items, pagination:{total, page, size, totalPages}}`（12 端点，若依继承面）与 perm 域扁平 `PaginatedResp{items, total, pageNum, pageSize, hasNext}`（8 端点，api-contract §3.3 与 SDK Feign 冻结）。两者语义重复、字段命名互不一致（page/size/totalPages vs pageNum/pageSize/hasNext），且 admin 请求侧用 `pageNum/pageSize` 与自身响应的 `page/size` 不自洽。`project-rules.md` §1.3 本就规定扁平五字段为唯一分页响应结构——嵌套方言属存量违约。另 admin 旧面存 5 处 `R<List<…>>` 裸数组端点，违反 §1.3「非分页列表也必须 `{items:[…]}` 包装」。

## 范围

- **后端**：perm-common `PaginatedResp` → `PageResp` 更名（8 perm 端点 + SDK `PermissionFeignClient` 签名，compile/docs-only）；admin 12 端点 36 处装配点迁 `PageResp`；5 处裸数组收编 `ItemsResp`；删 `PaginatedResult`/`PaginationMeta`。
- **前端**：`user-manage.ts` 的 `PaginatedResult` 类型与 `pagination` 读取改扁平；`PaginatedResp` TS 类型随更名（类型定义分散于 role-manage.ts 等并 re-export，随更名同步）。
- **文档**：admin-service-api-contract 分页/列表端点行、api-contract §3.3 类名、project-rules §1.3 类名指引。
- **测试**：HttpApiPathSnapshotTest 签名串、分页相关断言与 mock。

## 当前口径

- 全仓唯一分页方言 = 扁平五字段 `{items, total, pageNum, pageSize, hasNext}`，承载类 `PageResp<T>`（perm-common，由 `PaginatedResp` 更名，线格式与字段零变化）。
- 无分页列表统一 `ItemsResp{items}`；`data` 禁止裸数组（§1.3 既有规则）。
- 分页字段不进 `R`：信封管传输（code/message/requestId/traceId），分页管载荷形状，两者正交、组合使用 `R<PageResp<T>>`。
- 分页统一类不用 `Page` 命名：与 MyBatis-Flex `Page`（admin 持久层 19 文件在用）同名撞 import，且违反仓库 `XxxResp` DTO 命名规范。
- 与 T-ADMIN-026 编辑面重叠（同批 admin ServiceImpl），建议串行执行避免同文件冲突（并发编辑面备注，非语义依赖）。

## 验收对照

acceptance 条目即验收清单，全部满足且设计回写 done 后方可置 done。

## 非目标 / 遗留

- 不改 api-contract §3.3 线格式形状（对外契约零断线，仅类名与内部装配变化）。
- 不动 `R` 信封与 `ItemsResp`（ItemsResp 为「禁裸数组」最小落体，保留）。
- 不合并 `ItemsResp` 与 `PageResp`（无分页语义的列表塞 4 个 null 分页字段为负收益）。
