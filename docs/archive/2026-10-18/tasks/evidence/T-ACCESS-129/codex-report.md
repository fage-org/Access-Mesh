完成两轮只读复评：先沿调用链检查，再从数据库写点反查事务、锁序与竞争入口。未确认本轮新增或被本轮放大的 P0–P3 缺陷；另有一项存量观察。

## 专项核查结果

| 项目 | 结果 |
|---|---|
| 1. 公共锁与重置事务 | 核对无事务/同步、预中断、等待/非等待竞争、迟到成功、技术异常、注册失败、提交/回滚、解锁失败及重入配平。三个重置入口键与事务边界一致；未发现跨线程或独立嵌套事务重入路径。既有真实 Redisson 用例仍经过下沉后的实现。 |
| 2. 完整锁图 | 覆盖主体、成员、组织、角色、类型、资源、授权、投影、bootstrap 与任务相关路径，并纳入隐式 SQL 行锁及提交回调；未确认可实际闭合的 Redis↔PG 等待环。 |
| 3. 写入口与读取 | PG106 位于主体读取、FULL 预载和版本 CAS 前；成员 parent 在 SYS_ORG 后读取；主体缺失由投影层拒绝并回滚。类型/操作修复点使用新鲜数据库读取；其他同步入口的缓存问题见存量观察。 |
| 4. 普通更新与字典 | 核对请求 patch、派生字段、有效行锁、软删除及租户条件。字典父删除按 ID 排序，创建/改绑锁目标父类型；未确认相反改绑形成等待环。 |
| 5. Gateway marker | 删除 generation 同步提升 epoch，旧 token 无法恢复有效；重试继续共享原 deadline，失败沿既有 503 路径处理。 |
| 6. 上轮七项 | Q083–Q089 原报告中的具体交错均已闭合；附带默认值与 operation 更新问题亦已修复。未用关闭标记替代代码核对。 |
| 7. 测试与契约 | 核对真实 Redis/PG 夹具、Mapper XML、mock bridge 与既有报告；未把 mock 当作行锁证据，也未发现本次反例依赖裸 sleep 保证交错。 |

判定依据包括[公共锁设计](/D:/codespace/Access-Mesh/docs/design/redis-transaction-locks.md:28)、[联合锁序](/D:/codespace/Access-Mesh/docs/design/redis-transaction-locks.md:105)、[自动授权并发边界](/D:/codespace/Access-Mesh/docs/design/dependency-auto-grant.md:262)、[成员并发边界](/D:/codespace/Access-Mesh/docs/design/default-org-tree-user-lifecycle.md:303)、[管理员连续性](/D:/codespace/Access-Mesh/docs/design/tenant-lifecycle.md:48)及[文档治理](/D:/codespace/Access-Mesh/docs/design/project-rules.md:1280)。Q081 的交付窗口、watchdog 边界和树锁等待取舍均按原范围保留。

既有 XML 按实际 `testcase` 汇总为 **3018 项，失败/错误/跳过均为 0**，包含 35 项 E2E 和 heavy；已排除两份旧报告。源码及日志哈希与[验收记录](/D:/codespace/Access-Mesh/docs/archive/2026-10-18/tasks/evidence/backend-concurrency/results.json:119)吻合，后续变化限于文档收口。

## 过度设计可裁剪项

过度设计可裁剪项：无。

## 存量观察

- [ResourceEntitySyncAppServiceImpl:226、461](/D:/codespace/Access-Mesh/access-service/src/main/java/cn/ac/fage/accessmesh/access/resource/service/impl/ResourceEntitySyncAppServiceImpl.java:226)、[AbstractRoleSyncAppServiceImpl:135、271](/D:/codespace/Access-Mesh/access-service/src/main/java/cn/ac/fage/accessmesh/access/role/service/impl/AbstractRoleSyncAppServiceImpl.java:135)：仍通过 `TYPE_VALUE` 确定落库类型；旧读取在同码删建后迟到回填，可使后续同步写入旧 `typeValue`，缓存过期不能清除已落库孤儿；该路径基线已有、本次未扩大，宜统一使用锁内数据库类型身份。

## 总结

P0：0；P1：0；P2：0；P3：0。

对指定变更，**未发现具有明确证据的生产级缺陷。**

未运行构建或测试，未修改文件或 Git 状态，未使用子代理；最终 HEAD 仍为 `3e7ec01e3`，工作区干净。