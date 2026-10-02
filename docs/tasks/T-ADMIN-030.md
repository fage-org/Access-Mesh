---
doc_type: task
id: T-ADMIN-030
title: 角色重新指派对既有绑定的语义收口
status: done
plan: docs/plans/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md §10（role 能力：user-role 分配）
depends_on: []
blocks: []
acceptance:
  - "同用户同角色按 relationId 区分绑定；完全相同幂等跳过；null 关系改期更新；不同关系新增；非空相同关系改期明确拒绝"
  - "assignRole 接收窗口参数、assignRolesBatch 新建固定无限期两形态同批覆盖；装载既有绑定按有效期口径与拍板一致"
  - "回归锁：过期绑定重新指派在旧实现下「返回成功但仍失效」、新实现下按拍板语义处理（实证旧失败）"
  - "契约 §10 写明重指派语义（幂等边界 + 窗口/关系变更处置），先撤销再分配的恢复路径作为对照写明"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-02
---

# T-ADMIN-030 角色重新指派对既有绑定的语义收口

## 背景

承接 [Q-047](../pending-problems.md#q-047)：`UserManageAppServiceImpl.assignRole/assignRolesBatch` 按 userId+roleId 内存去重命中即跳过；`UserRoleMapper.xml` 装载既有绑定不滤有效期，uk_user_role 含 relationId 但内存去重未区分。后果：过期绑定重新指派返回成功但仍失效；未来窗口及 relationId 变更也可能被吞。先撤销再分配可恢复，但用户以为授上了实际没授上。

## 范围

两入口的既有绑定装载、去重、更新与新增判定；互斥校验、事务回滚与权限变更失效；契约 §10 口径落账。sync 多重集窗口语义不在本次变更范围。

## 当前口径

重指派按用户、角色与 relationId 定位绑定：全部相同的请求幂等跳过；null 关系的有效期变化更新旧行；不同关系新增；非空相同关系的有效期变化明确报错，提示先撤销再分配。assignRolesBatch 仍以无限期为目标，因此 null 关系的有限期绑定会改为无限期。契约见总册 §10.4；实现、验证与设计回写已完成。

## 非目标 / 遗留

- sync/full-sync BIND 的多重集窗口语义（另一契约面，维持现状）。

## 验收对照

- [x] 两入口按关系键区分绑定，完全相同重试幂等；null 关系改期更新，非空同关系改期报错，不同关系新增。
- [x] 既有绑定装载保留过期及未来窗口；assign 按请求更新，batch-assign 固定无限期。
- [x] 过期重指派旧实现实证不通过，修复后有效角色缓存与用户角色展示查询均恢复；回归覆盖其他关系保留、批内重复重试、未来窗口、互斥拒绝及事务故障回滚。
- [x] 契约 §10.4 回写幂等、覆盖边界、错误码、失效与恢复路径；CHANGELOG 登记对外行为变化。

## 完成记录

- 2026-10-02：`mvn test -pl access-service -Dtest=UserRoleReassignmentPgIT` 在业务修复前运行，6 项，5 个断言失败、1 个旧窗口导致的互斥异常，0 跳过；确认旧实现不能通过行为锁。
- 2026-10-02：`mvn test -pl access-service -Dtest=UserRoleReassignmentPgIT,UserManageAppServiceImplTest,RoleMutexGuardPgIT`，43 项，0 失败、0 错误、0 跳过。覆盖新增角色重指派 PgIT、用户管理单测及原互斥 PgIT。
- 2026-10-02：`mvn test -T 1C`，2,399 项，0 失败、0 错误、0 跳过，`BUILD SUCCESS`；包含 heavy 组与 E2E（27 项），耗时 15 分 18 秒。完整输出落盘后按各 execution 汇总行聚合，未使用跳过开关；开跑前 9100 空闲，构建期间未修改源码。
- **代码轨结论**：无未处理缺陷。实证通过：角色管理门禁仍在装载与写入前；null 关系改期受本地投影保护；Mapper 租户/未删除/目标类型/null 关系过滤；批量更新显式写 null，保留创建字段；更新后的窗口经 DB 新鲜读参与互斥检查，其他关系同值窗口保留；互斥拒绝与插入故障均回滚更新；真实变更提交后失效。新增方法由两分配入口共享，未增加配置或策略层。存疑待决项：无。
- **文档轨结论**：无未处理缺陷。实证通过：契约、唯一索引与实现对齐；两入口窗口形态、非空关系错误码及先撤销再分配路径已说明；任务/计划/看板状态一致；遵守领域服务复用、批量访问及 ItInfra 容器测试轨道；任务卡和契约轮次词扫描无命中。存疑待决项：无。
