---
doc_type: task
id: T-PERM-044
title: 四棵树（角色/组织/菜单/资源实体）move 并发成环窗口与递归 CTE 遇环不收敛统一加固
status: done
plan: ""
domain: permission-center
design_refs:
  - docs/design/frontend/role-manage.md#§8
  - docs/design/permission-center/api-contract.md#§5.2
  - docs/design/access-service-architecture.md#§17
  - docs/design/access-service-rebuild-runbook.md#§3
depends_on: []
blocks: []
acceptance:
  - "四棵树（abstract_role / sys_org / sys_menu / resource_entity）move 的 check-then-update 并发窗口统一收口：根治方案二选一并四树一致——递归 CTE 改 UNION 去重（遇环自终止，不挂连接）或 move 对涉及行按 id 排序加锁 / 树级 advisory lock（消除窗口本身）。resource_entity 为 T-PERM-028 复评扩入（2026-08-30 定案）：moveResource 的跨类型/防环校验同为 check-then-act 无锁，子孙查询 selectDescendantIdsBatch 同为 UNION ALL 递归 CTE、后果同构"
  - "环路一旦落库后的自愈/检测能力：任一树出现 parent 环时树构建与鉴权查询不挂死（TreeBuilder 已有 visited 防环，缺口在 SQL 侧递归 CTE），并能定位环节点供订正"
  - "回归测试：PgIT 用 JDBC 制造 2-环后调用子孙/祖先递归查询，锁定不挂死且行为确定；并发交叉移动的窗口用例（可选，视根治方案；resource_entity 侧 T-PERM-028 已有 move 防环单测+PgIT 语义锁可复用）"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-04
---

# T-PERM-044 四棵树（角色/组织/菜单/资源实体）move 并发成环窗口与递归 CTE 遇环不收敛统一加固

> 状态：done（2026-09-04 收口）
> 依赖：无（独立维护债）
> 前置验收：见 acceptance

## 背景

T-PERM-022 为角色域 moveRole 补齐了环路防护（自身/子孙拒绝 20050，`ROLE_PARENT_INVALID`）与 sync/full-sync 同款判定，但防护为 check-then-update 非串行：两个写请求在同一瞬间交叉移动同一子树（A 挂 B 下、同时 B 挂 A 下）时双双通过校验、双双落库，数据库出现环。admin 域组织树（`ORG_PARENT_CYCLE` 10108）与菜单树（`MENU_PARENT_INVALID` 10207）为先例同构写法，同样未处理并发窗口。

后果放大项：四棵树的子孙/祖先查询均为 `UNION ALL` 递归 CTE，且全仓无 `statement_timeout` 配置——环一旦落库，鉴权链路在用的递归查询（如 `selectDescendantIdsBatch` / `selectAncestorGroupRoleIds`）在环上不收敛，可挂死连接占满连接池；`TreeBuilder` 有 visited 防环（树构建不挂），缺口在 SQL 侧。

## 范围

- 统一评估并落地：UNION 去重自终止（止损，不解决窗口）或行锁/advisory lock（根治窗口）或两者组合，四棵树同构统一。
- 全仓 `statement_timeout` 兜底策略评估（可选，随方案定）。
- 不改各树既有的类型一致/环路业务校验语义（T-PERM-022 已落地部分保持不变）。

## 设计口径

1. **根治窗口 = 树级分布式锁（Redisson，2026-09-04 定案变更）**：`TreeWriteLockSupport.lockTreeWrites`
   取 `RLock`（key `accessmesh:tree-write-lock:{treeKey}:{tenantId}`，treeKey 1-4 显式编号），
   `lock()` 不带 leaseTime 由 watchdog 自动续期（30s/10s 续，防持锁进程崩溃死锁）；**释放挂
   `TransactionSynchronization.afterCompletion`**（事务提交/回滚后同线程 unlock），堵死「解锁先于
   提交」缝隙；unlock 失败仅告警由 lease TTL 兜底。已知边界（定案接受）：watchdog 网络分区/JVM
   长停 >30s 时锁逐出而事务仍在跑（窗口复现，概率极低）；Redis 不可用时写入口 fail-closed。
   正确性依赖 READ_COMMITTED（语句级快照），改 REPEATABLE_READ 需把锁提到事务外层。行锁
   （FOR UPDATE）不采用——正确性要求锁全树行集（只锁移动节点+新父挡不住深交叉场景）。首个实现
   为 `pg_advisory_xact_lock`（随事务原子释放），同日定案变更切换为 Redisson；advisory 路径必须
   经 MyBatis mapper 执行才有效（JdbcTemplate 直连实测 autocommit 不关、锁语句结束即释放），
   留作语句级锁方案的坑位登记。
