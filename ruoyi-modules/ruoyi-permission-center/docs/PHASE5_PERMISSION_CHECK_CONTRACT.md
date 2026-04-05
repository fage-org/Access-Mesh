# Phase 5 Permission Check Contract

本文件用于补充说明权限中心模块 `POST /api/perm/check` 的 Phase 5 冻结契约，优先级与 `plan/PHASE0_API_CONTRACT.md` 保持一致。

如果与 [接口文档.md](D:/project/RuoYi-Cloud-Plus/ruoyi-modules/ruoyi-permission-center/docs/接口文档.md) 的历史描述冲突，以本文件和 `plan/` 基线为准。

## Request

```json
{
  "tenantId": 1,
  "userId": 1001,
  "resourceEntityId": 2001,
  "operationPermissionId": 3001,
  "bizDomainId": 4001,
  "inheritMode": "NONE",
  "checkDependency": false,
  "context": {}
}
```

字段说明：

| 字段 | 必填 | 说明 |
|------|------|------|
| `tenantId` | 是 | 租户 ID |
| `userId` | 是 | 当前主体 ID，当前语义对应 `abstract_user_id` |
| `resourceEntityId` | 是 | 资源实体 ID |
| `operationPermissionId` | 是 | 操作权限 ID |
| `bizDomainId` | 否 | 业务域 ID |
| `inheritMode` | 否 | `NONE` / `CHILDREN` / `PARENT` / `BOTH`，默认 `NONE` |
| `checkDependency` | 否 | 是否同时校验依赖链，默认 `false` |
| `context` | 否 | 条件 handler / 自定义表达式使用的运行时上下文 |

兼容说明：

- 为兼容历史调用，请求仍接受旧字段 `abstractUserId`。
- 对外标准字段统一为 `userId`。

## Response

```json
{
  "granted": true,
  "denyReason": null,
  "grantedBy": [],
  "conflicts": [],
  "dependencyGaps": []
}
```

字段说明：

| 字段 | 说明 |
|------|------|
| `granted` | 是否通过 |
| `denyReason` | 拒绝原因：`NO_ROLE` / `NO_PERMISSION` / `CONDITION_FAIL` / `CONFLICT` / `DEPENDENCY_FAIL` |
| `grantedBy` | 命中的授权来源 |
| `conflicts` | 检测到的冲突明细 |
| `dependencyGaps` | 依赖缺口 |

## Error Codes

Phase 0 冻结错误码如下：

- `PERM-101`: 请求参数非法
- `PERM-102`: 资源不存在
- `PERM-103`: 操作不存在
- `PERM-104`: 资源类型与操作类型不匹配
- `PERM-105`: 域配置不允许
- `PERM-106`: 条件未审批通过
