# 2026-06 归档批次：permission-center v3.0~v3.3 设计演进

> 归档日期：2026-06-18
> 替代文档：[../../design/permission-center-v3.5-design.md](../../design/permission-center-v3.5-design.md)（v3.5 简化版，`status: adopted`；v3.4 已被 v3.5 取代，仅通过 git history 追溯）

## 归档原因

v3.0~v3.3 在迭代过程中暴露**双系统反模式**——`admin/sys_menu.operations` 等字段实际承担权限语义，与 `permission-center` 形成影子双系统。R3 评估（2026-06-18）三角色（架构师/安全/PM）一致认定 v3.4 单源派生方向正确，本批次进入归档。

## 设计演进时间线

| 版本 | 日期 | 核心范式 | 关键变化 |
|---|---|---|---|
| v1（example-service-integration）| 2026-06-17 | 双轨并行（无显式 AND/OR）| 业务概念清单 + 信息流图 + 6 决策点 |
| v2（menu-business-perm-alignment）| 2026-06-17 | 节点类型分治 + 双轨 AND + entryPermCodes JSONB 多值 | 菜单元数据化首次提出 |
| v3 合并版 | 2026-06-17 | 双轨 AND + 多租户硬隔离 + 一致性总线 | 9 角色 R1→R2 评估，6 大范式定型 |
| v3.1 | 2026-06-18 | SIMPLIFY：1:1 报表-菜单约束 | 9/9 全票一致；恢复唯一索引；字段级 MOVE_TO_V2_1 |
| v3.2 | 2026-06-18 | 菜单元数据化业务能力清单 | sys_menu.operations + primary_operation 字段化 |
| v3.3 | 2026-06-18 | 去 manifest 化 + 字典 admin 中心化 | OperationPermission 字典 + 业务服务零启动耦合 |
| **v3.4** | **2026-06-18** | **菜单零权限化 / 单源派生** | **删除 ADMIN_MENU 资源类型 + sys_menu.operations 字段；is_entry 取代 primary_operation** |

## P1~P14 范式 OBSOLETED 关系图

| 范式 ID | v3.x 出处 | OBSOLETED 原因 | v3.4 替代位置 |
|---|---|---|---|
| P1 唯一索引 / perm_code 多值 | v3.0 §1.2 | 1:1 约束下 perm_code 无独立存在必要 | v3.4 §2 schema（仅 resource_type+resource_code 联合唯一索引）|
| P2 字段级 MVP（IR-6.3-A~E） | v3.0 §6.3 | A/B/E 与 1:1 冲突 | v3.4 §3 字段级契约（IR-6.3-C/D/F 沿用 + is_entry 新增）|
| P3 依赖规则双 SSOT | v3.0 §3.1 | manifest 概念物理消失 | v3.4 §2（仅 ADMIN_UI 来源；MENU_PUBLISH_DERIVED 和 MANIFEST 全部废弃）|
| P4 前端 permissions 对象数组 | v3.0 §12.2 | KEEP（与 1:1 / 单源派生正交） | v3.4 §5 单 RPC 原子返回 |
| P5 一致性总线（Outbox + Kafka）| v3.0 §12 | KEEP | v3.4 §9 性能与一致性 |
| P6 AUTO_DEP 巡检 E-4b-1 | v3.0 §3.3 / §11 | KEEP | v3.4 §10 迁移计划 + §14 风险监控 |
| P7 perm_code 二段式 | v3.2 §1.2 | 1:1 约束下退化为 resource_type+resource_code 直接表达 | v3.4 §2 schema |
| P8 菜单 operations 元数据化 | v3.2 §1.2 | 双系统反模式 | v3.4 §3（is_entry 在字典层守门）|
| P9 primary_operation + sensitivity 守门 | v3.2 §1.1 | 守门下沉到操作位字典 is_entry | v3.4 §3 字段级契约 |
| P10 saveMenu N 条派生 | v3.2 §3.1.2 | 派生消失（单源派生不需要 dependency 行）| v3.4 §2 schema |
| P11 manifest 物理消失 | v3.3 §13.2 | 沿用并强化 | v3.4 §1 设计原则（业务服务零启动耦合）|
| P12 effective_bits 位运算覆盖业务依赖 | v3.3 §13.2 | 沿用 | v3.4 §3 + §6 审计 |
| P13 更新发布无审核 | v3.3 §13.2 | 退化（单源派生下"菜单更新"无 operations 字段可改） | v3.4 §7 授权 UX（按菜单视图糖）|
| P14 操作位字典 admin 中心化 | v3.3 §17 | 沿用并强化（is_entry 入字典）| v3.4 §3 + §12 SDK 接入 |

## 决策点演进

| ID | v3.0~v3.3 拍板 | v3.4 状态 |
|---|---|---|
| D1~D5 | 已采纳（v1 决策）| 沿用 |
| D6 字段级 v2 GA → v2.1 | 已采纳 v3.1 | 沿用 |
| D7~D10 | v3.1 已采纳 | 部分调整（D7 双轨 → 单源；D8 entryPermCodes → 直接派生）|
| D-A~D-G | v3.3 已采纳 | 沿用基础（字典 admin 维护、不推送、复用 URL 协议）|
| D-H~D-K | v3.3 待拍板 | OBSOLETED（v3.4 下不存在 operations 字段，相关决策无意义）|
| D-L~D-O | v3.4 已拍板 | 进入 v3.4 主文档 |

## 文件清单

- `permission-center-v3.0-v3.3-design-2026-06-17.md` — 合并历史快照（顶部带 OBSOLETED banner）

## 强制规则

1. 新人 Onboarding（CLAUDE.md / AGENTS.md）只指向 v3.5 简化版主文档，本目录禁止入索引（v3.4 已被 v3.5 取代，仅通过 git history 追溯）
2. v3.4 文档每章末尾如涉及范式切换，加 "← 取代 vX.X §Y P\<N\>" 标注
3. 法务/合规签字记录（双轨 AND → 操作可追溯话术切换）作为 v3.4 §15 附件存档，不进入归档目录

---

> 2026-09-12 迁移注记：本批次原侧挂于 `docs/plans/archive/2026-06/`，按归档统一落点规范（design-plan-task-lifecycle skill §6.5）并入 `docs/archive/2026-06-18/`；内容与内部链接未变。
