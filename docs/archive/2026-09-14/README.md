# 2026-09-14 归档批次

## 批次一：access-service 能力包融合计划终态归档

## 归档原因

T-ACCESS-032~041 十任务全部 done（末卡 041 规则与技能文件重写于 2026-09-14 收口），验收五条达成、capability-structure §9 定案修订对照全部落地；本地双轨评审 + claude/grok/codex-sol 三通道外评全处置后，用户确认批次定稿并归档。

## 本批次内容

| 内容 | 去向 |
|------|------|
| access-capability-fusion-plan.md | 本目录（status: archived；十任务 T-ACCESS-032~041 全 done） |
| 任务卡十张（T-ACCESS-032~041） | 本目录 `tasks/` |

## 终态权威去向

- 结构契约：`docs/design/access-service-capability-structure.md`（adopted——§8 归属清单十项裁决、§8.4 架构断言五测试）
- 单册设施：`engine.constant.OperationCode`（034）、`infrastructure.enums.AccessErrorCode`（038）、`infrastructure.cache.AccessCacheCatalog`（039）
- 契约与内档：`docs/design/access-service-api-contract.md` 总册（040）+ `docs/design/engine/` 三册
- 规则与口径：`.claude/rules/permission-coding-standards.md`（041）+ AGENTS.md 指针表；域叙事词汇=管理面/权限面（registry 2026-09-14 行）
- 定案：decision-registry 2026-09-13 融合定案行 + 038/039/041 拍板行
- 遗留登记：pending-problems（Q-001 URL 两风格、Q-006 滚动发布/指标面、Q-009 冻结白名单收敛、Q-010 死方法等）

## 批次二：frontend-phase2 与 design-audit-followup 计划归档（轻量清扫）

### 归档原因

frontend-phase2（2026-06-29 立项）与 design-audit-followup（2026-09-05 立项）的执行任务全部 done，仅剩暂缓项——T-PERM-035/036（design-review §11 暂缓门禁，等 PM 重申）与 T-PERM-054（关联权限自动授权方向已定 2026-09-09、方案未定）随本批次脱出计划挂任务看板（plan 字段 —、门禁不变），两计划按剩余范围归档（2026-09-14 用户拍板「轻量清扫+计划归档」）。无计划归属的终态卡 T-PERM-065 一并单卡归档（归档滞留禁令补账，2026-09-12 收口后滞留 docs/tasks/）。

### 本批次内容

| 内容 | 去向 |
|------|------|
| frontend-phase2-plan.md | 本目录（status: archived；T-PERM-025~031/033/034/037/040/041、T-FE-036/038~040、T-ADMIN-021 十七卡随迁 tasks/） |
| design-audit-followup-plan.md | 本目录（status: archived；T-PERM-052/053、T-API-002、T-ACCESS-029 四卡随迁 tasks/） |
| T-PERM-065.md | 本目录 tasks/（单卡归档，原无计划归属） |

### 暂缓项去向

T-PERM-035/036/054 留 `docs/tasks/`（⚙️ proposed、plan 字段 —，看板行已标注脱出来源）；重启经 PM 重申 / 方案定案后直接以看板任务推进，不再依赖已归档计划。

## 批次三：T-FE-044 单卡归档

| 内容 | 去向 |
|------|------|
| T-FE-044.md（Phase 3 补遗联调：资源依赖 3.4 mock→真实收口） | 本目录 `tasks/`（done 即单卡归档——Q-011/Q-012 转出任务，无所属计划） |

关联收敛：pending-problems Q-011/Q-012 随卡 done 收敛入索引；业务页 mock 全部退役（mock/ 仅剩 login.ts 开关门控件与 asyncRoutes.ts 模板参考件）。

## 批次四：轻量代码清扫（前端 GROUP_ROLE 死面 + 工具类改名，2026-09-14 用户拍板）

> 无归档物、无任务卡载体（仿批次二 Q-010 顺带删形态——本批次即清扫本身为载体）；零行为变更。

### 内容一：GROUP_ROLE extra-roles 前端死面全删（推翻 T-PERM-043 实现期「代码保留不删」处置）

