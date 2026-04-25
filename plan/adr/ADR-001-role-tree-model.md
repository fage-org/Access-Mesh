# ADR-001：角色树形一体化模型

**状态**：✅ 已采纳

**日期**：2026-04-24

**上下文**

原始设计使用 `role_group` + `role_group_role` 两张独立表管理角色分组和角色关联，共 20 张表。
问题：分组概念与角色概念割裂，增加了表数量和关联复杂度。

**决策**

去除 `role_group` 和 `role_group_role` 表，直接在 `abstract_role` 中通过 `parent_id` 实现树形结构。

**角色类型行为差异**：

| 类型 | 层级 | 可配权限 | 子级 |
|------|------|----------|------|
| ORG(1) | 树形 | 是 | 可 |
| POSITION(2) | 平铺 | 是 | 否 |
| PERSONAL(3) | 平铺 | 是 | 否 |
| GROUP_ROLE(5) | 树形 | **否** | 可 |
| BASIC_ROLE(6) | 平铺 | 是 | 否 |

**后果**

- 表数从 20 张减少到 18 张
- `abstract_role` 新增 `parent_id` 字段
- `user_role` 新增 `relation_id` 字段（POSITION 类型时记录组织归属）
- `user_role.target_type` 扩展为 ROLE/ORG/POSITION/PERSONAL/GROUP_ROLE
- GROUP_ROLE 不直接配置权限，通过 `extra.basicRoleIds` 额外关联基本角色
