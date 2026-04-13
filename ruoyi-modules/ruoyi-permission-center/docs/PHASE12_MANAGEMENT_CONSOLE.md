# Phase 12: 管理端接口验收

## 验收状态：已完成（后端接口）

---

## 1. 输入依赖

- Phase 0-11 所有后端接口已就绪
- 17 张事实表与仓储层
- PermissionService 统一内核
- 精确鉴权、授权写入、条件系统、冲突治理、快照版本等全部链路

---

## 2. 输出接口

### 2.1 用户管理接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/perm/users/page` | POST | 分页查询用户 |
| `/api/perm/users/save` | POST | 保存用户 |
| `/api/perm/users/remove` | POST | 删除用户 |
| `/api/perm/users/{userId}/roles` | GET | 查询用户角色列表 |
| `/api/perm/users/{userId}/roles` | POST | 批量分配角色 |
| `/api/perm/users/{userId}/roles` | DELETE | 批量回收角色 |

### 2.2 角色管理接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/perm/roles/list` | POST | 查询角色列表 |
| `/api/perm/roles/save` | POST | 保存角色 |
| `/api/perm/roles/remove` | POST | 删除角色 |
| `/api/perm/roles/{roleId}/permissions` | GET | 查询角色权限列表 |
| `/api/perm/roles/{roleId}/permissions` | POST | 批量授权 |
| `/api/perm/roles/{roleId}/permissions` | DELETE | 批量回收权限 |

### 2.3 资源管理接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/perm/resources/list` | POST | 查询资源列表 |
| `/api/perm/resources/save` | POST | 保存资源 |
| `/api/perm/resources/remove` | POST | 删除资源 |

### 2.4 操作权限接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/perm/operations/list` | POST | 查询操作列表 |
| `/api/perm/operations/save` | POST | 保存操作 |
| `/api/perm/operations/remove` | POST | 删除操作 |

### 2.5 业务域接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/perm/domains/page` | POST | 分页查询业务域 |
| `/api/perm/domains/save` | POST | 保存业务域 |
| `/api/perm/domains/remove` | POST | 删除业务域 |

### 2.6 域配置接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/perm/domain-scope/list` | POST | 查询域范围配置 |
| `/api/perm/domain-scope/save` | POST | 保存域范围配置 |
| `/api/perm/domain-scope/remove` | POST | 删除域范围配置 |
| `/api/perm/domain-relation/list` | POST | 查询域关系配置 |
| `/api/perm/domain-relation/save` | POST | 保存域关系配置 |
| `/api/perm/domain-relation/remove` | POST | 删除域关系配置 |
| `/api/perm/domain-binding/list` | POST | 查询域引用绑定 |
| `/api/perm/domain-binding/save` | POST | 保存域引用绑定 |
| `/api/perm/domain-binding/remove` | POST | 删除域引用绑定 |

### 2.7 条件管理接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/perm/conditions` | GET | 查询条件列表 |
| `/api/perm/conditions` | POST | 创建条件 |
| `/api/perm/conditions/{conditionId}` | PUT | 更新条件（审核/启停） |
| `/api/perm/conditions/list` | POST | 查询条件列表（兼容） |
| `/api/perm/conditions/save` | POST | 保存条件（兼容） |
| `/api/perm/conditions/remove` | POST | 删除条件（兼容） |

### 2.8 冲突治理接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/perm/conflict-rules` | GET | 查询冲突规则列表 |
| `/api/perm/conflict-rules` | POST | 保存冲突规则 |
| `/api/perm/conflict-rules` | DELETE | 删除冲突规则 |
| `/api/perm/conflict-rules/list` | POST | 查询冲突规则列表（兼容） |
| `/api/perm/conflict-rules/save` | POST | 保存冲突规则（兼容） |
| `/api/perm/conflict-rules/remove` | POST | 删除冲突规则（兼容） |
| `/api/perm/conflict-rules/detect` | POST | 冲突检测（全量） |
| `/api/perm/conflict-detection` | POST | 冲突检测（分页） |

### 2.9 依赖管理接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/perm/resource-dependencies` | GET | 查询依赖列表 |
| `/api/perm/resource-dependencies` | POST | 保存依赖 |
| `/api/perm/resource-dependencies` | DELETE | 删除依赖 |
| `/api/perm/resource-dependencies/list` | POST | 查询依赖列表（兼容） |
| `/api/perm/resource-dependencies/save` | POST | 保存依赖（兼容） |
| `/api/perm/resource-dependencies/remove` | POST | 删除依赖（兼容） |
| `/api/perm/resource-dependencies/graph` | GET/POST | 查询依赖图 |
| `/api/perm/resource-dependencies/check` | POST | 依赖检查 |

### 2.10 审计接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/perm/change-logs` | POST | 分页查询变更记录 |

### 2.11 类型定义接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/perm/type-definitions/list` | POST | 查询类型定义列表 |
| `/api/perm/type-definitions/save` | POST | 保存类型定义 |
| `/api/perm/type-definitions/remove` | POST | 删除类型定义 |

