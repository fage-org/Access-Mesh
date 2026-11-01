---
doc_type: plan
title: 架构边界修复与维护复杂度收敛
status: proposed
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/engine/implementation.md
  - docs/design/access-service-api-contract.md
  - docs/design/project-rules.md
tasks:
  - T-PERM-113
  - T-FE-081
  - T-PERM-114
  - T-PERM-115
  - T-ACCESS-151
  - T-ACCESS-152
  - T-ACCESS-153
  - T-FE-082
  - T-FE-083
  - T-ACCESS-154
  - T-ACCESS-155
  - T-PERM-116
  - T-ACCESS-156
  - T-PERM-117
  - T-ADMIN-036
  - T-PERM-118
  - T-PERM-119
  - T-PERM-120
  - T-PERM-121
  - T-ACCESS-157
  - T-ADMIN-037
  - T-FE-084
  - T-FE-085
  - T-ACCESS-158
  - T-ACCESS-159
acceptance: "原报告全部建议有明确去向；确认方案完成适用实施与验证，未决项经用户逐项确定，设计回写、联合验收及索引一致后归档。"
last_updated: 2026-11-01
---

# 架构边界修复与维护复杂度收敛计划

## 目标

对原报告（外评快照按定规不入库）涉及的问题与建议建立完整跟踪，按[范围映射](../tasks/evidence/architecture-boundary-remediation/scope-map.md)定位责任任务。先处理明确边界问题，再凭实际证据决定结构优化；全部纳入跟踪不表示全部建议均已成立或获准实施。结构建议采用已确认的核查先行安排：证据齐后逐项交用户确认，需要实施才追加实现卡；维持现状或暂缓也须用户明确决定。

## 非目标

本次仅交付计划、任务与已经确认的目标设计，不修改生产代码、测试、可执行 DDL 或部署配置。保留各权威文档中的当前支持范围；现有暂缓事项不因进入本计划自动重启，不将规划、核查或历史通过数写成修复完成。

## 准入条件

