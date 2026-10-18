# 后端并发专项排查与方案验收

日期：2026-10-18。对象：8733a51c24adfe64b1ff4cb9a75594a1b324a3d6 全后端现状。

## 外部排查

用户指定 gpt-6.1-sol；按 external-review skill 使用 `-m gpt-6.1-sol -c model_reasoning_effort="max" --disable multi_agent -s read-only`，不注入上下文覆盖。提示词经 stdin，输出整文件落盘。退出0，开始与结束 Git 工作区均干净；未核验或声称核验后端模型身份。

[原始提示词](codex-prompt.md)、[原始报告](codex-report.md)、[参数与哈希](codex-run.json)保留原文。原始完整运行日志留在 `.tmp/concurrency-sol-20261018/run.log`，哈希已归档。仅执行本次用户要求的外部排查，没有追加外部评审。

## 核实结果

外部报告 P0=0、P1=3、P2=4、P3=0。主代理逐条代码与当前规则核对后保留全部发现；这是静态/框架证据核实，尚未运行新反例，不宣称已出现实际环境事故或已经修复。根因、全部位置、交错及建议见原始报告；未修复状态对应 Q-083～Q-089。

<a id="q-083"></a>
### Q-083 旧实体回写

逐项复核普通实体 update、从旧实体复制未触达列的 patch，以及对应删除写者。实际 BaseMapper.update(T) 传 ignoreNulls=true；框架收集非空实体列。仓库未声明相关实体逻辑删除映射，不能假定旧 delete_flag 被框架排除。OAuth2 H1/H2 交错及同族写者保留为修复范围。

<a id="q-084"></a>
### Q-084 锁后一级缓存

亲核 updateType 的锁前/锁后 selectValidById，以及 TypeDefinitionMapper 缺失 flushCache；本机 MyBatis Configuration 字节码默认 SESSION。createOperation 的冷缓存解析路径同类。已接受的角色删除窗口与本交错主体不同，不撤回。

<a id="q-085"></a>
### Q-085 岗位旧 parent

亲核 assign 的 orgMap、remove 的 org 均在 SYS_ORG 取锁前读取；移动迁移 relation_id，解绑按旧 relation 精确匹配，残留角色关系仍参与主体有效角色读取。

<a id="q-086"></a>
### Q-086 主体删除与绑定

亲核原生删除持 PG 连续性锁、绑定持 SYS_ORG，读取/清理与未提交新增关系没有共同生命周期守卫；外部管理与 sync 同类调用点按原报告纳入修复枚举。

<a id="q-087"></a>
### Q-087 主体 FULL 旧预载

亲核 fullSync 先构造 existingByExternalId 再逐项 applyVersion；胜出后直接用预载决定 insert/update。版本 CAS 不刷新该 Map，已存在唯一键可使合法新版本整体失败；不误报为版本倒退或失败不回滚。

<a id="q-088"></a>
### Q-088 字典父子生命周期

亲核类型删除普通查询后查数据/软删，数据创建与改绑普通查父后写入；DDL 以编码关联、无外键，类型软删后允许同码新建。

<a id="q-089"></a>
### Q-089 Gateway 代际回退

亲核 liveKeys 在 marker 同步区外生成；retainLiveKeys 删除 keyGeneration、不提升 globalEpoch；generationOf 缺失返回0。旧空集合能把已标记的1清回0，begin/mark/commit 同步各自正确仍无法阻止该交错。

## 本地代码轨

已在文档轨前独立完成。正式源代码发现保持 P1=3/P2=4；排查产物没有引入生产源码变化。通过项：现有 Redis 获取/释放中断机制、三个重置入口的目标键、树锁等待与全序、平台/租户行锁、服务凭证退役、任务租约与 fencing；保持既有接受边界。未发现本次公共提取草案需要新增注解/AOP/配置/重试/后台补偿的调用需求。过度设计可裁剪项：无；存疑待决策项：本草案没有阻断性产品取舍，实施仍需覆盖列明的业务问题，不能仅搬 helper。

## 本地文档轨

独立核对原始报告、当前权威、草案、任务与 pending 索引。方案标 draft，task done 只表示排查完成；问题不标 closed，未把3004历史通过数说成本轮新测试。原四项待解决事项与 T-PERM-036 暂缓保持。发现临时任务卡的相对链接须随归档修正，已按最终位置回写。Q 编号从83递增至89，原82不复用；无同问题的已实施任务被误计为未完成。

## 验证边界

- 指定模型只读排查：passed，退出0。
- 主代理源代码、SQL、实际框架字节码及现行规范复核：passed。
- 原始 prompt/report 字节与 SHA-256 一致；草案/任务/证据的相对链接检查通过。
- 新增并发反例、定向行为测试、完整回归、测试环境部署：not run；本卡未改生产/测试源码。
- T-ACCESS-120 的3004项通过属于此前基线证据，不证明本文竞态已受覆盖。

公共方案详见 [草案](../../../../../design/redis-transaction-locks.md)。后续实施应先写能使旧实现失败的确定性交错测试，再修复并执行仓库收口回归。