### 2.12 权限服务接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/perm/service/grant` | POST | 单条授权 |
| `/api/perm/service/revoke` | POST | 单条回收 |
| `/api/perm/service/snapshot` | POST | 构建快照 |
| `/api/perm/service/version` | POST | 查询版本 |

---

## 3. 验收记录

### 3.1 定向回归

```bash
mvn -pl ruoyi-modules/ruoyi-permission-center -am test -DskipTests=false -Pdev
```

**结果**：Tests run: 293, Failures: 0, Errors: 0, Skipped: 2

### 3.2 关键验证点

- [x] 用户管理接口完整（查询、保存、删除）
- [x] 用户角色分配/回收接口完整（支持有效期）
- [x] 角色管理接口完整（查询、保存、删除）
- [x] 角色权限授权/回收接口完整（支持 can_manage、condition_id）
- [x] 资源管理接口完整
- [x] 操作权限管理接口完整
- [x] 业务域管理接口完整
- [x] 域范围/关系/绑定配置接口完整
- [x] 条件管理接口完整（创建、审核、启停）
- [x] 冲突规则管理接口完整
- [x] 冲突检测接口完整（全量 + 分页）
- [x] 资源依赖管理接口完整
- [x] 依赖图查询接口完整
- [x] 依赖检查接口完整
- [x] 审计查询接口完整（多维度过滤）
- [x] 类型定义接口完整

---

## 4. 接口规范

### 4.1 统一请求格式

- 所有查询接口使用 POST + JSON
- 分页查询返回 `TableDataInfo<T>`
- 列表查询返回 `R<List<T>>`
- 单条操作返回 `R<Void>` 或 `R<T>`

### 4.2 统一响应格式

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": { ... }
}
```

### 4.3 分页响应格式

```json
{
  "code": 200,
  "msg": "操作成功",
  "rows": [ ... ],
  "total": 100
}
```

---

## 5. 前端开发指南

### 5.1 前端项目

- 项目地址：https://gitee.com/JavaLionLi/plus-ui
- 技术栈：Vue 3 + TypeScript + Element Plus
- 状态管理：Pinia
- HTTP 客户端：Axios

### 5.2 接口调用基址

- 前端请求基址：`/permission-center`
- 接口路径前缀：`/api/perm`

### 5.3 页面结构建议

```
src/views/permission/
├── user/              # 用户管理
│   ├── index.vue
│   └── components/
│       ├── RoleAssignDialog.vue
│       └── ChangeLogDialog.vue
├── role/              # 角色管理
│   ├── index.vue
│   └── components/
│       ├── PermissionConfig.vue
│       └── PermissionPreview.vue
├── resource/          # 资源管理
│   └── index.vue
├── domain/            # 域配置
│   ├── index.vue
│   ├── ScopeConfig.vue
│   ├── RelationConfig.vue
│   └── BindingConfig.vue
├── condition/         # 条件管理
│   ├── index.vue
│   └── components/
│       ├── ConditionForm.vue
│       └── ConditionAudit.vue
├── conflict/          # 冲突治理
│   ├── index.vue
│   └── Detection.vue
├── dependency/        # 依赖管理
│   ├── index.vue
│   ├── Graph.vue
│   └── Check.vue
└── audit/             # 审计日志
    ├── index.vue
    └── components/
        └── SnapshotDetail.vue
```

---

## 6. 风险与未决问题

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 前端项目独立开发 | 需要单独克隆和配置 | 提供完整的接口文档和类型定义 |
| 树形数据量大 | 前端性能问题 | 建议实现懒加载 |
| 依赖图可视化复杂 | 开发成本高 | 建议使用 G6 或 D3.js |
| 条件表达式编辑 | 用户体验要求高 | 建议使用 Monaco Editor |

---

## 7. 后续优化方向

1. **前端页面开发**：基于本阶段提供的接口，开发完整的管理界面
2. **接口文档完善**：生成 OpenAPI/Swagger 文档
3. **性能优化**：大数据量场景下的分页和查询优化
4. **监控指标**：暴露管理端操作审计指标

---

## 8. 相关文件

| 文件 | 说明 |
|------|------|
| `AbstractUserController.java` | 用户管理接口 |
| `UserRoleContractController.java` | 用户角色接口 |
| `AbstractRoleController.java` | 角色管理接口 |
| `RolePermissionContractController.java` | 角色权限接口 |
| `ResourceEntityController.java` | 资源管理接口 |
| `OperationPermissionController.java` | 操作权限接口 |
| `BizDomainController.java` | 业务域接口 |
| `DomainScopeConfigController.java` | 域范围配置接口 |
| `DomainRelationConfigController.java` | 域关系配置接口 |
| `DomainScopeBindingController.java` | 域引用绑定接口 |
| `PermissionConditionController.java` | 条件管理接口 |
| `ConflictRuleController.java` | 冲突规则接口 |
| `ConflictDetectionController.java` | 冲突检测接口 |
| `ResourceDependencyController.java` | 资源依赖接口 |
| `PermissionChangeLogController.java` | 变更记录接口 |
| `TypeDefinitionController.java` | 类型定义接口 |
| `PermissionServiceController.java` | 权限服务接口 |
