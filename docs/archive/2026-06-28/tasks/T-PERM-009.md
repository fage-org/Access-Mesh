---
doc_type: task
id: T-PERM-009
title: scopeMode 枚举 + 响应结构重构（宽义）
status: done
plan: docs/archive/2026-06-28/scope-mode-migration-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center-v3.5-design.md#§3-数据权限契约
  - docs/design/permission-center/api-contract.md#§6.7
depends_on: [T-PERM-003]
blocks: [T-PERM-010, T-PERM-011, T-PERM-012, T-PERM-013, T-PERM-015]
acceptance:
  - "[x] ScopeMode 4 态枚举定义(DENIED/INSTANCE/ALL/EMPTY)放 perm-common"
  - "[x] QueryScopesResp 重构为分类模型: 按 (resourceTypeCode, operationCode) 双层 Map(scopeGroups[])"
  - "[x] 每格各自 ScopeMode, items[] 挂格下仅 INSTANCE 非空"
  - "[x] 删除 allowed(合并进 ScopeMode) + mergeMode + 顶层 ScopeEntry"
  - "[x] queryScopes/processScopePermissions/buildScopeGroup 按 (resourceType,operation) 分桶重写"
  - "[x] mvn test 通过(135 tests 0 failures) + api-contract §6.7 回写"
design_writeback:
  required: true
  status: done
last_updated: 2026-06-21
---

# T-PERM-009 scopeMode 枚举 + 响应结构重构（宽义）

> 来源：[scope-mode-migration-plan](../scope-mode-migration-plan.md) 任务 B-1（工作单 B2）
> 范围：宽义 = 定义枚举 + 重构 QueryScopesResp 结构（与 T-PERM-010 合并）

## 方案定稿（用户确认 2026-06-20）

### 1. ScopeMode 4 态枚举（合并 allowed）

| 枚举 | 含义 | 业务方行为 |
|---|---|---|
| `DENIED` | 无操作权限（原 allowed=false） | 拒绝/403，不发 SQL |
| `INSTANCE` | 有权限 + 具体实例授权 | items[] 加 IN 过滤 |
| `ALL` | 有权限 + 全量授权 | 不加范围过滤 |
| `EMPTY` | 有权限但无数据范围 | 返回空结果，不发 SQL |

放 perm-common（`cn.ac.fage.accessmesh.perm.common.enums.ScopeMode`），供 admin/gateway 共用。

### 2. 分类模型（不单 scopeMode，按资源类型×操作分类）

分类键：`(resourceTypeCode, operationCode)` 双层 Map。每格各自 ScopeMode。

### 3. 删除字段
- `allowed`（合并进 ScopeMode）
- `scopeTypeCodes[]`（分类模型下冗余——每格已明确 resourceType+operation，ALL 覆盖即该格自身）

### 4. 新响应结构

```json
{
  "scopeGroups": [
    {
      "resourceTypeCode": "REPORT",
      "operationCode": "VIEW",
      "scopeMode": "ALL",
      "items": []
    },
    {
      "resourceTypeCode": "REPORT",
      "operationCode": "EXPORT",
      "scopeMode": "INSTANCE",
      "items": [{"resourceCode": "r-001", "codeType": "default", "resourceName": "..."}]
    },
    {
      "resourceTypeCode": "REPORT",
      "operationCode": "DELETE",
      "scopeMode": "DENIED"
    }
  ],
  "permissionVersion": "...",
  "cacheTtlSeconds": 60
}
```

保留 `permissionVersion`（T-PERM-001 收尾移除）、`cacheTtlSeconds`。
保留 `reason`（DENIED 时填拒绝原因，挂顶层或每格？待重写时定——倾向顶层，DENIED 是整体拒绝）。

## 待核实项（动手前）

1. `QueryScopesReq` 结构：当前 queryScopes 是"父资源下子范围"查询（parentResourceTypeCode/parentCode），需确认双层 Map 键来源
2. `processScopePermissions` / `ScopeAccumulator` 完整逻辑（权限合并语义）
3. `buildQueryScopesResponse` 完整体
4. 调用方（外部消费 QueryScopesResp）
5. 测试

## 设计回写

- `api-contract.md §6.7`：QueryScopesResp 新结构 + ScopeMode 4 态语义
- `permission-center-v3.5-design.md §3`：L2 数据权限契约对齐 scopeMode 分类模型