T-PERM-043 删除后端 extra-roles/* 三接口与 GROUP_ROLE 写入口时，前端死码曾按「待 role_inclusion 立项恢复」保留（仅代码注释口径，decision-registry 无对应定案行）；本次清扫全删（恢复走 git 历史）：

- `api/role-manage.ts`：`listExtraRoles/addExtraRole/removeExtraRole` 三封装 + `RoleSummaryResp/GroupRoleExtraRolesQuery/GroupRoleExtraRoleReq` 三类型
- 角色页：`hook.ts` 死分支（loadTree/handleNodeClick/handleDelete 的 GROUP_ROLE 支 + 三函数 + 两 ref）、`index.vue` 额外基本角色面板（script computed 区 + 模板区块 + 样式）、`perms.ts` `ROLE:ASSIGN/ROLE:REVOKE` 两键（后端操作已零引用）、`mock/login.ts` sec 矩阵两串
- 授权页：`SubjectTreePanel.vue`（`handleNodeExpand` 整函数、`requestSelectGroup` 死 emit、分组/基础角色 tag）、`subject-tree.ts` `buildExtraContainer`、`types.ts` kind 收窄（删 `EXTRA_CONTAINER/EXTRA_ROLE` 与 `expandedFromGroup/groupRoleName/expandedLoaded/fromGroupRoleName` 四字段）、`hook.ts` 死高亮支、`subject-tree.spec.ts` 对应 2 用例

**外部评审处置**（2026-09-14，claude + grok 双通道，各自 P0~P2 全零）：①claude P3-1 = grok P3（双通道同源交叉证实）——批次初判「requestSelectGroup 零消费」有误，父组件为 kebab 形 `@request-select-group`（camel 检索漏扫），消费链 `onSelectGroup`/`groupHint`（hook + index.vue 绑定传参）与 `GrantMatrixPanel` 的 groupHint prop/`v-else-if` 展示支已在处置提交补删（空态简化回 `v-if="!hasSubject"`）；②claude P3-2——`engine/implementation.md` frontmatter 历史链内 2026-09-07 条目被机械改名污染，已还原旧类名 `BusinessKeys` 并加更名注记、头部补登 Q-004 改名条目；③grok 存量观察（同性质随处置修）——`capability-structure.md` §sync 融合映射表源列历史 FQCN `permission.util.SyncKeyCodec` 被机械替换，已还原并注记现名。两通道均确认改名提交纯机械（归一多重集比对/blob 精确 diff/skills 双副本 SHA256 一致）、死面删除可达性判断成立（`filterVisibleTree` 只放行 BASIC_ROLE）。grok 首跑 40 轮耗尽未出报告，80 轮重跑产出。
- 设计文档回写：`role-manage.md`（§1/§2 布局图/§4.2/§5 注记/§6/§7 表与门禁/降级/mock 矩阵）、`permission-grant.md`（§0-13/§1.1/§2.1 表/§6.5）

### 内容二：Q-004 工具类改名（按 project-rules §6.2「XxxUtil 去末尾 s」）

- `BusinessKeys` → `BusinessKeyUtil`（perm-common；golden 锁测试随类更名 `BusinessKeyUtilParityTest`）
- `SyncKeyCodec` → `SyncKeyCodecUtil`（access-service sync；两测试类随更名）
- 同批替换 34+17 文件：代码+测试+`TypeDefinitionMapper.xml` 注释+skills 双副本（accessmesh-patterns）+AGENTS.md+活设计文档；decision-registry 带日期历史行与 docs/archive 不改写

### 验证

- 前端：vue-tsc 0 错、vitest 229/229（231−2 为删除的 buildExtraContainer 用例）、eslint 0 问题
- 后端：全仓 `mvn clean compile` 通过；全量回归 `mvn test -T 1C`（收口形态含 E2E）全绿——perm-common 30（`BusinessKeyUtilParityTest` 更名后 golden 锁绿）/ common 65+15+10+106 / access-service 1232 单测 + 210 容器 / e2e 14，BUILD SUCCESS 6:36（日志整文件落盘解析，零失败字串）

### 关联收敛

pending-problems Q-004 → 已收敛索引（关联本条目）。

## 批次五：T-PERM-066 单卡归档

| 归档物 | 去向 |
|---|---|
| T-PERM-066.md（operationCodeKey 族大小写口径统一——raw 严格化） | 本目录 `tasks/`（done 即单卡归档——Q-003 转出任务，无所属计划） |

Q-003 随卡收敛（pending-problems 已收敛索引）；定案见 decision-registry 2026-09-14 行，契约总册 §2.4/§2.5 集中注记。
