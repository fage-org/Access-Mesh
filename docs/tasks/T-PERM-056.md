---
doc_type: task
id: T-PERM-056
title: user_type/role_type 删除零检查——主体/角色类型引用面保护（T-PERM-050 盘点拆分）
status: proposed
plan: ""
domain: permission-center
design_refs:
  - docs/design/schema/access-service.sql
  - docs/design/permission-center/api-contract.md#§5.1
depends_on: []
blocks: []
acceptance:
  - "背景（T-PERM-050 盘点发现，2026-09-09 用户拍板拆分登记）：deleteTypesByIds 对自定义 user_type/role_type 删除零检查零级联——abstract_user.user_type / abstract_role.role_type 以 type_value 引用类型定义，删除被引用类型后该类型用户/角色行反解缺项（管理视图不可达、无清理入口），与 T-PERM-050 同性质的孤儿面"
  - "修法方向执行时决策：删除保护（存在引用该 typeValue 的有效 abstract_user/abstract_role 行时拒绝删除，对齐 resource_type 行数守卫先例 20056）/ 级联 / 其他；role_type 侧 ORG/POSITION 等种子类型 isSystem=true 本就不可删，面仅限自定义角色类型"
  - "回归锁（旧实现下失败的用例）+ schema 注释 + api-contract §5.1 语义回写"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-09
---

# T-PERM-056 user_type/role_type 删除零检查——主体/角色类型引用面保护

> 状态：proposed（2026-09-09 T-PERM-050 引用面盘点拆分登记）
> 依赖：无硬依赖

## 背景

T-PERM-050 盘点发现：`TypeDefinitionAppServiceImpl#deleteTypesByIds` 的引用检查仅覆盖 `typeKey=resource_type`（行数守卫 + 操作/授权级联）。自定义 `user_type`/`role_type` 删除同样零检查——例：建自定义用户类型「CONTRACTOR」并创建该类型用户后删除类型，用户的 `user_type` 指向已软删类型值，批量反解缺项、管理视图不可达。与 T-PERM-050 根治的 resource_type 孤儿面同性质，但主题不同（主体/角色类型引用面），2026-09-09 用户拍板拆分登记。

## 范围

- user_type：`abstract_user.user_type` 引用面检查（删除保护或级联，执行时决策）。
- role_type：`abstract_role.role_type` 引用面检查（同上；种子角色类型 isSystem 不可删，面仅限自定义角色类型）。
- 存量孤儿订正语句登记（如适用）。
