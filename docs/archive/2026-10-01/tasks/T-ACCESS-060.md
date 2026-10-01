---
doc_type: task
id: T-ACCESS-060
title: （ADM-T05）失效、TTL 边界与在途代次
status: done
plan: docs/archive/2026-10-01/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §5.3/§8.5（含 §8.5a 落地记录）
depends_on:
  - T-ACCESS-058
  - T-ACCESS-059
blocks: []
acceptance:
  - "§8.5 失效矩阵全链落地：授撤/角色归属与有效期/AUTO_DEP 改变、条件同 ID 改规则与启停、操作覆盖/定义变更、父授权撤销或结构变化、映射新增/改绑/停用/删除/FULL 缺失、服务模式/配置代次/协议切换——删除一个来源不清掉其他有效来源"
  - "条件关联服务反查按「引用条件的授权类型→该类型所需操作→映射服务」安全超集再按实际覆盖优化（不沿旧授权资源=API 资源联接）——markConditions 通道扩展反查并广播 serviceCodes（PermissionChangeAspect/PermissionChangeContext 改动面）"
  - "新快照 TTL、上游安全目录寿命与截止时间按 §5.3 重新推导并纳入启动校验（不宣称旧 30 秒边界自动覆盖新依赖）；回填保留读前令牌/剩余 TTL；N19/N21/N23 全绿（旧在途读取不覆盖新代次、保留加载去重/回源截止/失败关闭）"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-28
---

# T-ACCESS-060 （ADM-T05）失效、TTL 边界与在途代次

## 背景

设计 §8.5/§5.3（报告临时编号 ADM-T05）。新准入把业务操作覆盖转换为快照候选，依赖关系已变——不能沿用旧快照约 30 秒安全结论；即便采用新鲜操作定义，广播+TTL 仍非零延迟强一致（设计明示）。

## 范围

- 失效触发逐项反向测试（每个触发有负向用例）；网关 L1/回源截止与新目录边界同批推导；负缓存与节点能力处理。

## 非目标 / 遗留

- 专用短 TTL/版本化业务操作缓存为备选（须重做安全边界证明，设计 §5.3 非默认）——本卡默认新鲜数据库读取。

## 实现记录（2026-09-28 收口）

### 盘点结论（§8.5 六类触发的现状覆盖）

已由 058/059 就位、本卡核实无需改动：授撤/角色归属/AUTO_DEP（markRoles/markUsers→网关角色级租户清/用户级精确清）；操作定义/覆盖变更（`OperationAppServiceImpl` 三写入口按类型超集广播）；映射写路径（saveAll 单点/删除/FULL 全部 markServiceCodes+同事务代次 +1）；模式/启停切换（保存入口模式或状态变化即 +1+广播）；网关在途/去重/截止/失效代际（epoch 三组竞态测试既有）。

本卡修复的主缺口：**条件→服务反查沿 T-PERM-017 旧联接（`rrp.resource_entity_id = m.resource_entity_id`）在新映射模型下失真**——映射行 `resource_entity_id` 现指 API 登记实体、准入候选来自业务类型授权，旧联接结果普遍为空（条件改规则不广播任何服务）；且内联轨（授权页随记录编辑/回收）完全不反查，仅靠 15s TTL 兜底。

### 代码落点

- `RoleResourcePermissionMapper.selectServiceCodesByConditionGrantTypes`（新）+ 旧 `selectServiceCodesByConditionIds` 整体退役（mapper/XML/DomainService 三层同步）：安全超集＝引用条件的授权行 `resource_type`（全行 NOT NULL 含实例行）→ `operation_permission` → `resource_api_mapping.required_operation_id` → service_code，单 SQL 无实体联接。「再按实际覆盖优化」不做——网关对 serviceCodes 非空整租户失效，精确化只改变「是否广播」不改变失效范围，超集方向安全。
- `PermissionChangeAspect` flush 扩展（markConditions 通道）：conditionIds 非空时统一反查并并入 serviceCodes 广播——管理页 update/delete、内联编辑/回收、类型级联回收全部经 markConditions 自动覆盖；afterCommit 反查读已提交数据，失败仅本条广播缺失由 TTL 兜底（flush catch 全包裹）。
- `ConditionAppServiceImpl` 退役 per-caller 反查（`markServiceCodesForConditions` 删除，两调用点收敛为 markConditions）。
- `PermissionAdmissionAppService.SNAPSHOT_TTL` 常量公开（15s）+ `PermCacheBoundaryValidator` 方程锁：`MAX_SNAPSHOT_L2_TTL(10s) + SNAPSHOT_TTL(15s) ≤ MAX_WORST_CASE_STALENESS(30s)`，调大任一常量启动失败。
- `GatewayCacheCatalog`/`AccessCacheCatalog` Javadoc 按重推导口径更新。

### 边界重推导（§5.3 收口）

快照构建读源实核：事实族六目录（EFFECTIVE_ROLES/ROLE_PERM_SNAPSHOT/TYPE_VALUE/TYPE_CODE/CONDITION_RULES/ROLE_MUTEX_RULE）经引擎 L2_ONLY≤10s；操作定义（057 新鲜目录）、条件规则原文（装配器 `conditionMapper.selectValidByIds` 直读）、路由映射与服务配置含代次复读全部新鲜库读不占预算。最坏陈旧＝事实族 L2≤10s＋构建耗时≤5s（网关回源截止约束——expiresAt 在构建完成后计算，构建期事实年龄继续增长，外评修正补入算式）＋快照有效期 15s（网关 `isFresh` 按服务端 expiresAt 门禁命中——「不在投影完成后重新起算寿命」由此承载；L1 TTL 15s 仅作丢失广播兜底）＝30s＝30s 目标压线达标（0 余量）。回填保留读前令牌（`LoadToken` 取于回源前、提交前代际校验）与回源截止共享（既有机制，测试既有锁定）。

