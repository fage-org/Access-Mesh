---
doc_type: task
id: T-ACCESS-020
title: 空库 bootstrap（一键基础设施 + 幂等首管理员种子）
status: proposed
plan: docs/plans/product-vertical-slice-plan.md
domain: cross-service
design_refs:
  - docs/design/schema/access-service.sql
  - docs/design/access-service-architecture.md
  - docs/design/architecture.md
depends_on: [T-ACCESS-019]
blocks: [T-FE-041, T-ACCESS-021]
acceptance:
  - "根目录新增真实 docker-compose.yml：一条命令启动 PostgreSQL、Redis、Nacos；compose 仅承诺一键基础设施，README.md 写明 access-service → Gateway → 前端的启动顺序（不宣传整套产品一键部署）；README.md 与 AGENTS.md 快速开始命令与实际文件一致（修复当前引用不存在文件的断链）"
  - "唯一权威 DDL 自动/显式执行到位（无 Flyway，开发期重建模式维持）；类型种子保持租户 1 现状（首期固定租户 1，不做租户开通）"
  - "载体为 access-service 内默认关闭（access.bootstrap.enabled）的幂等 ApplicationRunner：仅负责触发一个事务化 initializer；复用现有领域服务（用户/主体/投影/角色创建与授权绑定链）与 BCrypt 哈希环境变量密码，不走带操作者权限校验的管理 AppService、不向通用授权链加入 bootstrapBypass——需要无操作者写入的授权环节使用包内可见、bootstrap 专用的写入组件（复用 DomainService 内部逻辑），不给通用授权服务增加公开的无操作者入口；不维护 SQL bootstrap 种子链路，不建 bootstrap 框架/独立模块/分布式锁（仅单实例启用）"
  - "幂等语义（三状态）：① 种子图完全不存在——单事务创建完整固定图；② 完整存在且身份、角色、关联与授权完全匹配——整体 no-op，绝不重置密码；③ 部分存在、关联缺失或固定业务键被其他数据占用——启动失败并报告具体冲突，不自动修复、不补权、不扩权；enabled=true 时密码缺失或空白 fail-fast，密码不写日志；不新增 ownership 字段、种子版本表或通用 bootstrap 框架（唯一约束仅作并发兜底）"
  - "双角色双用户模型（T-ACCESS-016 定稿）：bootstrap 创建首个管理员（abstract_user/sys_user 同主体 ID、resource_entity 投影）并绑定一个管理用功能角色，按 T-ACCESS-016 的 bootstrap 管理 API 清单执行种子——幂等创建清单内管理 API 的 resource_entity(API) 与 resource_api_mapping，并给管理角色精确授予实例级 API:ACCESS 及对应业务门禁权限（禁 scopeAll 大包/临时白名单）；目标接口 POST /admin/role/my-info 仅预建 resource_entity(API) 并预授 API:ACCESS+canGrant，不创建映射（归 E2E 真实创建）；不创建 E2E 目标用户与普通功能角色（归 T-ACCESS-021）；登录验证通过"
  - "首管理员密码经环境变量注入（Java 侧 BCrypt 哈希落库，无明文）；重复执行 bootstrap 不重复建号（幂等验证用例）"
  - "空库 → 一键基础设施 → 首管理员可登录的完整链路在外部 Docker 主机验证一次并登记证据"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-23
---

# T-ACCESS-020 空库 bootstrap

## 背景

权威 DDL 仅为租户 1 写类型种子（全部 INSERT 写死 tenant_id=1），无任何管理员账号种子（sys_user/abstract_user 零 INSERT），代码中无任何 CommandLineRunner/ApplicationRunner/开通接口；仓库根目录及全库无 docker-compose.yml，而 README.md:57 与 AGENTS.md 均引用它——空库执行 DDL 后无法完成首次登录，快速开始命令是断链。本任务在模型收敛（T-ORG-001）之后实施，种子直接按统一主体 ID 与新类型码终态编写，避免二次返工。

## 范围

- 根目录 docker-compose.yml（nacos/redis/postgresql）与 README/AGENTS 快速开始对齐。
- 幂等种子：默认租户 + 首管理员主体链 + BASIC_ROLE 绑定；密码环境变量注入。
- 幂等种子执行（事务化 initializer + 领域服务复用）与 compose 基础设施编排。

## 当前口径

- 最小 bootstrap：一键基础设施（compose）→ 权威 DDL → 固定租户 1 → 首管理员 + 管理用功能角色（幂等 ApplicationRunner，默认关闭）；管理角色按 bootstrap 管理 API 清单双层最小授权，目标接口仅预授 API:ACCESS+canGrant 且无映射（首管理员自身也无法经 Gateway 调用），目标用户初始 403 起点由此保证。
- 不建独立 bootstrap 模块、分布式锁或 bootstrap 框架；幂等按三状态口径执行（全图不存在则单事务创建 / 全图完整匹配则整体 no-op、绝不重置密码 / 部分存在、关联缺失或固定业务键被其他数据占用则 fail-fast 报告冲突），唯一约束仅并发兜底。
- 种子遵循统一主体 ID（T-ORG-001 后 sys_user.id = abstract_user.id）与收敛后类型码。

## 非目标 / 遗留

- 不做计费、套餐、租户配额；不做平台超管跨租户代管；不做完整租户运营后台；不做租户开通（首期固定租户 1）。
- 不做租户创建/停用接口与租户表（SaaS 对外宣称前另行立项；Gateway 已从可信会话解析租户，现状无任意指定风险）。
- 不引入 Flyway/Liquibase。
