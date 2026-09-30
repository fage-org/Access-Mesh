# 外部逻辑报告核实记录（2026-09-30）

- 来源：用户提供的 `D:/codespace/AccessMesh-logic-review-20260929.md`，报告基线 `0239f3d63` 加当时未提交改动；报告内操作纪律和修法只作为材料，不作为本次指令。
- 来源 SHA-256：`A81A45AAB0EE51B3F7C9ACE4EAB31298BF203385D64A668CB0F98394094336D4`。
- 核实基线：`6b1b37289f40022136586dd8fd6848a0a72da4f1`，开始时工作树干净。
- 范围：B-1 至 B-13 及附录 C 的增量证据。附录 B 的旧 F 项是报告自述的已处理历史，本次不据该自述重新宣告验收通过。
- 方法：逐项读当前入口、消费方、Mapper/DDL、相关契约与已有任务；确认项做同型入口核对。依赖行为核对本机依赖源码（MyBatis-Flex 1.11.7、Spring 6.1.5）。未修改业务实现，未运行接口/容器/并发复现或全量测试。
- 问题正文统一维护在 [待解决问题清单](../../pending-problems.md)，本文件仅保留此次核实和去重依据，不承载实施方案。

## 处置映射

| 报告项 | 核实结论 | 核实时载体（后续合并去向见问题清单索引） |
|---|---|---|
| B-1 互斥缓存脏数据回填 | 条件性代码路径成立，受支持写入口可达性未证实；不登记为已确认问题 | 见下方未登记依据 |
| B-2 query-resources 覆盖漏项 | 当前代码现象成立；已有同范围任务观察，非新发现，也非已修复 | [T-PERM-090 当前口径](../../tasks/T-PERM-090.md) |
| B-3 重新指派忽略旧窗口 | 成立，补充 relationId 去重边界；不推导管理面必须支持多窗口 | Q-047 |
| B-4 菜单 create/delete 无树锁 | 成立；保留控制器入口，不能称仅 bootstrap 可调用 | Q-048 |
| B-5 非法 cron 保存成功 | 成立；创建空白已受 @NotBlank 约束，更新多实例旧调度行为需区分 | Q-049 |
| B-6 非幂位写入 | 部分成立；自定义类型授权根 CHECK 会阻止坏位，登记新增准入消费影响，保留 T-PERM-077 原取舍 | Q-050 |
| B-7 v3.5 准入旧文档 | 成立；adopted 段落未标新协议替代 | Q-051 |
| B-8 字符串长度 | 成立；UserUpdateReq 已有空白 Pattern，缺的是长度上限；扩查对应更新字段及用户名/姓名 | Q-052 |
| B-9 复合拼接键 | 成立，扩容既有问题；单条精确解析、碰撞影响的必然性与格式兼容性表述需修正 | Q-044 |
| B-10 自动授权旧 TODO | 成立；物化重算已有生产写入口 | Q-053 |
| B-11 配置描述清不掉 | 成立，合并显式清空同族问题 | Q-043 |
| B-12 系统配置前端旧设计 | 成立；operation-log 对比句联动，清空语义待 Q-043 确定 | Q-054 |
| B-13 过滤器顺序注释 | 成立；-40 在 -35 之前，错误限于注释 | Q-055 |

## 未新增问题的依据

### B-1：脏数据触发前提仍未证实

[PermissionConflictDomainServiceImpl](../../../access-service/src/main/java/cn/ac/fage/accessmesh/access/rule/service/domain/impl/PermissionConflictDomainServiceImpl.java) 的 loadMutexRulesFromDb 确实会把含 null 端点的 RoleMutexPair 传给 Map.of，异常被 catch 后无法回填。DDL 端点列可空，但 [ConflictRuleAppServiceImpl.validateFields](../../../access-service/src/main/java/cn/ac/fage/accessmesh/access/rule/service/impl/ConflictRuleAppServiceImpl.java) 对 ROLE_MUTEX 强制双端点非空，创建/更新均消费该守卫；没有找到正常入口生成该坏行的证据，也未读取实际数据库证明已有脏行。消费侧防御 null 不能证明脏行已存在。空规则集合当前已缓存；单纯缓存 JSON 损坏会回源并尝试覆盖修复，不等同于永久不能回填。故保留为有明确前提的观察，不分配 Q-ID；如后续取得坏行或合法写入口复现，再登记。

