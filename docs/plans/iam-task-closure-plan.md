---
doc_type: plan
title: IAM 核心正确性与用户任务闭环
status: active
domain: cross-service
design_refs:
  - docs/design/iam-task-closure.md
  - docs/design/dependency-auto-grant.md
tasks:
  - T-ORG-002
  - T-ADMIN-028
  - T-PERM-074
  - T-PERM-075
  - T-ORG-003
  - T-PERM-076
  - T-FE-057
  - T-FE-058
  - T-API-004
  - T-ADMIN-029
  - T-FE-059
  - T-GW-010
  - T-PERM-077
  - T-ACCESS-052
  - T-ACCESS-053
  - T-PERM-078
  - T-ACCESS-054
  - T-ACCESS-055
acceptance: "所属任务完成或有明确取消依据，核心用户任务验收有实际证据，权威设计回写和旧任务衔接闭合；未交付能力不写为当前可用。"
last_updated: 2026-09-21
---

# IAM 核心正确性与用户任务闭环计划

## 目标

围绕[评审证据](../archive/2026-09-20/comprehensive-review.md)修复核心正确性、打通有限职责管理、补齐页面生命周期、简化首次接入，并评估待实施机制的必要性。总体方案见[设计稿](../design/iam-task-closure.md)，已采纳的自动授权方向以[独立设计](../design/dependency-auto-grant.md)为准，原子范围与验收在任务卡。

## 非目标

本计划按任务逐项实施，不启动真实数据恢复或部署；自动授权简化方向已采纳，其他 draft 候选不随之自动采纳。自动授权沿T-PERM-071～073原序列承接；T-PERM-036/054继续维持原启动门禁。其他已知Q事项沿原载体，不因本计划重复立项。

## 准入条件

每张卡进入in-progress前核对现行代码/定案，解决该卡对应U项并确定契约；不要求无关任务等待全部取舍。事实性小修可先推进，设计选择必须形成具体角色、数据例、后果与迁移范围。

编排先处理正确性，再完成有限管理和生命周期，最后做组合验收。表中只列直接硬依赖；无依赖的任务可独立推进，共用前端组件/契约编辑需协调文件所有权。跨任务设计变化必须回查任务依赖，不能只更新设计稿。

## 任务清单

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-ORG-002](../tasks/T-ORG-002.md) | 默认身份目录删除与恢复边界闭合 | ✅（2026-09-21 收口） | — |
| [T-ADMIN-028](../tasks/T-ADMIN-028.md) | OAuth2 授权码客户端关联校验 | ⚙️ | — |
| [T-PERM-074](../tasks/T-PERM-074.md) | 同步失败与版本记账事务一致性 | ✅ | — |
| [T-PERM-075](../tasks/T-PERM-075.md) | 互斥角色有效期与判定入口一致性 | ⚙️ | — |
| [T-ORG-003](../tasks/T-ORG-003.md) | 组织与岗位成员候选门禁统一 | ⚙️ | — |
| [T-PERM-076](../tasks/T-PERM-076.md) | 资源批量创建复合身份一致性 | ⚙️ | — |
| [T-FE-057](../tasks/T-FE-057.md) | 岗位停用后可发现与恢复 | ⚙️ | — |
| [T-FE-058](../tasks/T-FE-058.md) | 岗位与成员角色候选分页闭合 | ⚙️ | — |
| [T-API-004](../tasks/T-API-004.md) | 可编辑字段显式清空协议贯通 | ⚙️ | — |
| [T-ADMIN-029](../tasks/T-ADMIN-029.md) | 公告状态与受众生命周期闭合 | ⚙️ | — |
| [T-FE-059](../tasks/T-FE-059.md) | 共享列表上下文与可写对象绑定 | ⚙️ | — |
| [T-GW-010](../tasks/T-GW-010.md) | 开发与代理拓扑的 Origin 接入一致性 | ⚙️ | — |
| [T-PERM-077](../tasks/T-PERM-077.md) | 操作继承掩码缺省值契约对齐 | ⚙️ | — |
| [T-ACCESS-052](../tasks/T-ACCESS-052.md) | 实例委派的目录菜单与管理任务闭环 | ⚙️ | T-ORG-003 |
| [T-ACCESS-053](../tasks/T-ACCESS-053.md) | 首次服务接入与撤销验证路径简化 | ⚙️ | T-GW-010 |
| [T-PERM-078](../tasks/T-PERM-078.md) | 自动授权实施前协议与算法校准 | ✅ | — |
| [T-ACCESS-054](../tasks/T-ACCESS-054.md) | 外围任务能力与缓存过渡机制取舍 | ⚙️ | — |
| [T-ACCESS-055](../tasks/T-ACCESS-055.md) | 核心用户任务组合验收与文档收口 | ⚙️ | T-ORG-002, T-ADMIN-028, T-PERM-074, T-PERM-075, T-PERM-076, T-FE-057, T-FE-058, T-API-004, T-ADMIN-029, T-FE-059, T-PERM-077, T-ACCESS-052, T-ACCESS-053 |

既有任务衔接（仅引用，不属于本计划tasks，不重复计完成度）：

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-PERM-071](../tasks/T-PERM-071.md) | 独立依赖声明与可选 SDK 协调 | ✅ | T-PERM-070, T-PERM-078, T-PERM-074 |
| [T-PERM-072](../tasks/T-PERM-072.md) | 自动授权物化与共享推导 | ✅（2026-09-21 收口） | T-PERM-071 |
| [T-PERM-073](../tasks/T-PERM-073.md) | 按需来源解释、授权界面与对账 | ✅（2026-09-21 收口） | T-PERM-072 |
| [T-PERM-036](../tasks/T-PERM-036.md) | 动态数据权限端到端验证 | ⚙️（暂缓） | T-FE-013, T-PERM-033 |
| [T-PERM-054](../tasks/T-PERM-054.md) | API映射与业务操作权限关联 | ⚙️（暂缓） | — |

## 归档条件

本计划tasks全部done/cancelled，实际采纳的设计已回写，所有待决项有结论或明确承接，验收证据能追溯。不得因外部关联后续任务（如自动授权 072/073 物化与解释）尚未实施而谎称它们完成；本计划仅要求本批方案校准与衔接完成。按生命周期规则将本计划及所属卡随迁归档，更新索引。

## 当前进度

计划已 active，T-PERM-074、T-PERM-078、T-ORG-002 已 done（T-ORG-002 2026-09-21 收口：U001 拒绝+人数/树配置最小面+set-default 严格判定三项拍板见 registry 同日行；双轨评审全处置），衔接的自动授权序列 T-PERM-071/072/073 已全部收口（2026-09-21，含 T-PERM-079 风格清理单卡归档），其余任务状态见任务清单。自动授权简化方向已 adopted，078 已完成设计 M1～M5 校准，072/073 验收已衔接；其他任务待决项维持原安排。已有问题/在途事项的去重与承接见[评审证据映射](../archive/2026-09-20/comprehensive-review.md)。
