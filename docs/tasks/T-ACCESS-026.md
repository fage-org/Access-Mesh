---
doc_type: task
id: T-ACCESS-026
title: 验证证据登记与文档状态收口（含 post-merge 归档）
status: done
plan: docs/archive/2026-08-27/product-vertical-slice-plan.md
domain: cross-service
design_refs:
  - docs/design/architecture.md
  - docs/design/access-service-architecture.md
  - docs/archive/2026-08-27/access-post-merge-plan.md
  - docs/design/project-rules.md
depends_on: [T-API-001, T-ADMIN-022, T-PERM-043, T-ADMIN-023, T-GW-007, T-ACCESS-024, T-ACCESS-025, T-ADMIN-024]
blocks: []
acceptance:
  - "外部主机 Docker 验证证据正式登记：Docker 版本、执行命令、提交 SHA（7b7cf3254 等）、测试结果；68 项计数作为 2026-08-22 历史验证证据登记，不做数量同步机制（CI 已由 T-ACCESS-017 落地并以退出状态判定成功）"
  - "access-post-merge-plan 按其归档条件收口归档（其 CI 准入前置由 T-ACCESS-017 最小 CI 全绿关闭），归档自检清单执行"
  - "README 状态口径复核：T-ACCESS-021 已完成核心切片回写，本任务核对其与 example 接入（T-API-001）结论一致，补齐未交付能力清单"
  - "「一项任务只保留一个权威状态字段」复核：全量扫描 plans/tasks 无相互矛盾的状态表述（proposed 任务全 done、准入已关闭仍挂起等模式）"
  - "README 快速开始与 T-ACCESS-020 产物一致（compose 命令可执行）"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-27
---

# T-ACCESS-026 验证证据登记与文档状态收口

## 背景

文档与证据现状：README 状态已于 2026-08-23 先行改为真实中间状态（T-ACCESS-021 完成时更新为切片完成口径）；access-post-merge-plan 三任务全 done 但计划 proposed、准入（40 项 Docker 测试）未关闭且计数已过期（实际 68 项）；外部主机 Docker 验证无正式登记；CI 已由 T-ACCESS-017 前置落地（单测强制、容器门控按需）。本任务在里程碑 B 尾部做证据与文档状态最终收口。

## 范围

- 验证证据登记、post-merge 归档、状态字段一致性复核、README 口径复核。

## 当前口径

- CI 与测试数量口径：以退出状态判定成功；68 只作为 2026-08-22 历史验证证据，不维护计数同步。
- 本任务是证据与文档收口，不新增工程化能力。

## 非目标 / 遗留

- 不建设 Grafana/Prometheus 部署、告警编排、日志采集平台。
- 不做多分支流水线/发布流水线。

## 验收落地（2026-08-27）

### 外部 Docker 主机验证证据登记（历史基线，2026-08-22）

| 项 | 记录 |
|---|---|
| 执行环境 | 外部 Windows 主机，Docker Desktop 4.87 + WSL2（socat 代理 `docker-api-proxy` 容器暴露 `tcp://localhost:2375` 供 Testcontainers） |
| 执行命令 | `mvn -B test -pl access-service` |
| 提交 SHA 修复链 | `c8dc06c52` → `b9f48bb39` → `7b7cf3254`（`test(access): 任务 Bean 可见性与 applyVersion 并发断言修正`） |
| 测试结果 | 9 类 68 项 Testcontainers 门控全绿（2026-08-22 时点口径） |
| 后续同环境复验 | 2026-08-23 容器 fork 线程残留修复收口后同环境全量复验：722 项全绿（单测 execution 662 + 容器 execution 60，12 容器类），BUILD SUCCESS，无强杀日志/jvmRun dump（`@DirtiesContext` 生效），耗时 3m49s |

68 项计数自此仅作历史验证证据登记，不建立数量同步机制（当前容器轨随任务演进为 98 项，以 CI 退出状态判定成功）。

### CI 真实运行证据（T-ACCESS-017 落地，准入前置关闭依据）

| job | 触发 | 证据 |
|---|---|---|
| `unit-tests`（`mvn -B test -DskipTestcontainers=true`） | push/PR/手动 | 提交 `80e87fb98` push 后 GitHub Actions 自动运行成功（2026-08-23，仓库所有者确认） |
| `testcontainers`（`mvn -B test`） | PR/手动 | 手动 dispatch 运行成功，含全部 722 项与 12 个容器类（2026-08-23） |

