# 归档批次 2026-09-16（release-preview 收口批次）

本批次归档「首个发布预览（v0.1.0）与部署验证」计划的全部产物——计划 completed（四任务全 done）、五张终态任务卡随迁。

## 归档内容

| 条目 | 类型 | 说明 |
|---|---|---|
| release-preview-plan.md | 执行计划（completed） | 首个发布预览（v0.1.0）与部署验证：全栈 compose profile + 发布文档全套 + 空环境部署验证 + 本地 tag v0.1.0；三项定调与双轨评审处置见 decision-registry 2026-09-16 三行 |
| tasks/T-ACCESS-047.md | 任务卡（done） | 全栈部署编排——compose app profile + 服务/前端镜像 + 配置占位符 |
| tasks/T-ACCESS-048.md | 任务卡（done） | Q-006 双命名空间失效修复——ORG_VISIBILITY legacy 别名同批 evict + 回归锁 |
| tasks/T-ACCESS-049.md | 任务卡（done） | 发布文档与版本化——quickstart/deployment 基线/CHANGELOG/README/LICENSE |
| tasks/T-ACCESS-050.md | 任务卡（done） | 首次部署验证与版本打点收口——空环境全栈冒烟 22/22 + v0.1.0 tag |
| tasks/T-ACCESS-042.md | 终态任务卡（单卡归档） | URL 单命名空间统一 `/api/access/**`——done 2026-09-15、无活跃计划归属，按归档滞留禁令随本批次迁入 |

## 收口证据摘要

- 最终全量回归 `mvn test -T 1C` 十模块含 E2E 全绿（八结果段 1706 项 0 失败，2026-09-16）。
- 空环境全栈冒烟 22/22（bootstrap 空库播种 → 登录 → 菜单 → 建角色/用户/绑定 → example 接入五步 → 403→授权→0.2s 转 200 + 身份回显）；开发模式走查（quickstart 路径 B）通过。
- 版本打点：v0.1.0 定格 commit + 本地 tag → 主线回 0.2.0-SNAPSHOT（只本地不 push）。

## 当前权威入口

- 部署与发布：[docs/quickstart.md](../../quickstart.md)、[docs/ops/deployment.md](../../ops/deployment.md)、[CHANGELOG](../../../CHANGELOG.md)
- URL 命名空间契约：契约总册 §2（`docs/design/access-service-api-contract.md`）
- 缓存别名机制：dual-layer-cache-framework skill 双副本