### 当前口径（三项定案）

1. **接收侧代次匹配检查：不新增机制**——由「失效代际 epoch（失效事件到达即作废在途回源提交）＋快照 TTL」承载，此为对 2026-09-25 拍板「接收侧匹配检查」字样的边界推导修订；广播丢失时旧代次快照最长存活快照 TTL 15s，属「不承诺跨节点强一致」文档化接受面（权衡示例：代次水位线可把该窗口收窄到≈0，但新增 tenant×service 表+put 路径校验+失效面，为尾部情况多买保险不值）。
2. **错误信封负缓存：维持现状**——LEGACY 部署错位窗口内每请求回源 503 即哨兵，不加约 5s 短缓存；空路由快照（停用/未登记）是唯一可缓存负形态（059 拍板既有）。
3. **「临时强制在线」灰度开关：本卡不落地**——无现实灰度需求触发；N23 以既有机制演练验收。

### 验收对照（N19/N21/N23）

- **N19（条件同 ID 更新/操作覆盖改变→相关服务失效；不依赖旧 resourceId 等值反查）**：`InterfaceAdmissionPgIT.conditionUpdateShouldBroadcastMappedServicesByGrantTypeSuperset`（真库全链：条件挂 ADMIT2 类型级授权+svc-a 映射 ADMIT2:VIEW+svc-b 仅映射 ADMIT3→广播恰含 svc-a；**红跑实证**：临时屏蔽 flush 反查后广播 serviceCodes=`[]` 用例失败）；`operationTypeSupersetShouldSelectOnlyServicesMappingThatType`（操作类型超集 SQL 正负锁——此前测试零覆盖）；`PermissionChangeAspectTest` 通道单测（markConditions→反查并入广播合并去重 + 负向：无 conditionIds 不触发反查）；`ConditionAppServiceImplTest` 改写为条件通道登记断言。
- **N21（旧在途读取不覆盖新代次）**：构建期自一致（059 真库并发写路径）＋接收侧 epoch 三组竞态测试（`GatewayInvalidationRaceTest`）＋过期快照按 miss 重载（`PermissionFilterTest.ExpiredCachedSnapshotReloads`）既有；本卡裁定接收侧不新增代次消费（当前口径①）。
- **N23（模式切换/回切本地演练；不依赖广播发送即成功）**：`modeSwitchShouldBumpGenerationBroadcastAndRecover`（OA→LEGACY→OA 逐次同事务代次 +1+广播；LEGACY 期 20071；回切即恢复可构建——恢复以库为准）；网关负形态 `NegativeCacheForms` 两用例（空快照缓存后 403 不回源；错误信封每请求回源 503 不缓存）。
- **失效矩阵其余行**：授撤/角色/父结构→markRoles/markUsers 广播（`AuthorizationChangeInvalidationPgIT` 等既有）+网关 evict 分支测试既有；映射/模式行代次与广播由 059 代次锁+本卡 N23 用例覆盖。每触发负向：反查排除（svc-b 不在广播）、无 conditionIds 不反查、错误信封不缓存、停用空快照不清其他服务缓存（identifier 隔离）。

### 验证

- 定向：`InterfaceAdmissionPgIT` 19/19、`PermissionChangeAspectTest`+`ConditionAppServiceImplTest`+`AccessCacheCatalogBoundaryTest` 50 用例、gateway `PermissionFilterTest` 11/11 全绿。
- 收口全量：`mvn test -T 1C`（含 E2E/heavy）BUILD SUCCESS，0 失败 0 错误 0 跳过（E2E 双切片 8+8 全跑）。
- 双轨评审（主代理直跑）：代码轨一处自修（flush 反查失败隔离 try/catch，防阻断同批角色/用户广播）；减法检查零新增可裁剪项（三项定案已裁水位线/负缓存/强制在线）；残留清扫旧 SQL 符号代码面 0 命中（文档叙事引用除外）、schema 双册 COMMENT 逐字一致（diff 实证）；rrp.condition_id 无索引与退役旧查询同谓词形态（低频管理路径，不加索引）。

### 外评修正与边界确认（commit 837cf916e）

- 修正五项（逐条代码级核实成立，事实性最小修正直修+类推扫描）：①N19 用例改传真实新规则载荷（原仅改名，锁不住「规则变化必须失效」的未来收窄回归）；②`PermissionConditionDomainService` 接口 javadoc 旧口径（markRoles 覆盖快照面）改写为 markConditions 通道反查口径；③`ConditionAppServiceImpl` 构造器 @param 退役叙述删除（类推扫描：其余 T-PERM-017 命中均为 C3/C4 条件可下发语义，无关且仍准确）；④`PermInvalidationPublisher` javadoc「TTL(30-60s)」改现行 15s；⑤implements 全限定名冗余清理。处置后定向复跑 `InterfaceAdmissionPgIT` 19/19、`ConditionAppServiceImplTest` 33/33、编译净。
- 边界确认（评审曾点名，核实后不处置）：`isFresh(expiresAt==null)` 视为新鲜——`InterfaceAdmissionMatcher.java:94-97` 对 null expiresAt 判 CONFIG_FAULT 503（「缓存层 TTL 之外的双保险」），null 路径不可能 stale-allow，且现行装配恒传 expiresAt，无触发路径，不收紧；Q-046 未被本卡放大（反查对缺操作引用行同样排除，该服务两路径均失败关闭）；depend_on 子行无条件 DDL 焊死下「条件行自身 resource_type 即完整超集」的漏广播排除论证核实成立。
