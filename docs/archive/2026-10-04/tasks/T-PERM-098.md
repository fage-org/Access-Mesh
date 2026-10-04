---
doc_type: task
id: T-PERM-098
title: 操作位写入与准入消费一致性边界定案与收口
status: done
plan: docs/archive/2026-10-04/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md §12（resource 能力：操作定义）+ §25（操作准入协议）
  - docs/design/engine/implementation.md §3.3（执行管线/prepareAdmissionClauses）
  - docs/design/frontend/resource-operation.md §4（操作权限 CRUD）
  - docs/design/iam-task-closure.md §2.6（T-PERM-077 取舍的部分替代）
depends_on: []
blocks: []
acceptance:
  - "写侧创建及更新显式 binaryBit 拒绝非正数单比特；准入消费保持严格配置故障语义"
  - "契约 §12 明确替代 T-PERM-077 的 binaryBit 不新增数值校验取舍与依据；inheritMask 负数表示维持不是错误"
  - "内置及自定义类型非法操作位统一写前拒绝 20044；合法自定义类型补种与 AUTHORITY_ROOT CHECK 保留；存量坏位的 20071 触发条件与快照失败范围有契约口径"
  - "回归锁覆盖：碰撞位写入的准入快照构建路径（旧实现下失败/未定义）"
  - "契约 §12/§25 与 implementation §3.3 口径同步"
  - "前端校验正数单比特、63 位范围和同类型占位，新增自动生成最小空闲单比特，高位十进制字符串无损，位用尽阻止新增"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-03
---

# T-PERM-098 操作位写入与准入消费一致性边界定案与收口

## 背景

承接 [Q-050](../../../pending-problems.md#q-050)：实施前 `OperationAppServiceImpl.createOperation/updateOperation` 未限制 `binaryBit` 为单比特，operation_permission 表也无对应 CHECK；内置类型跳过补种、可追加不重复的非幂位。而 `QueryExecutionEngine.prepareAdmissionClauses` 拒绝覆盖坏位行——直接 API 写入位 3 后，与其有效位相交的准入要求可能触发 20071、相关快照构建失败。前端数值输入已有幂位校验，字符串分支跳过校验。该问题是 T-PERM-077 之后新增严格准入消费引入的影响。

## 范围

后端操作创建/更新的输入约束、直接 API 写入与准入快照回归、前端操作位校验及自动填写、契约与实现设计同步。数据库约束不扩展。

## 当前口径

2026-10-02 采纳写侧约束方案 A，并纳入前端自动填写可用位。长期边界见契约总册 §12/§25、引擎实现 §3.3 和前端资源与操作定义 §4；T-PERM-077 的取舍仅在 binaryBit 数值约束上被取代，inheritMask 符号与缺省语义保留。

## 验收对照

- [x] 创建/更新显式 binaryBit 在权限门禁后、写库前校验，拒绝 null（服务直接调用创建）、零、负数及多比特值；更新 null=不修改。合法边界 1/2^62 和负 inheritMask 保留。
- [x] 契约 §12 明确 T-PERM-077 取舍的替代范围及依据；§25.4 与引擎 §3.3 明确相关坏位、无关坏位、在线/快照失败范围。
- [x] 直接 HTTP API 对内置类型形态写位 3 返回 20044，操作行不产生/不修改，真实快照和在线准入仍成功；自定义类型补种及原 CHECK 由既有真库闭环验证。
- [x] 存量坏位 12 不覆盖 VIEW=2 时快照成功；改为位 3 后快照与在线端点均返回 20071。
- [x] 前端新增读取完整目录并自动填最小空闲位；校验重复位、正数单比特、范围和数值精度；高位字符串无损；位用尽、加载失败、类型切换均阻止错误提交。
- [x] 设计、任务看板、所属计划与 Q-050 索引同步；无新增下游依赖，所属计划仍活跃，本卡保留原位。

## 完成记录

- **后端回归锁**：2026-10-02，`mvn test -pl access-service -DskipTestcontainers=true -Dtest=OperationAppServiceImplTest,AdmissionStagesTest,OperationCodeCaseValidationTest,OperationPermissionWireFormatTest`，77 项通过。撤去新增写侧校验后运行 `mvn test -pl access-service -Dtest=InterfaceAdmissionPgIT#should_keepSnapshotBuildable_whenApiRejectsCollidingOperationBits`，位 3 经签名管理请求实际落库，真实快照在 `prepareAdmissionClauses` 抛 `AdmissionConfigurationException`（1 项预期错误），证明锁住原缺陷。恢复校验并强制刷新源码编译时间后，2026-10-03 隔离运行 `mvn test -pl access-service -Dtest=OperationAppServiceImplTest,InterfaceAdmissionPgIT`，57 项通过。
- **全量回归**：2026-10-03，`mvn test -T 1C`，全部模块 SUCCESS；352 个测试类、2420 项测试，0 失败/0 错误/0 跳过，包含 heavy 与 E2E。E2E 三组共 27 项通过。输出整文件落盘后按各测试类汇总，不重复计入模块聚合统计。
- **前端验证**：2026-10-02，`pnpm test`，41 个测试文件、484 项通过；`pnpm --config.shell-emulator=true build`（Windows 脚本兼容运行）、`pnpm typecheck`、`pnpm lint` 全部通过。新增自动生成/校验及提交回归覆盖 CRUD 后分配 16、空位复用、2^62 精度、63 位用尽、非法字符串/越界/不安全数字、重复占位和切类型后拒绝旧表单提交。
- **代码轨**：P0–P3 无未处理项；实证通过门禁顺序、写前拒绝、合法自定义类型联动、更新省略字段语义、准入严格防御、前端高位精度及并发唯一约束兜底。无存疑待决策项，无新增抽象/配置等可裁剪机制。
- **文档轨**：P0–P3 无未处理项；实证通过 §12/§25/引擎 §3.3/前端 §4 口径一致、T-PERM-077 部分取代、schema 未扩展、链接/轮次词扫描、任务与问题生命周期同步。无存疑待决策项。

## 非目标 / 遗留

- inheritMask 负数表示语义（非错误，维持）。
- 不自动清理存量坏位，不新增 DDL CHECK，不提供后端自动占号或并发重试分配机制。