- 实施前读取[定案入口](../design/decision-registry.md)、任务 design_refs、适用 Rules/Skills 与有效例外，复查工作树增量及消费方。
- 计划保持 proposed；当前授权为制定计划，后续执行需用户明确启动。缓存、查询观测及审计的已确认目标按[服务架构](../design/access-service-architecture.md#operation-catalog-consistency)对应主题消费。
- 每项待定取舍逐项向用户展示真实场景、数据、后果与推荐/备选；答复前不进入相关实现，不把沉默、原报告建议或 agent 倾向当作确认。
- 先执行核查/定稿卡，再登记获准的实现卡并重连依赖。已有问题/任务回链，避免为 Q-060、Q-068 或已受支持边界重复立项。
- 任务验收仅采用适用性覆盖；优先复用现有测试和 ItInfra，不默认新增框架、笛卡尔积测试或每卡全量回归。

## 任务清单

唯一清单及状态见[任务看板](../tasks/README.md)，详细范围与待定门槛只在任务卡。

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-PERM-113](../tasks/T-PERM-113.md) | 操作定义读取统一与掩码缓存退役 | ⚙️ | — |
| [T-FE-081](../tasks/T-FE-081.md) | 公共分页失败后的页码与数据一致性 | ⚙️ | — |
| [T-PERM-114](../tasks/T-PERM-114.md) | 查询预算退役范围与日志口径定稿 | ⚙️ | — |
| [T-PERM-115](../tasks/T-PERM-115.md) | 内部计算预算改为执行观测日志 | ⚙️ | T-PERM-114 |
| [T-ACCESS-151](../tasks/T-ACCESS-151.md) | 审计饱和与事务连接占用验证 | ⚙️ | — |
| [T-ACCESS-152](../tasks/T-ACCESS-152.md) | 判定证据提交饱和策略修复 | ⚙️ | T-ACCESS-151 |
| [T-ACCESS-153](../tasks/T-ACCESS-153.md) | 现役架构规则与交付状态订正 | ⚙️ | — |
| [T-FE-082](../tasks/T-FE-082.md) | 零消费存储与打印工具裁剪 | ⚙️ | — |
| [T-FE-083](../tasks/T-FE-083.md) | 依赖图的 ECharts 按需注册精简 | ⚙️ | — |
| [T-ACCESS-154](../tasks/T-ACCESS-154.md) | 认证通道矩阵与 OAuth2 职责核查 | ⚙️ | — |
| [T-ACCESS-155](../tasks/T-ACCESS-155.md) | 租户运营结果与运行时门禁表达核查 | ⚙️ | — |
| [T-PERM-116](../tasks/T-PERM-116.md) | SDK 协议与客户端职责边界核查 | ⚙️ | — |
| [T-ACCESS-156](../tasks/T-ACCESS-156.md) | example 最小接入与完整样例分工核查 | ⚙️ | — |
| [T-PERM-117](../tasks/T-PERM-117.md) | 主体组织写入与 FULL 同步规模核查 | ⚙️ | — |
| [T-ADMIN-036](../tasks/T-ADMIN-036.md) | 菜单读取故障与无权限表达核查 | ⚙️ | — |
| [T-PERM-118](../tasks/T-PERM-118.md) | 角色类型支持面与管理提示核查 | ⚙️ | — |
| [T-PERM-119](../tasks/T-PERM-119.md) | 授权计划处理阶段与目录装载核查 | ⚙️ | — |
| [T-PERM-120](../tasks/T-PERM-120.md) | 资源类型配置与生命周期职责核查 | ⚙️ | — |
| [T-PERM-121](../tasks/T-PERM-121.md) | 业务域分类与条件互斥解释边界核查 | ⚙️ | — |
| [T-ACCESS-157](../tasks/T-ACCESS-157.md) | 投影初始化与公共设施职责核查 | ⚙️ | — |
| [T-ADMIN-037](../tasks/T-ADMIN-037.md) | 平台公告与文件容量边界核查 | ⚙️ | — |
| [T-FE-084](../tasks/T-FE-084.md) | 授权交互状态与局部类型边界核查 | ⚙️ | — |
| [T-FE-085](../tasks/T-FE-085.md) | 管理导航与身份交互一致性核查 | ⚙️ | — |
| [T-ACCESS-158](../tasks/T-ACCESS-158.md) | 工程验证与发布支持范围核查 | ⚙️ | — |
| [T-ACCESS-159](../tasks/T-ACCESS-159.md) | 边界修复联合验收与计划归档 | ⚙️ | T-PERM-113、T-FE-081、T-PERM-114、T-PERM-115、T-ACCESS-151、T-ACCESS-152、T-ACCESS-153、T-FE-082、T-FE-083、T-ACCESS-154、T-ACCESS-155、T-PERM-116、T-ACCESS-156、T-PERM-117、T-ADMIN-036、T-PERM-118、T-PERM-119、T-PERM-120、T-PERM-121、T-ACCESS-157、T-ADMIN-037、T-FE-084、T-FE-085、T-ACCESS-158 |

## 归档条件

全部已登记及后续加入的任务 done/cancelled；用户确认事项无未落地安排，维持/暂缓均有明确决定与权威落点；各 design_writeback 完成，原报告映射可追溯。T-ACCESS-159 的适用联合验收、收口回归和本地双轨核对完成后，按[生命周期规范](../../.agents/skills/design-plan-task-lifecycle/SKILL.md#65-归档)迁移计划、任务及证据，同步文档/计划/任务索引。

## 当前进度

计划与任务待启动。已确认目标见所属权威设计；零消费工具裁剪和图表按真实消费收窄按对应任务卡执行，结构建议按核查先行安排逐项讨论。实现、业务测试与性能验证均未执行。

