# 原报告范围映射

原报告快照（gitignored `.tmp` 工作副本，不入仓库与 git 历史）来自用户提供的整体评估，源码基线为 `44e5b03e2`。快照保留原报告文字，不表示其全部判断、建议或测试声明均已核实或采纳。执行入口为[计划](../../../plans/architecture-boundary-remediation-plan.md)，任务范围与验收只在各任务卡。

本表只做来源到责任任务的导航。全部纳入跟踪；结构建议先核查并由用户逐项确认，未决不等于接受风险、不等于自动实施。已有暂缓事项只回链，不重复立项。

| 原报告位置 | 责任任务／已有载体 |
|---|---|
| 一.1 操作缓存；三.9/12 类型与引擎；七缓存减法 | [T-PERM-113](../../T-PERM-113.md) |
| 一.2 读取预算；三.12 引擎；五高扇出证据 | [T-PERM-114](../../T-PERM-114.md)、[T-PERM-115](../../T-PERM-115.md) |
| 一.3 审计饱和；三.16 审计分级；五饱和证据 | [T-ACCESS-151](../../T-ACCESS-151.md)、[T-ACCESS-152](../../T-ACCESS-152.md) |
| 一.4 公共分页；四公共列表设施 | [T-FE-081](../../T-FE-081.md) |
| 二 common 的共同语义准入与职责 | [T-ACCESS-157](../../T-ACCESS-157.md) |
| 二 gateway 多认证矩阵、Redis 往返与在线回源 | [T-ACCESS-154](../../T-ACCESS-154.md) |
| 二 perm-common 协议/算法/传输适配 | [T-PERM-116](../../T-PERM-116.md) |
| 二 client starter 小接口、认证前提及宿主侵入 | [T-PERM-116](../../T-PERM-116.md)、[T-ACCESS-158](../../T-ACCESS-158.md) |
| 二 registration starter 的显式发布与重试边界 | [T-PERM-116](../../T-PERM-116.md) |
| 二 example 两类样例、内存任务与真实 SQL | [T-ACCESS-156](../../T-ACCESS-156.md) |
| 三.1 auth / OAuth2 协议维护 | [T-ACCESS-154](../../T-ACCESS-154.md) |
| 三.2 tenant 的数据库/门禁运行时结果与恢复 | [T-ACCESS-155](../../T-ACCESS-155.md) |
| 三.3 user 的主体锁、吞吐与真实观测 | [T-PERM-117](../../T-PERM-117.md) |
| 三.4 org 的多事实联动与 org_type 兼容 | [T-PERM-117](../../T-PERM-117.md) |
| 三.5 menu 的技术故障与无权表达 | [T-ADMIN-036](../../T-ADMIN-036.md) |
| 三.6 role 的产品支持面、历史/预留语义 | [T-PERM-118](../../T-PERM-118.md) |
| 三.7 grant 的稳定阶段与相关类型装载 | [T-PERM-119](../../T-PERM-119.md) |
| 三.8 resource 的多种关系与职责/读取成本 | [T-PERM-120](../../T-PERM-120.md) |
| 三.9 type 的 extra 类型化解析与页面编辑 | [T-PERM-120](../../T-PERM-120.md)；操作缓存另见 [T-PERM-113](../../T-PERM-113.md) |
| 三.10 domain 的分类含义与目录请求内复用 | [T-PERM-121](../../T-PERM-121.md) |
| 三.11 rule 的条件/互斥解释与有限词表 | [T-PERM-121](../../T-PERM-121.md)；完整排障沿既有 [Q-060](../../../pending-problems.md#q-060) |
| 三.12 engine 的单执行主体、索引回退 | [T-PERM-113](../../T-PERM-113.md)、[T-PERM-114](../../T-PERM-114.md)、[T-PERM-115](../../T-PERM-115.md)；保留既有语义 |
| 三.13 sync 的 FULL 长事务、支持规模与重试 | [T-PERM-117](../../T-PERM-117.md) |
| 三.14 projection 的同事务与门面职责 | [T-ACCESS-157](../../T-ACCESS-157.md) |
| 三.15 bootstrap 的初始化/校验/迁移边界 | [T-ACCESS-157](../../T-ACCESS-157.md) |
| 三.16 audit 的可靠性分级、导出与故障策略 | [T-ACCESS-151](../../T-ACCESS-151.md)、[T-ACCESS-152](../../T-ACCESS-152.md)；说明订正见 [T-ACCESS-153](../../T-ACCESS-153.md) |
| 三.17 platform：字典/公告/文件/任务/配置 | [T-ADMIN-037](../../T-ADMIN-037.md)；职责边界见 [T-ACCESS-157](../../T-ACCESS-157.md) |
| 三.18 infrastructure：公共/横向能力与锁序 | [T-ACCESS-157](../../T-ACCESS-157.md)；锁证据见 [T-PERM-117](../../T-PERM-117.md) |
| 四 登录/改密/OAuth 同意、独立平台运营 | [T-ACCESS-154](../../T-ACCESS-154.md)、[T-ACCESS-155](../../T-ACCESS-155.md)、[T-FE-085](../../T-FE-085.md)；验证码延期沿既有 [Q-068](../../../pending-problems.md#q-068) |
| 四 用户/组织/岗位、角色及上下文守卫 | [T-PERM-117](../../T-PERM-117.md)、[T-PERM-118](../../T-PERM-118.md)、[T-FE-085](../../T-FE-085.md) |
| 四 权限授予、状态协调、组件职责 | [T-FE-084](../../T-FE-084.md) |
| 四 资源/类型/操作、extra 与明确字段 | [T-PERM-120](../../T-PERM-120.md) |
| 四 业务域/条件/互斥的提示 | [T-PERM-121](../../T-PERM-121.md) |
| 四 服务接口/凭证/同步状态的接入旅程导航 | [T-FE-085](../../T-FE-085.md)、[T-PERM-116](../../T-PERM-116.md) |
| 四 操作/变更/登录日志及 requestId | [T-ACCESS-152](../../T-ACCESS-152.md)、[T-ACCESS-153](../../T-ACCESS-153.md)、[T-FE-085](../../T-FE-085.md)；完整诊断沿 [Q-060](../../../pending-problems.md#q-060) |
| 四 文件管理与公共 HTTP 会话归属 | [T-ADMIN-037](../../T-ADMIN-037.md)、[T-FE-085](../../T-FE-085.md)、[T-FE-081](../../T-FE-081.md) |
| 四 strict:false、局部类型与授权组件拆分 | [T-FE-084](../../T-FE-084.md) |
| 五 e2e 的跨进程接线、类路径与打包产物 | [T-ACCESS-158](../../T-ACCESS-158.md) |
| 五 SDK 两种宿主探针 | [T-PERM-116](../../T-PERM-116.md)、[T-ACCESS-158](../../T-ACCESS-158.md) |
| 五 access 测试资产及最小补证 | [T-PERM-113](../../T-PERM-113.md)、[T-PERM-114](../../T-PERM-114.md)、[T-ACCESS-151](../../T-ACCESS-151.md)、[T-ACCESS-158](../../T-ACCESS-158.md) |
| 五 schema：租户/软删/唯一/外键/JSONB 长期成本 | [T-ACCESS-153](../../T-ACCESS-153.md)、[T-ACCESS-158](../../T-ACCESS-158.md)、[T-ADMIN-037](../../T-ADMIN-037.md) |
| 五 构建/CI/SNAPSHOT/部署支持范围 | [T-ACCESS-158](../../T-ACCESS-158.md) |
| 六 准入 OR、旧装配器、角色缓存描述 | [T-ACCESS-153](../../T-ACCESS-153.md) |
| 六 全仓无外键绝对规则 | [T-ACCESS-153](../../T-ACCESS-153.md) |
| 六 限流与安全响应头的规则/部署冲突 | [T-ACCESS-153](../../T-ACCESS-153.md) |
| 六 example 规划与关系待实施状态残留 | [T-ACCESS-153](../../T-ACCESS-153.md) |
| 七 localForage / 打印零消费工具 | [T-FE-082](../../T-FE-082.md) |
| 七 ECharts 多余注册 | [T-FE-083](../../T-FE-083.md) |
| 七 现役文档中的历史实现叙事 | [T-ACCESS-153](../../T-ACCESS-153.md) |
| 七 GROUP_ROLE / PERSONAL 裁剪前提 | [T-PERM-118](../../T-PERM-118.md) |
| 七 example 外壳与 SDK 管理接口裁剪前提 | [T-ACCESS-156](../../T-ACCESS-156.md)、[T-PERM-116](../../T-PERM-116.md) |
| 七 应保留的投影/分层/锁/fencing/探针/E2E | [T-ACCESS-157](../../T-ACCESS-157.md)、[T-PERM-117](../../T-PERM-117.md)、[T-ACCESS-158](../../T-ACCESS-158.md) |
| 建议处理顺序、适用测试与最终交付 | [T-ACCESS-159](../../T-ACCESS-159.md) |

