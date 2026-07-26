# 归档：权限授予页面设计文档（2026-07-26）

本目录归档已废弃的权限授予页面（v1 + v2）前端设计文档，仅作历史追溯，不再作为实现依据。

## 归档原因

权限授予页面对现有交互不满意，v1（`permission-grant`）+ v2（`permission-grant-v2`）两套页面于 2026-07-26 整体删除，重新设计中。对应代码、plan、任务同步处置，设计文档归档至此保留历史追溯。

## 归档文件

| 文件 | 说明 |
|---|---|
| `permission-grant.md` | v1 权限授予页面前端设计（含 §16 授权弹窗与右栏变更重构） |
| `permission-grant-state-model.md` | v2 状态模型补充（状态机 / 事件 / 不变量，含并发缺口预留） |
| `permission-grant-interaction.md` | v2 交互规格补充（直接操作矩阵 + 渐进披露，重构提案） |
| `permission-grant-error-flow.md` | v2 错误流补充（异常清单 / 预防 / 反馈 / 恢复 / 重试） |

## 关联处置

- **plan 归档**：`docs/plans/archive/2026-07/`（`permission-grant-v2-plan.md`、`permission-grant-ux-refactor-plan.md`），status: cancelled
- **任务处置**：见 `docs/tasks/README.md` 前端段废弃说明块
  - done 保持历史事实但产出废弃：T-FE-014、T-FE-024~026、T-FE-029~034
  - cancelled：T-FE-018、T-FE-027、T-FE-028、T-FE-035
- **保留的共享件**（跨页使用，未删）：`ReConditionEditor` / `utils/condition-rules.ts`（被 `permission-condition` 页使用）
- **后端授权接口**（`role-resource-permission/*`）属领域能力，不随页面删除，权威契约见 `docs/design/permission-center/api-contract.md`

## 当前状态

**仅作历史追溯，不再作为实现依据。** 重新设计后另立新设计文档与新任务（T-FE 下一编号 036）。
