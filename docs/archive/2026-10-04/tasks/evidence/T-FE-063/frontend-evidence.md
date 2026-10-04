# 前端测试精简证据（2026-10-03）

## 实际映射与保留

新增测试专用 `frontend/src/test-support/async.ts`，只包含无状态 defer/flush；每次 defer 新建 Promise 与 resolve/reject，flush 保持原先一个事件循环周期的语义，不新增固定时序余量。

| 原支持函数 | 接替与消费者 |
|---|---|
| defer 的相同副本 | list-load.spec、system/user、config、permission-change-log 的 hook.spec → 共享 defer |
| 同样返回 setTimeout Promise 的 flush | 上述消费者及 biz-domain、resource-operation、service-interface → 共享 flush |
| grant/utils/hook.spec 的 async flush | 保留原形态，不因名称相同改变微任务层次 |

所有原案例与断言保留。请求先后、拒绝 Promise、错误提示、loading/total、用户二次确认与页面接线仍由消费者验证，没有用 helper 单测替代业务证据。7 个 spec 与新增 helper 合计 1587→1532 行，净减 55 行；声明与展开案例未减少。

其他候选核对结论：grant-plan.spec 已有 makeRecord/summaryOf/addChangeOf/dialogOf/slotOf 局部工厂，授权新增、修改、删除、替换、主子关系各有不同安排与副作用，不再套统一开关模板。grant-keys 的 DOM 安全、分隔符碰撞、null/undefined、角色命名空间分别保留；角色管理仅 BASIC_ROLE 与角色分配 BASIC_ROLE/PERSONAL 的消费者边界不合并。前后端独立 golden 未删，未改生产算法、支持输入或安装新依赖。

## 验证

- 原始 `pnpm --dir frontend test`：41 文件、484 案例通过；`pnpm --dir frontend typecheck`：通过。日志 `.tmp/testing-simplification/frontend-before.log` / `frontend-typecheck-before.log`。
- 最终相同测试命令：41 文件、484 案例通过；最终 tsc + vue-tsc 通过。日志 `frontend-final.log` / `frontend-typecheck-final.log`。
- `pnpm --dir frontend exec eslint --max-warnings 0 <受影响文件>` 和 `pnpm --dir frontend exec prettier --check <受影响文件>`：通过；只格式化实际修改文件，没有全仓 autofix 或 UI/CSS 改造。
- 替换期间用户页 import 曾误插入说明注释，Vitest 的用户列表竞态案例与 TypeScript 同时发现未定义 helper；已恢复注释并正确导入，最终完整检查通过，不隐去失败或禁用案例。

原/新 Vitest wall time 为 12.79s/9.41s，执行时系统负载不同，不将差值宣称为性能提升；本批收益是减少重复维护。

## 本地两轨核验

代码轨：共享函数与原副本等价，Promise 状态按调用独立，mock/消费者动作/断言未抽走；无多开关框架或新增测试平台。文档轨：按能力保持契约，候选保留理由、真实执行数和检查边界明确，技能在真实任务中未促成无价值新增或跨消费者合并。无 P0–P3 遗留发现，无新增待决取舍。计划级最终回归由 T-ACCESS-076 汇总。
