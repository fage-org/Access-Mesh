# 2026-09-10 归档批次：权限排查前端设计退役

## 归档原因

T-PERM-059「权限视图/排查删除重设计——删除收口」（2026-09-10 三项用户定案）：permission-view 七端点与 /auth/query-permission-tree 全删，前端排查页（permission-query）整目录同批删除，本设计文档随之退役。

## 归档内容

- `permission-query.md`：前端权限排查页设计（T-FE-013/T-FE-019/T-PERM-033 收口形态）。关联登记卡 T-FE-043 已 cancel（重做考虑事项随卡归档）。

## 后续

排查/视图新形态基于统一引擎结果模型另立任务承接（grill 级产品讨论，待用户启动）；设计基线以 `docs/design/permission-center/api-contract.md` §5.8 与 `implementation.md` §3 为准。
