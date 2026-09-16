---
doc_type: task
id: T-ACCESS-050
title: 首次部署验证与版本打点收口——空环境全栈冒烟 + v0.1.0 tag + 042 归档
status: done
plan: docs/archive/2026-09-16/release-preview-plan.md
domain: access-service
design_refs:
  - docs/plans/release-preview-plan.md
  - docs/design/decision-registry.md
depends_on: [T-ACCESS-047, T-ACCESS-048, T-ACCESS-049]
blocks: []
acceptance:
  - "空环境全栈验证：docker compose down -v → --profile app up -d --build → 冒烟清单全绿（五容器 healthy/前端登录/菜单渲染/建角色授权主链/example 403→授权后 200），证据（命令+日期+结果）内联本卡"
  - "开发模式手工路径走查（quickstart 第二路径成稿校验）"
  - "版本打点：根 pom 与各模块 pom parent.version 1.0.0-SNAPSHOT→0.1.0 → commit → 本地 tag v0.1.0 → 主线回 0.2.0-SNAPSHOT commit；只本地不 push（2026-08-28 定规）"
  - "T-ACCESS-042 终态卡单卡归档至 docs/archive/2026-09-16/tasks/，看板链接同步"
  - "registry 行落档（发布口径三项定调 + LICENSE 决策 + Q-006 修复）"
  - "计划 release-preview-plan 归档（四任务全 done 后）"
  - "全量回归 mvn test -T 1C 含 E2E 绿（跑前停本机 9100 dev 服务，日志整文件落盘）"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-16
---

## 背景

项目从未整体部署过（Q-006「现在为什么没出事」原文即「项目未正式部署」）；发布预览的收口动作 = 空环境真实验证 + 版本锚点 + 治理收尾。

## 范围

空环境全栈冒烟、开发模式路径走查、v0.1.0 版本打点（tag 本地）、T-ACCESS-042 单卡归档、registry 行、计划归档、全量回归。

## 当前口径

- 版本打点流程：根 pom+各模块 pom parent.version → 0.1.0（一个定格 commit）→ tag v0.1.0 → 主线 0.2.0-SNAPSHOT（一个恢复 commit）；git tag 仅本地。
- 冒烟基线参考 T-ACCESS-042 dev 冒烟九项先例 + 全栈特有项（容器健康、nginx 同源反代链路）。
- 全量回归纪律：先停本机 9100 dev 服务（DualInstanceContainerTest 占真实端口）；mvn 运行期间禁改源码；输出整文件落盘再解析；失败先隔离复跑定性。

## 验收对照

见 acceptance 七条。

## 完成记录

- 空环境全栈验证（2026-09-16）：`docker compose --profile app down -v` → `up -d --build`（镜像含 T-ACCESS-048 修复）→ 七容器 Up（gateway/frontend healthy）；bootstrap 空库首启日志 `Bootstrap graph created: tenant=1, adminSubjectId=1, roleId=1, apiResources=86, mappings=85, grants=135, menus=14, defaultTreeRootOrg=1`；TYPE_DEFINITION 实例投影自愈补种 33 行。
- 冒烟脚本 22/22 全绿（node 脚本，脚本序列随卡留档于完成记录末尾）：①验证码签发（nginx→gateway→access 链）+ Redis 取码 + admin 真实登录 + accessToken；②user-menu 菜单下发（4322 bytes）；③建角色/建用户（initialPassword 回传 userId=2）/user-role/assign；④接入五步——service-config/save → sync FULL（createdResources=1/createdMappings=1）→ 资源树定位 resourceId=144；⑤目标用户登录 → 授权前经 Gateway 403；⑥apply-grant-plan（items=1）→ 授权后 0.2s 转 200，响应回显 `{"greeting":"hello, release-smoke","userId":"2","tenantId":"1"}`（Gateway 身份注入链）。
- 048 部署链路实证：同批次 access-service 日志出现 `Evicted all L2 cache for catalog=admin:org-visibility, tenantId=1`（bootstrap 播种事务触发 flush 的 legacy 同批 evict）。
- 开发模式走查（quickstart 路径 B）：compose 基建 + 手工 `mvn spring-boot:run`（access/gateway，env 经 `set -a; source .env`）+ `VITE_PORT=8890 pnpm dev`；access 就绪 ~12s、bootstrap no-op（幂等证据 `already present and matching — no-op (password untouched)`）；vite 代理链（8890→8080→9100）验证码+登录 200。
- 全量回归：首跑 access-service fork 内 `TaskExecutionLeaseConcurrencyTest.claimBlockedOverMaxAttempts` 1 失败（expected: 1 but was: 0，L333）——隔离复跑 `-Dtest=TaskExecutionLeaseConcurrencyTest` 10/10 绿（23.1s），定性 `-T 1C` 模块并行负载时序抖动、非本批改动破坏；重跑全量结果见下。
- dev 环境提示：`down -v` 重建后 dev 库 admin 密码=本地 .env 所填值（冒烟值，用户可改 .env 后 `down -v` 重种）。

- 全量回归终跑（t050_full_regress4.log，2026-09-16，含 41c91b50d 评审处置与租约用例加固）：`mvn test -T 1C` 十模块全 SUCCESS 含 E2E（2:29），八结果段合计 1706 项 0 失败。
- 时序用例加固：`TaskExecutionLeaseConcurrencyTest.claimBlockedOverMaxAttempts` 原 `sleep(1200)` 对 1s 租约仅 200ms 余量（T-ACCESS-030 在同类 takeover 用例已实证该形态不可靠并改有界轮询、本方法漏改；本批 `-T 1C` 全量 2/3 失败、隔离三连绿）——按同文件既定定式改 5s 有界轮询 `failExpiredOverMaxAttempts`，隔离复跑绿后终跑全量绿。
- 双轨评审：代码轨 P3×6 + 文档轨 P1×2/P2×2/P3×6 逐条核实全成立、全处置（两项用户拍板 + 十项直接修 + 两项带理由不修，registry 2026-09-16 处置行）。
- 版本打点：根 pom 与各模块 pom parent.version → 0.1.0 定格 commit → 本地 tag `v0.1.0` → 主线回 `0.2.0-SNAPSHOT` commit（tag 哈希见 git tag -l v0.1.0；只本地不 push，2026-08-28 定规）。
- 设计回写：registry 2026-09-16 立项/处置两行 + 本行；Q-006 收敛入 pending-problems 已收敛索引；gateway.md/architecture/capability-structure 随 047/处置批回写；计划 completed 并随本批次归档 archive/2026-09-16/。

## 非目标 / 遗留

- 不发布产物（无 artifact/镜像分发）；不做升级路径验证（0.1.0 无迁移框架，已知限制明示）。
