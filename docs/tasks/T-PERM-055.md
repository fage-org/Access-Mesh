---
doc_type: task
id: T-PERM-055
title: 域分类查询批量预载优化——matchesTypeCode 循环点查放大收敛
status: proposed
plan: ""
domain: permission-center
design_refs:
  - docs/design/access-service-architecture.md#§13
  - docs/design/permission-center/api-contract.md#§3.4
depends_on: []
blocks: []
acceptance:
  - "背景（T-PERM-046 双轨评审存疑项，2026-09-09 用户拍板登记）：PermViewAssembler 按域过滤权限视图时对每个权限条目循环调一次 matchesTypeCode（每次含 isGlobalDomain 点查+声明集查询，未命中再查全局域声明/补集，补集路径 getAllClaimedTypeCodes 为 per-domain 循环单查的存量 N+1）——1000 条目、10 域租户最坏数千条点查；当前靠管理页低频 + resolveTypeValue/resolveDomainId L1 缓存兜住"
  - "修法方向：按 domainCode 预载域实际范围（含全局域声明/补集一次计算）供循环内过滤复用，消除逐条目点查放大"
  - "不改变三模式语义：ALL/GLOBAL_PLUS/DOMAIN_ONLY 判定结果与现行逐条调用一致，用对拍回归锁（同输入逐条调用 vs 批量预载结果相等）"
  - "调用方清单（GLOBAL_PLUS 批量上下文五处）：PermViewAssembler（权限视图域过滤×2）、UserManageAppServiceImpl（×2）、RoleManageAppServiceImpl（×3）、ResourceManageAppServiceImpl（×3）、PermissionQueryAppServiceImpl（×1）；单次调用场景不动"
  - "低优先级性能项（评审定级非阻断），无硬依赖"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-09
---

# T-PERM-055 域分类查询批量预载优化——matchesTypeCode 循环点查放大收敛

> 状态：proposed（T-PERM-046 双轨评审存疑项 C，2026-09-09 用户拍板登记独立待办）
> 依赖：无硬依赖

## 背景

T-PERM-046 收口的双轨代码轨评审发现：`DomainClassifyService.matchesTypeCode` 的调用方多在批量上下文循环内逐条调用（`PermViewAssembler.filterByDomainCode` 对每个权限条目一次），每次调用含数次单行点查，最坏情况放大到数千条查询。属存量结构（T-PERM-046 改动未恶化，全局域有声明路径反而省掉一个存量 N+1），靠管理页低频 + L1 缓存兜住，登记为独立优化待办而非随任务混修。

## 范围

- 批量预载接口（如 `preloadEffectiveTypeCodes(tenantId, domainCode)` 或批量变体）+ 五处调用方改造。
- 对拍回归锁：批量预载过滤结果 == 逐条 matchesTypeCode 结果（含全局域有声明/无声明/无全局域三态）。
