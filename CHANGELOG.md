# Changelog

本文件记录 AccessMesh 的对外版本变更（[Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 体；版本语义遵循 SemVer）。内部开发历史与任务口径以 [docs/README.md](docs/README.md) 归档记录为权威，不在此回溯补写。

## [Unreleased]

### Fixed

- compose `GATEWAY_CORS_ALLOWED_ORIGINS` 透传改 `-` 形态——显式置空（=禁用 CORS）此前被 `:-` 默认值吞掉，文档承诺的关闭路径到不了容器（claude 外评 P3）。
- 发布文档修正：quickstart 授权闭环补「持角色用户/令牌来源」获取路径与密钥分发范围表述；deployment.md §4 真实 IP 边界改准确口径（Gateway IP 条件只消费直连对端地址，多层代理下真实 IP 不可用——codex sol 外评 P2）；nginx.conf 注释对齐实际 hash 路由；registry/pending-problems 归档连带锚点回写；rebuild-runbook JWT 密钥口径随 fail-fast 更新。

## [0.1.0] - 2026-09-16

首个预览版（preview）：核心产品链路第一次以「一条命令全栈跑起来」的形态对外可验证。

### 新增（能力概览）

- **权限引擎**：资源-操作-角色三位一体 RBAC，条件权限（时间/IP/内联与管理页双轨）、范围权限（scopeMode 四态）、角色互斥（授权时校验 + sync 通道逐条守卫）、资源依赖自动补全、类型授权根（AUTHORITY_ROOT 首授种子）、资源类型级所有权门禁（MANAGED/SYNC）。
- **统一查询引擎**：`PermQueryEngine` 单入口（check/batch-check/query-resources/query-scopes），判定面继承 + 目标模式三态；网关级鉴权（接口快照本地匹配 + 30 秒撤权边界，fail-closed）。
- **管理面**：12 能力包单体 access-service——组织与用户（默认树 + 生命周期）、角色、资源与操作定义、类型定义、权限授予（记录级聚焦编辑）、权限条件、冲突规则、业务域分类（CLASSIFY + 全局域动态补集）、服务与接口映射、系统配置、操作日志、菜单、字典、文件（文件夹级授权）。
- **认证**：Sa-Token 会话（Bearer 头、Cookie 通道双端关闭）+ OAuth2（授权码 + PKCE / 刷新令牌）。
- **多租户底座**：tenant_id 行级隔离（租户上下文 + TenantFactory）；空库幂等 bootstrap（首管理员 + 固定图最小授权）。
- **SDK**：perm-client（Feign 远程查询）、perm-gateway（Gateway 鉴权插件）；示例服务 example-service（单受保护接口，经 Gateway 鉴权 + 身份回显）。
- **部署编排**：`docker compose --profile app` 全栈一键预览（前端 nginx 同源反代 + 三服务镜像）；统一 URL 命名空间 `/api/access/**`（外部路径=服务路径）。
- **文档**：快速开始、生产部署基线、API 契约总册、引擎三册、扩展指南（业务服务接入 / 自定义资源类型）。

### 已知限制（preview 边界）

- 单租户试运行：租户开通/运营能力未交付（固定租户 1）。
- 角色生命周期：功能角色仅 BASIC_ROLE（PERSONAL/GROUP_ROLE 未交付；GROUP_ROLE 写入口已删除）。
- 动态数据权限（scopeMode → SQL 端到端）与自动授权暂缓（写入口预留禁用态，等需求重申）。
- 无数据库迁移框架：DDL 变更 = 销毁重建（`docker compose down -v`），不适用于有存量数据的环境。
- bootstrap 与文件存储均为单实例约束；多实例横向扩展未验证。
- 发布物仅源码形态（无制品仓库/镜像分发）；CI 为最小两 job（单测 + Testcontainers 全量），无发布流水线。

[Unreleased]: https://github.com/fage-org/Access-Mesh/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/fage-org/Access-Mesh/releases/tag/v0.1.0
