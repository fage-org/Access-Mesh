---
doc_type: plan
title: codex 项目级设计体检处置批次
status: proposed
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md
  - docs/design/permission-center/core-flows.md
  - docs/design/access-service-architecture.md
  - docs/design/schema/access-service.sql
tasks:
  - T-PERM-052
  - T-PERM-053
  - T-PERM-054
  - T-API-002
  - T-ACCESS-029
acceptance: "体检新发现（P1×1、P2×2）逐条代码级核实并定案后拆为可追踪任务；五条预置题的执行口径已定并回写对应任务卡/架构文档；暂缓项与演进方向单独登记不混入执行面；各任务实现按任务卡验收执行。"
last_updated: 2026-09-05
---

# codex 项目级设计体检处置批次

> 状态：proposed
> 来源：2026-09-05 codex（gpt-5.6-sol xhigh，read-only，探索/核实全经 luna 子代理双交叉）项目级设计体检报告；全部结论经代码级逐条核实与逐项讨论定案。报告全文仅存于当日会话历史（未归档），其 §A/§B 编号以本计划任务拆分表为准
> 约束：本计划只建立任务跟踪入口与定案登记，不授权实现；各任务进入 in-progress 前按任务卡验收执行

## 目标

将体检报告 §B 新发现（P1×1、P2×2）与 §A 预置五条的定案口径转为可追踪任务；暂缓项与演进方向单独登记，不混入执行面。

## 非目标

- 不在本计划内修改 `docs/design/` 权威契约（各任务 design_writeback 承担）。
- 不在本计划内改代码。
- 不处置体检豁免清单内的已登记技术债（T-PERM-039/045/047/048/050、T-ADMIN-026、T-FE-023 等）。

## 任务拆分

| 任务 | 来源 | 范围一句话 | 状态 |
|---|---|---|---|
| [T-PERM-052](../tasks/T-PERM-052.md) | §B P1 | 资源同步双向所有权边界（sync 接管拒绝 + 管理面 SYNC 行只读含级联守卫 + FULL 删除归属核验） | ⚙️ |
| [T-PERM-053](../tasks/T-PERM-053.md) | §B P2-3a | service-config 同步 ApiItem.operationCode 无效字段删除（前后端同批锁步） | ⚙️ |
| [T-API-002](../tasks/T-API-002.md) | §B P2-2 | perm-sdk 补齐 auth/query-resources 与 auth/query-scopes 调用入口（含内部 id 字段族全裁 + 排查页同批改造） | ⚙️ |
| [T-ACCESS-029](../tasks/T-ACCESS-029.md) | §A-5 | bootstrap 固定图授权收缩通道——软删墓碑三分判定 | ⚙️ |
| [T-PERM-054](../tasks/T-PERM-054.md) | §B P2-3b | 手工 API 映射绑定非 API 资源处置——**暂缓**（关联权限自动授权方向待讨论） | ⚙️（暂缓） |

## 既有任务卡口径回写（非本计划新建）

| 任务卡 | 回写内容 |
|---|---|
| [T-PERM-051](../tasks/T-PERM-051.md) | 投影范围=全部三族；业务键=`{typeKey}:{typeCode}` 复合；TYPE_DEFINITION 加入创建保留清单 |
| [T-PERM-019](../tasks/T-PERM-019.md) | 重基线：D1 标完成（已落地）、D3 废注解收窄核对、D2 BusinessKeys 收敛为唯一实质交付 |
| [T-ADMIN-025](../tasks/T-ADMIN-025.md) | +ADMIN_FILE 加入创建保留清单；登记「新文件夹需首传才可授权」后续优化点 |
| [T-PERM-046](../tasks/T-PERM-046.md) | 设计项定案：create 加可选 global（默认 false）、补集语义维持、global 不可变 |

## 登记项（非任务）

- `access-service-architecture.md` §14.2：固定图收缩通道墓碑三分定案（T-ACCESS-029 承接实现）+「固定图→租户初始化引擎」演进方向登记（2026-09-05，待启动时另行设计）。
- 体检报告其余子项处置记录：§B P2-3 的「Controller MENU 映射示例」子项经核实不存在，撤回不立任务；§B P2-2 附带发现 `QueryScopesResp` 等对外响应含内部行 id 字段族，随 T-API-002 全族裁剪（含前端排查页同批改造）。
- 复评审（2026-09-05 双轨子代理）补强已并入各卡：T-PERM-052 级联守卫/异源反查/markStatus 口径、T-PERM-053 前后端锁步前提（严格 mapper）、T-API-002 契约测试计数、T-ACCESS-029 NULL 语义与重建边界。

## 验收标准

- 五张新任务卡与四张既有卡口径回写落盘（本批次完成）。
- 各任务实现按其任务卡验收执行；全部收口后本计划归档。

## 当前进度

- 2026-09-05：批次建立；同日双轨复评审（代码正确性/安全边界 + 规范符合性/文档一致性）结论并入各卡与看板，T-API 编号 003 修正回收为 002。

## 归档条件

- tasks 清单内四张执行任务全部收口、暂缓卡按其启动门禁另行处置后，随最后一张任务收口归档。
