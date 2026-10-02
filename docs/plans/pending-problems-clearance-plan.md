---
doc_type: plan
title: 问题清单转出批次（pending-problems 全量转出）
status: active
domain: cross-service
design_refs:
  - docs/design/access-service-api-contract.md
  - docs/design/engine/implementation.md
  - docs/design/engine/core-flows.md
  - docs/design/access-service-architecture.md
  - docs/design/default-org-tree-user-lifecycle.md
  - docs/design/dependency-auto-grant.md
  - docs/design/service-authentication.md
  - docs/design/org-user-permission-contract.md
  - docs/design/frontend/permission-grant.md
tasks:
  - T-PERM-096
  - T-PERM-097
  - T-PERM-098
  - T-PERM-099
  - T-PERM-100
  - T-PERM-101
  - T-PERM-102
  - T-PERM-103
  - T-ADMIN-030
  - T-ADMIN-031
  - T-ADMIN-032
  - T-ADMIN-033
  - T-ADMIN-034
  - T-ORG-004
  - T-ORG-005
  - T-FE-060
  - T-FE-061
  - T-FE-062
  - T-ACCESS-065
  - T-ACCESS-066
  - T-ACCESS-067
  - T-ACCESS-068
  - T-API-005
acceptance: "23 任务全部终态；22 条来源问题（Q-014/015/018/021/022/023/024/027/028/029/031/032/037/038/040/043/044/045/047/048/049/050）随关联任务 done 且验收覆盖后移入 pending-problems 已收敛索引；定案卡产出的拍板按 decision-registry 协议登记；拆出的后续实施卡（若定案超出单卡范围）从看板计数器取新号"
last_updated: 2026-10-01
---

# 问题清单转出批次（pending-problems 全量转出）

## 目标

把 [docs/pending-problems.md](../pending-problems.md) 全部 22 条 open 问题（2026-09-30 合并精练后的存量）转出为可执行任务，按「授权正确性 / 管理面一致性 / 前端展示 / 工程卫生 / 设计定案」五组编排。方案边界清楚的直接修复；方案未定的立定案卡（产出=拍板+契约/规范修订，实施超出单卡范围时另立新 ID）。

- Q-044 拆前后端两张卡（T-PERM-096 后端元组化、T-FE-060 前端键编码），修法面独立、可并行。
- 每张卡「背景」回链 Q-ID；pending-problems 条目转 `converted` 并填关联。

## 非目标

- 不含 T-PERM-036（动态数据权限，等 PM 重申，已有载体）与租户开通/运营（2026-09-17 已停）等清单外暂缓项。
- 定案卡（组五）不承诺实施：Q-043/Q-032 等拍板后的实施范围若超出单卡，另立新任务，不并入定案卡；例外=T-PERM-102（Q-045 暂缓安排的兑现）为「定案+实施」卡，验收含门禁实施与回归锁，占实施位。
- 不含 v0.2.0 版本打点（独立决策，不在本计划）。

## 准入条件

已满足（2026-10-01 立项）：22 条问题均经 2026-09-30 外部逻辑报告核实与合并精练，现象与证据锚点在册。

## 任务清单

状态快照以 [tasks/README.md](../tasks/README.md) 看板为唯一权威；依赖以各卡 frontmatter `depends_on` 为准。

### 组一 授权正确性（权限面）

| ID | 标题 | 来源 | 状态 | 直接依赖 |
|---|---|---|---|---|
| T-PERM-096 | 资源复合键内存索引结构化元组化（共享解析与转授索引） | Q-044 后端半边 | ⚙️ | — |
| T-FE-060 | 授权决策与展示键无歧义编码 | Q-044 前端半边 | ⚙️ | — |
| T-ADMIN-030 | 角色重新指派对既有绑定的语义收口 | Q-047 | ⚙️ | — |
| T-PERM-097 | GROUP_ROLE 绑定面入口收紧——四入口拒绑组角色（2026-10-02 改卡：原「新增侧展开」场景不可达） | Q-027 | ✅ | — |
| T-PERM-098 | 操作位写入与准入消费一致性边界定案与收口 | Q-050 | ⚙️ | — |
| T-PERM-099 | 删除类型所有者角色引用守卫 | Q-023 | ⚙️ | — |
| T-PERM-100 | sync 通道 codeType 归一与存量空白行处置 | Q-031 | ⚙️ | — |

### 组二 管理面数据一致性