access-post-merge-plan 原「40 项 Docker 门控 CI 验证前置」（自 merge-plan 归档时转移的责任）由上述最小 CI 落地并真实跑通而关闭。

### 双计划归档（设计口径：product-vertical-slice-plan 一并归档）

- `access-post-merge-plan`：三任务全 done、CI 准入前置关闭、稳定结论在 `docs/design/`，归档条件全部满足。
- `product-vertical-slice-plan`：18 项任务全 done（里程碑 A E2E 八步全绿 + 里程碑 B 全部交付），阶段完成；若仅改 status 保留 plans/ 则违反「一项任务只保留一个权威状态字段」复核（任务全 done 但 proposed 矛盾模式），按 project-rules §文档治理「阶段完成后把过程文档归档」与 merge-plan 先例（归档件 status: completed、任务卡 plan 字段指向 archive 路径、看板 plan 列「（已归档）」纯文本）一并归档。
- 归档落位 `docs/archive/2026-08-27/`（批次 README 含归档原因、内容定位与自检清单执行记录）；22 张任务卡 frontmatter `plan` 字段、`docs/plans/README.md` 索引、`docs/README.md` 执行计划索引/目录树/归档记录表、看板 22 处引用同步更新；旧路径 grep 零残留（历史归档件与 done 任务卡中描述当时状态的时点性记录除外，如 2026-08-22 批次 README 的迁入记录——其中「当前权威设计入口」节的活指引已更新为归档口径）。

### README 状态口径复核

- 项目状态更新为「核心垂直切片完成（里程碑 A）+ 试点加固完成（里程碑 B，随本任务收口）」双段口径；T-ACCESS-021 切片结论与 T-API-001 example 接入结论经三处交叉核对一致（特性表、服务架构图、快速开始启动顺序均含单受保护接口已交付与签名密钥同源要求）。
- 未交付能力清单补齐：文件夹级授权（T-ADMIN-025，proposed 未交付）、租户开通/运营、PERSONAL/GROUP_ROLE 生命周期、其余管理页面 mock 联调（Phase 3）、example 报表数据范围与动态数据权限。

### 状态字段一致性复核

- 全量扫描（任务卡 frontmatter × 看板行 × plan 状态表三方核对，列位精确解析）：双计划域 22 卡三方零不一致；唯一计划级矛盾为 access-post-merge-plan「proposed 但三任务全 done」，随归档消除。存量看板漂移一并收口：T-FE-024/025 状态 ⚙️ 与卡 done 矛盾（2026-07-26 产物废弃批次遗留）修正为 ✅，T-FE-029~031 回写列 ⏳ 修正为 —（产出废弃、设计回写不适用，口径同看板 v1/v2 废弃说明）。
- 归档后复扫：`docs/plans/` 剩余计划（design-review-def-followup/frontend-phase2~4/improvement 等）均仍有未完成任务，无「任务全 done 但计划 proposed」模式；本计划归档件 status: completed、任务清单表 T-ACCESS-026 行终态 ✅。

### README 快速开始与 T-ACCESS-020 产物一致性

- `docker compose up -d` 与 docker-compose.yml（T-ACCESS-020 产物）一致：PG 16-alpine trust 认证 + 权威 DDL initdb.d 首启自动执行、Redis 7 固定开发密码 `accessmesh-dev`、Nacos v2.3.2 standalone、端口仅回环绑定；bootstrap 环境变量组、启动顺序（access-service 9100 → Gateway 8080 → example-service 9300 可选 → 前端）、example 签名密钥同源要求（30003）、时间语义 UTC 部署说明均与产物及 T-API-001 结论一致；AGENTS.md 基础设施命令（`-f docker-compose.yml up -d nacos redis postgresql`）可执行、服务名与 compose 文件一致。

### design_writeback 说明

- 本任务回写面为根 README 状态口径、docs/README.md 与 docs/plans/README.md 双索引、看板引用与双计划归档；`access-service-architecture.md` 仅修复一处指向已归档计划的旧链接（文首实施编排引用，非设计语义变更、last_reviewed 不动），无其他变更（验证证据与 CI 口径的权威登记以本任务卡与归档批次 README 为载体，不写入架构文档）。