2. **树表全部写入口无条件持锁**：角色 `updateRole`/`moveRole`/`sync`/`full-sync`、组织
   `updateOrg`、菜单 `updateMenu`、资源 `updateResource`/`moveResource`/资源实体
   `sync`/`full-sync`、服务接口同步 `syncInterfaces`。外部评审 P1 收口：update(entity) 全列
   回写含 parent，「不带 parent 的普通编辑」按条件持锁时，无锁回写可静默回滚并发移动、经
   两步合法移动+回写可闭合成环——故无条件持锁（锁内读写串行，回写旧值不可能覆盖并发变更）。
   组织/菜单请求携带 parent 时锁内重读自身行重判换父（双轨评审补齐：防锁前快照漏校验语义）。
   资源实体同步判环先于版本写入（双轨评审补齐：拒绝不推进同步版本，full-sync 内存图判环 +
   已应用边镜像，经 cyclePreChecked 跳过逐项 DB 判环防 N+1）。锁先于任何树结构校验查询；
   事务外调用 fail-fast（IllegalStateException）。持锁事务期间并发写排队——管理操作低频，可接受。
3. **递归 CTE 止损 = UNION 去重为主 + subtreeHeight 深度上限**：四树 12 处 `UNION ALL` 递归 CTE
   中 11 处改 `UNION`（重复行不进工作表、迭代自终止，环上返回全部可达节点；含 `original_id`/
   `root_id` 分组列的批量查询按组合行去重、分组语义不变）。例外 `SysMenuMapper.selectSubtreeHeight`：
   递归列含 depth 每层新行永不重复，去重无法终止，改加 `depth < 100` 上限（对齐
   `OrgVisibilityQueryMapper` 既有先例；返回值确定为 100）。
4. **内存递归防环 = visited（对齐 TreeBuilder/wouldCreateCycle 先例）**：祖先链上溯 while
   （`OrgDomainServiceImpl`/`MenuDomainServiceImpl.batchGetAncestorIds`）重访即截断返回已收集链；
   向下树构建（`OrgServiceImpl.keepMatching/buildTree`、`MenuServiceImpl.buildTree`）重访节点按叶子
   返回——后者在当前调用图上环成员经 scopeToSubtree 裁剪/根不可达，属纵深防御。受影响的既有
   测试类（单测轨与容器轨 PgIT）构造参数同步补 TreeWriteLockSupport mock。
5. **环检测 = 订正 SQL 进 runbook，不做自动自愈**：每树一条同构检测 SQL（depth<200 防自不收敛）
   登记于 rebuild-runbook「常见问题」，输出环上节点 id；断哪条边是业务决策，人工订正后重跑检测
   为空即收口。检测 SQL 与 PgIT 用例同源保持不腐烂。不做内部检测端点/常驻检测方法（无消费方，
   过度设计）。
6. **statement_timeout 不做**：CTE 全部止损后已知挂死面已消除；全局超时会误杀 full-sync 等长
   事务，引入新的不可预期失败面。

## 登记追加（T-ADMIN-021 收口登记）

- `OrgServiceImpl.treeOrgs` 读路径两处向下递归（`keepMatching` 名称剪枝 / `buildTree` 子树构建）
  已随本任务加 visited 防环覆盖。执行中核实追加同构面：`MenuServiceImpl.buildTree`（菜单树构建
  同款无防护）、`MenuDomainServiceImpl`/`OrgDomainServiceImpl.batchGetAncestorIds` 祖先链 while
  （环上 JVM 死循环，与 SQL 挂死同后果）——一并收口。

