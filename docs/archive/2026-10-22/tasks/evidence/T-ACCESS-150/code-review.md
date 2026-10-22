# 代码轨本地评审

日期：2026-10-22。主代理只读执行；对象为 `725e14f49`～`bcba8eb40` 的资源关系实现，以及历史用途守卫、通用目录退役和 E2E 联合用例工作树。此前分卡评审结论按相同适用边界复核；未调用外部评审或子代理。

## 缺陷与决策

完整回归暴露两项确定性问题，均已修复：P2，ResourceAssociationDomainServiceImpl.removeResourceAssociations 对全部删除资源执行新增授权清理，使未声明关系能力的普通 SYNC 类型也改变历史行为；P3，AccessBootstrapPgIT:223、230 的固定接口数量未包含两个新只读接口。前者按已确认的“未声明类型保持现役行为”限制新增清理范围，后者同步固定图断言；均不引入业务取舍。普通类型的旧行为断言未修改。修复后四组 PG 共 68 项通过，0 failure/error/skip；最终完整回归见[联合验收](verification.md)。

存疑待用户决策项：无。历史用途锁定、策略提前保存均按已确认规则落地，不存在尚未拍板的默认业务选择。

修复后只读类推核对 single DELETE、FULL 缺失清理和管理删除三个调用点：统一经资源领域筛选新增清理范围，软删后的身份读取有租户条件并按 SqlBatches 批量执行；已解绑主资源仍保留来源身份，因此删除清理不会漏掉其专门授权。MANAGED 资源原有删除路径保持，普通 NONE 同步类型由既有自动授权 PG 用例锁定兼容性。

## 实证通过项

| 排查面 | 实现与证据 |
|---|---|
| 历史用途锁定 | ResourceAssociationDomainServiceImpl:95；资源/关系/业务授权历史 SQL 均保留 tenant 条件，不受 delete_flag 或角色删除影响；AUTHORITY_ROOT 豁免。ResourceAssociationDomainPgIT:378、392、404 覆盖解绑历史、已删实例、已删角色授权及根权限 |
| 事务与身份 | preparePublications 持既有 RESOURCE_ENTITY 锁，来源身份先于版本确认；主/子资源和关系同事务，FULL 失败项不参与缺失删除。领域 PG 验证旧身份重放、跨租户/服务、真实提交竞争与回滚 |
| 权限计算 | QueryReadSupport:69、106；关系按请求批量读取，作用于已有事实，不另建权限求值器。QueryExecutionEngine 在类型 ALL 短路之前限制目标，父来源完整收集；QueryRequestValidator 约束单一父上下文，混合无父项不受污染 |
| ALL 与树 | 专属 ALL 受当前主资源有效实例和输出预算限制；通用 ALL 保持全范围。ResourceEntityMapper.xml:14 的递归闭包保留租户/类型/状态/归属且 UNION 防环，判定与展示同源 |
| 清理和失效 | removeResourceAssociations、removeGrants 复用授权领域、自动授权物化、条件回收和 audit；解绑只删实例父下对应通用类型子项。受影响角色在删除前保留，审计同事务、失效 afterCommit；PG 验证提交/回滚与双顺序竞争 |
| example 退役 | DemoPolicyDomainServiceImpl:28 保留策略 CAS 与类型校验；DemoDataDomainServiceImpl:68 的 INSTANCE 映射完整性仍在。Controller/manifest/schema 测试锁定旧目录入口和表消失；范围 SQL 仍参数化，事务外查权限及发送发布 |
| 前端 | 原 GrantDialog 草稿与主子联合保存保留；受控 Promise 测试换父/换类型迟到响应；真实浏览器保存新主权限及 3 子项后重新打开，两张报表配置独立 |
| 联合负向身份 | ExampleBusinessFinalCheckE2EIT 的新场景建立普通 association-reader，无 bootstrap/ALL 后门；A/B 金额身份不同、共享目录的 R1/R2 分别授权，解绑/重绑不恢复、停用与删除分别验证 |

## 机制重量与测试质量

没有新增无真实调用方的框架、配置项或预留模式；沿用 sync/FULL、领域锁、QueryGate、统一缓存失效、SDK 和原管理页面。退役本地目录后已删除其实体、Mapper、发布分支及无调用方 Change 旧构造器。未发现需另行决策的可裁剪机制。

不以 mock 或截图替代持久化/鉴权证据。用途碰撞、历史占用及并发用例迁移至新引用来源；只删除随目录退役失效的测试，保留读写隔离、版本冲突、未知映射拒绝等现役行为锁。完整回归结果单独记录于[联合验收](verification.md)。
