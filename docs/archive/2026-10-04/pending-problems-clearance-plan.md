---
doc_type: plan
title: 问题清单转出批次（pending-problems 全量转出）
status: archived
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
  - T-PERM-104
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
acceptance: "24 任务全部终态；22 条来源问题（Q-014/015/018/021/022/023/024/027/028/029/031/032/037/038/040/043/044/045/047/048/049/050）随关联任务 done 且验收覆盖后移入 pending-problems 已收敛索引（立项后新登记的 Q-057 随追加卡 T-PERM-104 收敛，Q-056/Q-058 维持 open 随清单批次另行排期）；定案卡产出的拍板按 decision-registry 协议登记；拆出的后续实施卡（若定案超出单卡范围）从看板计数器取新号"
last_updated: 2026-10-04
---

# 问题清单转出批次

## 目标

按来源问题执行实现、验证或逐项定案，长期结论归权威设计；各任务范围和证据以任务卡为准。来源问题已移入 [已收敛索引](../../pending-problems.md)。

## 非目标

不含动态数据权限 T-PERM-036、租户开通/运营及版本打点；Q-056/Q-058 维持 open。定案后的 T-PERM-105、T-ADMIN-035、T-API-006、T-ACCESS-079/080 另立实施卡，不计入本计划，不声称已完成其代码交付。

## 准入条件

来源问题、任务验收及既有边界已登记；执行中出现的取舍均按具体场景确认并回写。

## 任务清单

状态与直接依赖见 [任务看板](../../tasks/README.md)，详情仅在任务载体。

### 授权正确性

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-PERM-096](tasks/T-PERM-096.md) | 资源复合键内存索引结构化元组化（共享解析与转授索引） | ✅ | — |
| [T-FE-060](tasks/T-FE-060.md) | 授权决策与展示键无歧义编码 | ✅ | — |
| [T-ADMIN-030](tasks/T-ADMIN-030.md) | 角色重新指派对既有绑定的语义收口 | ✅ | — |
| [T-PERM-097](tasks/T-PERM-097.md) | GROUP_ROLE 绑定面入口收紧——四入口拒绑组角色 | ✅ | — |
| [T-PERM-098](tasks/T-PERM-098.md) | 操作位写入与准入消费一致性边界定案与收口 | ✅ | — |
| [T-PERM-099](tasks/T-PERM-099.md) | 删除类型所有者角色引用守卫 | ✅ | — |
| [T-PERM-100](tasks/T-PERM-100.md) | sync 通道 codeType 归一与存量空白行处置 | ✅ | — |
| [T-PERM-104](tasks/T-PERM-104.md) | 用户角色分配/撤销入口角色定位键碰撞收口（入口 @Pattern + 键元组化） | ✅ | — |

### 管理面数据一致性

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-ADMIN-031](tasks/T-ADMIN-031.md) | 菜单 create/delete 补树写互斥 | ✅ | — |
| [T-ADMIN-032](tasks/T-ADMIN-032.md) | job cron 写前校验与调度失败可观测 | ✅ | — |
| [T-ORG-004](tasks/T-ORG-004.md) | 组织树配置根节点重叠守卫 | ✅ | — |
| [T-ADMIN-033](tasks/T-ADMIN-033.md) | 业务字段空值与长度校验对齐列宽 | ✅ | — |

### 前端展示与交互

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-FE-061](tasks/T-FE-061.md) | 停用主体授予入口状态与草稿保护 | ✅ | — |
| [T-ACCESS-065](tasks/T-ACCESS-065.md) | 树过滤完整性与状态口径逐子项定案修复 | ✅ | — |
| [T-FE-062](tasks/evidence/T-FE-062/verification.md) | 菜单种子图标离线注册补齐 | ✅ | — |

### 工程卫生

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-ACCESS-066](tasks/T-ACCESS-066.md) | 设计/契约/注释漂移九主题清扫（doc-only） | ✅ | — |
| [T-ACCESS-067](tasks/T-ACCESS-067.md) | 会话/网关时序用例固定 sleep 改造 | ✅ | — |
| [T-PERM-101](tasks/evidence/T-PERM-101/verification.md) | 主体组展开共享遍历参数化（行为等价重构） | ✅ | — |

### 设计定案与适配边界

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-ORG-005](tasks/T-ORG-005.md) | 组织/岗位动作码判定入口覆盖定案 | ✅ | — |
| [T-ADMIN-034](tasks/T-ADMIN-034.md) | OAuth2 委托链租户与用户状态校验定案 | ✅ | — |
| [T-API-005](tasks/T-API-005.md) | 可选字段显式清空协议逐域定案 | ✅ | — |
| [T-ACCESS-068](tasks/T-ACCESS-068.md) | 服务凭证覆盖运行时查询端点阶段二规划 | ✅ | — |
| [T-PERM-102](tasks/T-PERM-102.md) | 外部查询不开放 TRACE 的适配边界核验 | ✅ | — |
| [T-PERM-103](tasks/T-PERM-103.md) | grant_dep_id 保留列处置评估 | ✅ | — |

## 归档条件

计划内所有任务终态、设计回写和来源问题收敛完成，任务卡与证据随计划迁移，索引与相对链接自检通过。

## 当前进度

计划内 24 项全部 done。最终后端 `mvn test -T 1C` 2468 项零失败/错误/跳过，包含 E2E 与 heavy；前端 487 项及 typecheck/lint/build 通过。详情见 [最终验收](tasks/evidence/pending-problems-clearance/final-audit.md)。后续实施卡保留 proposed，范围与当前差异见任务看板。