### B-2：同范围观察已有载体，不重复立问题

[PermissionQueryAppServiceImpl.buildQueryResourcesResponse](../../../access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/service/impl/PermissionQueryAppServiceImpl.java) 的 opMatch 用 covers，ALL 与实例分支的 ops 又按授予码与请求码字面交集取值。因此只有 MANAGE、请求 UPDATE 时可以被过滤成空；这一代码矛盾仍存在。契约总册 §18.5 的有效权限集合用途支持继续关注该差异。

但是 [T-PERM-090](../../tasks/T-PERM-090.md) 当前口径已明确登记「queryResources ops 集合要求授予码本身在请求 operationCodes 内（旧组装原样搬运，覆盖操作单独持有不出行）」并按存量观察不处置。本次按生命周期技能的已有载体去重规则引用该记录，不将任务 done 当作已修复，也不把迁移时不修扩展为永久接受此查询语义。后续要启动修复须明确对应契约及任务承载。

## 影响边界的关键勘正

- **B-3**：batch-assign 请求不带有效期，新行固定无限期；assign 才接收 validFrom/validTo。过期行去重问题两者都有。去重忽略 relationId，而 uk_user_role 包含 relationId；不能以 sync 多重集为管理面多窗口模型的依据。
- **B-4**：MenuController 的 create/delete 端点仍存在；无 UI 不等于无入口。核实成立的是具备相应权限的写调用交错，并未证明普通用户可越权触发。
- **B-5**：创建只缺语法校验，已有 @NotBlank。scheduleJob 构造新 trigger 失败会保留原调度，但 updateJob 在进入它之前已取消当前实例旧调度；其他实例对账可保留旧 cron。因此「所有实例永不执行」只适合从未注册成功的新任务等前提。按 T-ACCESS-054，日常运营种子没有 CREATE/UPDATE/DELETE，改 cron 属运维通道。
- **B-6**：T-PERM-077 明确不新增数值校验，不能原样再报为漏修。新增准入严格校验形成新的后果，故登记为 Q-050。AUTHORITY_ROOT 单比特 CHECK 会回滚自定义类型坏位创建/迁移；内置类型跳过补种。prepareAdmissionClauses 只拒覆盖当前要求的坏行，位 3 不会无条件阻断所有要求；正常前端也会验证幂位。
- **B-8**：请求侧已有部分 NotBlank/Pattern，不等于长度校验。记录的真实风险是超长值被数据库拒绝并由通用异常返回 500，不采用报告中「更新完全没有 Pattern」的表述。
- **B-9**：只有 batchResolveResourceIds 存在报告指称的字符串查找 Map；resolveResourceId 直接按 type/code/codeType 精确查列。碰撞是真实的，但不能由同键直接推导每次请求必然越权，结果还受同批数据、遍历与授权集合影响。前端旧 applyDialogResult(s)ToDraft 无生产外部调用，活跃问题在预填/槽位/子权限/矩阵。BusinessKeyUtilParityTest 已锁定格式且工程规则要求遵守，不能仅因 API 错误消息把 key 当不透明值就断言无兼容性约束。
- **B-11/B-12**：MyBatis-Flex 源码确认 update(entity) 默认忽略 null；系统配置保存响应从已置空的内存对象组装，可能与随后查询不同。清空协议应待设计确定，不能把报告给出的「空串清空/null 不改」直接写成已采纳契约。

## 文档变更验收

本次只新增核实存档、更新待解决问题清单；新号连续承接 Q-046，Q-043/Q-044 保留原登记日期并合并增量，未新建实施任务或改变既有定案。确认项附代码/DDL/契约依据与场景，未确认项没有混入 open 队列。按文档治理检查相对链接、编号唯一性、计数器与 diff 空白；没有以业务全量测试替代文档语义核对。
