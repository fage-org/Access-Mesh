---
doc_type: task
id: T-ACCESS-010
title: 切换 Gateway、SDK、Nacos和部署配置
status: done
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#2-目标工程与部署单元
  - docs/design/access-service-architecture.md#9-api-sdk-与生态切换
  - docs/design/architecture.md
  - docs/design/services/gateway.md
depends_on:
  - T-ACCESS-004
  - T-ACCESS-005
  - T-ACCESS-008
blocks: []
acceptance:
  - "Gateway 的 /admin/**、/perm/**、/auth/** 路由目标统一为 lb://access-service，原路径行为不变"
  - "perm-sdk 外部 Feign 目标统一为 access-service；access-service 内部不依赖 perm-sdk 调用自身"
  - "Nacos、Docker Compose、应用名、端口、日志、指标、服务配置和缓存失效载荷统一为 access-service"
  - "serviceCode/ownerCode 中代表两个旧服务的值统一为 access-service，外部来源服务身份保持各自 serviceCode"
  - "根 Maven 聚合删除 admin-service 与 permission-center 模块，旧启动类、运行配置和源码目录移除"
  - "不创建旧服务名、旧端口、空壳模块或转发代理；非归档代码和配置无旧服务发现目标"
  - "Gateway 与 perm-sdk 契约测试证明旧 HTTP 契约继续可用"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-22
---

# T-ACCESS-010 切换 Gateway、SDK、Nacos和部署配置

## 背景

工程内部归并完成后，必须一次性切换所有运行入口并删除旧部署单元，避免形成长期三服务并存状态。

## 范围

- 切换 Gateway、SDK、注册发现、容器和观测标识。
- 更新根构建并删除旧服务模块。
- 验证保留接口契约，不提供部署兼容层。

## 完成记录

**2026-08-22 done。4 项用户决策：① /admin/** 与 /perm/** 合并为一条路由；② 删除 SyncTaskFeignClient；③ perm.service-code 去默认值缺失启动失败；④ 非功能性旧服务名文本全量清理。**

- **Gateway 切换**：`application.yml` 路由 `admin-service`+`permission-center` 合并为 `id=access-service`（`lb://access-service`、`Path=/admin/**,/perm/**`、`StripPrefix=1`、`metadata.serviceCode=access-service`），`auth-routes` 目标由 `lb://admin-service` 改 `lb://access-service`（StripPrefix=0 不变）；`GatewayProperties.serviceUrl` 默认值与 `gateway.permission.service-url` 切换为 `lb://access-service`。`PermissionClient` 日志文本、`PermissionFilter` 3 项 Micrometer 指标 description（`gateway.perm.unreachable` 等）统一 access-service。
- **Gateway 契约测试**：`GatewayApplicationConfigTest` 固化 3 条路由（access-service/example-service/auth-routes）+ 合并路由 Path/StripPrefix/serviceCode + 负向断言（路由 id 与发现目标不得残留旧服务名）。测试内 serviceCode 测试数据统一中性值 `example-service`（7 个测试文件）。
- **perm-sdk**：`PermissionFeignClient` `@FeignClient(name="access-service")`；新增 `PermissionFeignClientContractTest` 固化 18 个 `@PostMapping` 封闭路径清单 + POST/单一 `@RequestBody` 形态断言（SDK HTTP 契约零漂移）。`SyncTaskFeignClient` 删除（admin-service S5 内部同步定制、Map 请求体、仓库内无使用者；8 个 sync/full-sync 端点对外部服务继续保留）。`FeignInternalSyncInterceptor` `perm.service-code` 去默认值 `admin-service` → `${perm.service-code}`（无默认；配置 internal-secret 但缺 service-code 时启动失败 fail-fast）。
- **access-service 内部不依赖 perm-sdk 调用自身**：核验通过——access-service 仅依赖 `perm-common`（共享 DTO），不依赖 `perm-client-spring-boot-starter`（Feign 模块）；无使用者死代码 `RestTemplateConfig`（@LoadBalanced RestTemplate，内部 HTTP 调用时代残留）删除。
- **旧名清理（全量）**：主代码/测试 Javadoc 措辞（permission-center→access-service/permission 域、admin-service→admin 域）约 45 文件；`UserServiceImpl.updateUser` 过时的"同步任务记录"描述（T-ACCESS-005 内部同步删除后残留）一并清理；`common/GlobalErrorCode` 错误码分段注释更新为管理域/权限域措辞（分段值不变）；`access-service.sql` 表注释"admin-service 事实源"→"access-service admin 域事实源"、`sync_metadata.source_service` 注释示例值改外部服务；前端 mock 注册服务列表两条旧服务记录合并为一条 `access-service`、mock/src 注释措辞与 placeholder 示例值统一。
- **保留项（有意）**：`LocalProjectionOwner.LEGACY_ADMIN_SOURCE="admin-service"` 安全拒绝列表值（设计 §4.3：外部 sync 不得冒充 admin-service 来源；`LocalProjectionGuardTest`/`AbstractUserSyncAppServiceTest.shouldRejectInternalSourceService` 负向断言固化）；`docs/design/services/admin-service-api-contract.md` 等真实文档路径引用与 `docs/design/permission-center/` 概念文档目录（T-ACCESS-012 重基线处理）；`AccessServiceApplication` 等归并历史陈述；superseded schema 文件头。
- **部署单元核验**：根 Maven 聚合自 T-ACCESS-001 起即无旧模块；无旧启动类/运行配置/源码目录/docker-compose 文件残留（仓库本无容器编排文件，AGENTS.md 基础设施命令示例不涉及服务名，保留）；Nacos dataId（`gateway.yml`/`access-service.yml`）、应用名、端口（8080/9100/9300）核验正确；缓存失效载荷（`PermInvalidateEvent`）经代码/测试统一（载荷中的 serviceCode 值为运行时数据，空库起步无存量）。
- **设计回写**：`access-service-architecture.md` §9 追加"落地实现（T-ACCESS-010）"段（last_reviewed 2026-08-22）；`gateway.md` 职责边界新增路由拓扑条目 + `gateway.permission.service-url` 默认值更新；`architecture.md` §1 全节更新为归并后拓扑（服务清单/拓扑图/交互矩阵/§1.5/§1.6，last_reviewed 2026-08-22；§2 之后历史章节仍待 T-ACCESS-012 回写，顶部提示已更新）；AGENTS.md 服务架构图/权威来源表（schema 行修正指向 access-service.sql，既有漂移顺带修正）/SNAPSHOT 依赖链示例；README.md 拓扑图与 psql 初始化命令。
- **验证（2026-08-22）**：`mvn clean install -DskipTests` 全量成功；`mvn test` 全模块 BUILD SUCCESS——access-service 610 测试（37 skip 为本机无 Docker 的 PG Testcontainers，与基线一致）、gateway 59 测试（含 GatewayApplicationConfigTest 路由契约 6 项）、perm-client-starter 8 测试（含新增 PermissionFeignClientContractTest 3 项）。残留扫描：非归档代码/配置中剩余旧服务名引用全部为合法保留项（真实文档路径、LEGACY_ADMIN_SOURCE 拒绝列表、负向断言、归并历史说明注释），零功能残留。
