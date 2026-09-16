---
doc_type: plan
title: 首个发布预览（v0.1.0）与部署验证
status: active
domain: access-service
design_refs:
  - docs/design/services/gateway.md
  - docs/design/access-service-architecture.md
  - docs/design/extension-guide.md
  - docs/design/access-service-rebuild-runbook.md
  - .claude/skills/dual-layer-cache-framework/SKILL.md
tasks:
  - T-ACCESS-047
  - T-ACCESS-048
  - T-ACCESS-049
  - T-ACCESS-050
acceptance: "全栈 compose profile 一条命令起五容器且默认 compose 行为不变；外部用户路径文档齐全（quickstart/deployment 基线/CHANGELOG/frontend README/LICENSE）；Q-006 修复收敛；空环境部署验证证据登记；本地 tag v0.1.0 存在；T-ACCESS-042 卡归档；全量回归含 E2E 绿"
last_updated: 2026-09-16
---

# 首个发布预览（v0.1.0）与部署验证

## 目标

让「开源通用 IAM」定位（2026-08-28 定案）第一次被外部可验证：

1. **全栈一键拉起**：`docker compose --profile app up` 一条命令跑起前端 + 三服务 + 基建（默认 `docker compose up` 仍只起基建，现有 dev 流零干扰）。
2. **外部用户路径文档齐全**：quickstart、生产部署基线、CHANGELOG、frontend README、根 LICENSE 重写。
3. **版本锚点**：本地 tag `v0.1.0`（预览版语义，只本地不 push，2026-08-28 定规）。
4. 顺带：Q-006 双命名空间失效修复收敛；T-ACCESS-042 终态卡单卡归档。

三项定调（2026-09-16 用户拍板）：①全栈 compose profile；②v0.1.0 预览版（非 1.0.0——未交付清单与无迁移路径决定预览语义更诚实）；③文档全套，根 LICENSE 重写为本项目名义（用户口径：项目已与 RuoYi 无关；frontend/ 的 pure-admin-thin 上游署名处理在 T-ACCESS-049 任务卡单列复核）。

## 非目标

- 不建发布流水线（CI 维持 T-ACCESS-017 最小两 job）；不引入 jib/flatten/`${revision}`。
- 不做 DDL 迁移框架（0.1.0 已知限制明示「DDL 变更=重建」）。
- 不做租户运营 / PERSONAL、GROUP_ROLE 生命周期 / 动态数据权限（暂缓卡 T-PERM-035/036/054 不动，等 PM 重申）。
- 多实例/横向扩展不在验证范围（bootstrap 单实例限制写入已知限制）。

## 准入条件

- 任务板无 in-progress/review 任务（2026-09-16 核实：全部计划已归档，仅剩暂缓卡）。
- 全量回归基线绿（Q-009 收口 1698/0F 含 E2E，2026-09-15）。

## 任务清单（引用 tasks/README 看板，此处只列 ID+标题+状态快照）

| ID | 标题 | 状态 |
|---|---|---|
| T-ACCESS-047 | 全栈部署编排——compose app profile + 服务/前端镜像 + 配置占位符 | ⚙️ |
| T-ACCESS-048 | Q-006 双命名空间失效修复——ORG_VISIBILITY legacy 别名同批 evict + 回归锁 | ⚙️ |
| T-ACCESS-049 | 发布文档与版本化——quickstart/deployment 基线/CHANGELOG/README/LICENSE | ⚙️ |
| T-ACCESS-050 | 首次部署验证与版本打点收口——空环境全栈冒烟 + v0.1.0 tag + 042 归档 | ⚙️ |

依赖：T-ACCESS-049 depends_on T-ACCESS-047（quickstart 须按落地形态成稿）；T-ACCESS-050 depends_on T-ACCESS-047/048/049；T-ACCESS-048 独立。

## 归档条件

四任务全 done + 验收达成：部署验证证据登记、v0.1.0 本地 tag 存在、Q-006 收敛、T-ACCESS-042 卡归档、registry 行落档、全量回归含 E2E 绿。

## 当前进度

2026-09-16 立项（用户三问拍板后转出）；四任务待启动。