## 完成记录

- **代码**：新增 `TreeWriteLockSupport`（infrastructure，Redisson 实现 + afterCompletion 释放；
  首版 advisory mapper 路径随定案变更删除）；5 个写入口接锁（对外接口不变，切换零改动）；
  11 处递归 CTE 改 UNION 去重 + `selectSubtreeHeight` 深度上限；5 处内存递归加 visited。
- **测试**：新增 `TreeCycleHardeningPgIT`（容器轨 5 用例：四树 2-环下 SQL 递归查询返回且结果
  确定、组织/菜单祖先链截断、锁互斥（事务提交前同键 tryLock false、afterCompletion 释放后 true）、双线程同瞬交叉移动恰好
  一成一败且 2-环不落库、环检测 SQL 定位全部环节点）+ `AncestorChainCycleGuardTest`（单测轨
  3 用例：组织/菜单祖先链环截断 + 正常链不受影响；锁互斥用例随 Redisson 切换同步改写）。旧实现下：递归 CTE 用例不返回（连接挂死）、交叉移动用例双成功（窗口
  未收口）、祖先链用例不返回（JVM 死循环）——回归锁有效。
- **文档**：architecture 新增 §17（四树防护定案，含锁选型与历史坑位注记）；api-contract
  §5.2 move 补并发语义、§6.2.2 补资源同步判环条款与版本语义、frontmatter 同步；role-manage
  §8 登记句改收口口径；rebuild-runbook 常见问题表新增环检测订正条目（含 SQL）。
- **双轨评审处置（同日）**：代码轨 P1（resource-entity 同步写 parent 无锁无判环，单事件可落环）
  经核实属实并补齐（上述 sync/full-sync 防护 + 用例）；代码轨 P2（updateOrg/updateMenu 锁前
  快照静默回写并发移动 + level 记账漂移）经核实属实并修（锁内重读 + 用例）；文档轨 advisory
  残留清扫（runbook 环检测行、PgIT 类注释、OrgServiceImpl 旁注）、architecture/role-manage
  frontmatter 回写、任务卡编号与计数订正、§17.2 补排除句。资源同步测试随判环前移同步修订
  dependencyMissing 用例（applyVersion never 断言锁「拒绝不推进版本」语义）。
- **codex 外部评审处置（gpt-5.6-sol xhigh，同日）**：P1 普通更新全列回写 parent（含
  ResourceSyncHandler 接口同步批量面）经核实属实（两步合法移动+回写可闭合成环）——按用户
  定案改为树表全部写入口无条件持锁（updateRole/updateResource/updateOrg/updateMenu/
  syncInterfaces 五处扩面）并补入口 verify 用例；P1 fullSync 内存判环后逐项 DB 判环 N+1
  属实——doSyncOneInternal 加 cyclePreChecked 参数跳过并补 never 断言回归锁；P2 并发用例
  无确定性窗口制造（黑盒无法在校验与写入间注入屏障）——crossMove 用例定位精化为行为
  特征化（注释说明两道防线），入口接锁确定性由各单测 verify 承担；P3 PgIT 类注释方案句
  上轮 patch 断言失败未落盘——修正。
- **codex 二轮复评处置（gpt-5.6-sol xhigh，2026-09-05）**：P1 updateOrg/updateMenu 锁晚于
  首次实体读取（锁只串行写、未覆盖旧快照产生，普通编辑仍可在读取-拿锁-写回窗口覆盖并发
  移动成环）经核实属实——锁提前到首次读取之前（对齐 updateRole/updateResource 位置），
  带分支的锁内重读随之去除（快照已在锁内），两用例改 InOrder 顺序验证（锁先于 selectValidById）；
  P2 N+1 回归断言挂在必然成环项上（外层判环 continue 后不进 doSyncOneInternal，锁了个空）
  属实——补「合法 parent 过外层判环实际执行 update」用例（撤销 cyclePreChecked 时失败）；
  P2 verify 覆盖不全属实——组织无 parent 用例补锁断言（旧条件锁实现下失败）、角色/资源
  fullSync 分别补锁 verify；P3 ServiceSyncAppServiceImpl 全限定名改 import。