| ID | 标题 | 来源 | 状态 | 直接依赖 |
|---|---|---|---|---|
| T-ADMIN-031 | 菜单 create/delete 补树写互斥 | Q-048 | ⚙️ | — |
| T-ADMIN-032 | job cron 写前校验与调度失败可观测 | Q-049 | ⚙️ | — |
| T-ORG-004 | 组织树配置根节点重叠守卫 | Q-024 | ⚙️ | — |
| T-ADMIN-033 | 业务字段空值与长度校验对齐列宽 | Q-018 | ⚙️ | — |

### 组三 前端展示与交互

| ID | 标题 | 来源 | 状态 | 直接依赖 |
|---|---|---|---|---|
| T-FE-061 | 停用主体授予入口状态与草稿保护 | Q-037 | ⚙️ | — |
| T-ACCESS-065 | 树过滤完整性与状态口径逐子项定案修复 | Q-038 | ⚙️ | — |
| T-FE-062 | 菜单种子图标离线注册补齐 | Q-021 | ⚙️ | — |

### 组四 工程卫生

| ID | 标题 | 来源 | 状态 | 直接依赖 |
|---|---|---|---|---|
| T-ACCESS-066 | 设计/契约/注释漂移八主题清扫（doc-only） | Q-015 | ⚙️ | — |
| T-ACCESS-067 | 会话/网关时序用例固定 sleep 改造 | Q-014 | ⚙️ | — |
| T-PERM-101 | 主体组展开共享遍历参数化（行为等价重构） | Q-028 | ⚙️ | — |

### 组五 设计定案（产出=拍板+契约/规范修订；例外 T-PERM-102 含门禁实施与回归锁）

| ID | 标题 | 来源 | 状态 | 直接依赖 |
|---|---|---|---|---|
| T-ORG-005 | 组织/岗位动作码判定入口覆盖定案 | Q-032 | ⚙️ | — |
| T-ADMIN-034 | OAuth2 委托链租户与用户状态校验定案 | Q-029 | ⚙️ | — |
| T-API-005 | 可选字段显式清空协议逐域定案 | Q-043 | ⚙️ | — |
| T-ACCESS-068 | 服务凭证覆盖运行时查询端点阶段二规划 | Q-040 | ⚙️ | — |
| T-PERM-102 | TRACE 诊断输出授权门禁（定案+实施+回归锁，占实施位） | Q-045 | ⚙️ | — |
| T-PERM-103 | grant_dep_id 保留列处置评估 | Q-022 | ⚙️ | — |

## 归档条件

23 任务全部 done/cancelled；来源问题随卡收敛（pending-problems 移入已收敛索引）；三处 README 与索引链接同步；归档自检通过。

## 当前进度

- 2026-10-01 立项：23 任务建卡（21 独立卡 + T-FE-062/T-PERM-101 看板行），22 条问题 open→converted。
- 2026-10-01 T-PERM-096 + T-FE-060 收口（组一首两张，并行起步完成）：Q-044 随两卡收敛（closed，移入已收敛索引）；类推清扫退役 BusinessKeyUtil 第四个死方法 resourceTripleValueKey；前端顺带修 changeGroupKey（inlineName 自由文本 `|` 碰撞）；apiRouteResourceKey（path 中段自由文本）理论碰撞面用户拍板登记 Q-056（随清单批次排期，不占本计划任务位）。
- 2026-10-02 T-PERM-097 收口（组一第三张，Q-027 收敛）：实施核实原验收「新增侧展开子树」无可达场景（T-PERM-043 后无 GROUP_ROLE 绑定行写入方，现行活口产生的行写守卫与运行时一致不展开、无 Y 失效危害），用户拍板改卡为「绑定面入口收紧」——assign/batch-assign（值层双保险）+ sync/full-sync（scope 级）四入口拒绑 GROUP_ROLE（20022），持有侧/运行时组展开零改动；原「新增侧展开」设想随 role_inclusion 单事实源立项（T-PERM-043 双事实源技术债）另行处理。
- 建议顺序：组一先行（T-PERM-096 ∥ T-FE-060 并行起步），组二/组三随后可并行；组五定案卡可随时穿插（T-PERM-102 除外——含门禁实施与回归锁，按实施卡排期；其余定案卡拍板后视范围另立实施卡）；组四收尾（Q-015 doc-only 清扫放最后，避免与实施批同文件冲突）。
