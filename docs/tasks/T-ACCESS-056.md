---
doc_type: task
id: T-ACCESS-056
title: （ADM-T01）准入定案回写与协议落账
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §7/§8.1/§8.2
  - docs/design/access-service-api-contract.md §25/§20.2
depends_on:
  - T-PERM-080
  - T-PERM-082
blocks: []
acceptance:
  - "准入候选/子行规则（结构有效即候选、CONTEXT_DEFERRED 运行时父判定延后业务）、新快照安全读取（新鲜数据库操作定义目录，§5.3）、映射歧义规则（同要求去重/异要求 AMBIGUOUS_REQUIREMENT 阻断/引用损坏 503 配置故障/无注册拒绝）、服务迁移门槛（最终检查代码位置+反向拒绝测试）核对与设计 §7/§8 一致并当轮归本设计/契约的当前章节、补可追溯来源"
  - "新错误原因族（AMBIGUOUS_REQUIREMENT、准入拒绝、准入配置故障）编号段落 AccessErrorCode 并挂契约总册新章（版本化准入协议——名称 interface-admission 族为建议）"
  - "N 系验收用例（§10.3）分配到 T-ACCESS-057~062 各卡验收面落账"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-27
---

# T-ACCESS-056 （ADM-T01）准入定案回写与协议落账

## 背景

设计 §7/§8（报告临时编号 ADM-T01）。方案 A 主线沿 [历史定案原文](../archive/2026-09-26/decision-registry-before.md) 2026-09-09 方向定案（API 不单独授权、接口权限由操作权限关联派生）；本卡把设计定稿细则落成可引用的协议与验收分配。

## 范围

- 定案当轮归所属规范并补来源、契约总册新章（含 requiredPermission={resourceTypeCode, operationCode} 对外 DTO 形态）、错误码选段（按错误业务语义选 1xxxx/2xxxx 段）。

## 当前口径

四项落账拍板（2026-09-27 用户确认，协议正文归契约总册 §25）：

1. **错误码落账形态**：两族配置故障码随本卡进 `AccessErrorCode` 枚举（20070 `ADMISSION_REQUIREMENT_AMBIGUOUS`、20071 `ADMISSION_CONFIG_FAULT`，暂无 throw 点——沿 T-PERM-082/083 契约先行先例），首个抛出点随 T-ACCESS-057/058/059 落地。
2. **载体切分**：配置故障（要求歧义/引用损坏/未知协议）占数值码；**准入拒绝不占数值码**——AdmissionResult.decision=DENY + reason 词表（`NO_ROLE`〔准入〕/`NO_CANDIDATE`〔新增 token〕/`CONDITION_NOT_MET`），与 check 族「无权限是判定结果不是错误」同形态；网关对终端沿现行 403 信封。
3. **503 语义**：「技术错误 503」=网关→终端层（网关检测配置故障/fail-closed 时对终端 503，沿现行失联兜底形态）；access-service 在线端点错误维持现行统一信封（HTTP 200 + body 数值码），网关按「响应非成功即 fail-closed」消费——不新增 HTTP 状态分支。
4. **命名**：端点 `interface-admission` / `interface-admission-snapshot`（与旧 check-interface / interface-snapshot 对仗）；契约章名「操作准入协议 OPERATION_ADMISSION」。

## 验收对照

- 验收①（四族规则核对+归当前章节+补来源）：契约总册 [§25](../design/access-service-api-contract.md#operation-admission-protocol)——§25.3 候选/子行规则（对应设计 §7.2/§7.3）、§25.4 新快照安全读取（§5.3）、§25.5 映射歧义规则（§8.2）、§25.7 服务迁移门槛（§8.6）、§25.1 requiredPermission DTO 形态（§8.1）；章首含设计权威与定案链来源（2026-09-09 方向定案→2026-09-25 方案 A 定案→本卡四项拍板）。设计稿 §8 章首补落账指针。
- 验收②（错误原因族编号+契约新章）：`AccessErrorCode` 20070/20071（2xxxx 权限域段、Javadoc 指回 §25、类册段位清单补 20070-20079 行）；准入拒绝族走 §20.2 reason 词表（新增 `NO_CANDIDATE`，`CONDITION_NOT_MET`/`NO_ROLE` 复用）；载体切分依据 §25.6。
- 验收③（N 系分配落账）：设计 §10.3 补「验收归属」列（N01~N30 全落位）；程序化核验各卡 acceptance 覆盖完整——N07~N09 经 057 卡「N06~N10」区间记法覆盖、N12 由 057 显式移交 059、N21 双归属 059+060；N04/N05 准入半边经 claude 外评指出未在 057 卡面显式落位，已补记 057 卡验收项（与 061 业务半边同形）；契约 §25.8 为分配索引。

## 非目标 / 遗留

- 不写准入实现代码（在 T-ACCESS-057+）。

## 完成记录

- 2026-09-27 执行（commit b2ece5f4e）：契约总册 §25 新章（八节，含端点 DTO/快照形态、四族规则、错误码与 reason 表、迁移门槛表、N 系分配索引）＋§20.2 词表增 `NO_CANDIDATE`＋显式锚点 `operation-admission-protocol`；`AccessErrorCode` 增 20070/20071（Javadoc 指回 §25，类册段位清单补 20070-20079 行）；设计 §10.3 补验收归属列、§7/§8 章首补落账指针、两册 `last_reviewed` 2026-09-27；定案登记表主题路由行（服务身份/接口准入/网关）补契约 §25。
- 回归证据（2026-09-27）：`mvn test -pl access-service -DskipTestcontainers=true` BUILD SUCCESS，Tests run 1555, Failures 0, Errors 0（日志整文件落盘 /tmp/t-access-056-unittest.log）；定向 `ErrorCodeContractTest` 7/7（surefire 报告为准）。
- 外部评审（claude+grok 双通道，2026-09-27）结论核实成立并同批修正：①§25.2 快照节拆「请求/响应」两段并补请求形态（原「请求/响应」合并标注使快照 JSON 有被读作请求的歧义）；②§25.2 主体/租户口径拆写（租户与调用方身份=可信链，被检查主体=请求体断言、租户范围内解析——原单句混淆两者）；③§25.2 `operationCandidates[]` 示例改为每元素单条件身份＋具体 candidateKind（原示例多条件身份挤一元素、kind 写成「A|B|C」字面，与设计 §8.4 归并键矛盾）；④§25.7 补「不能任一允许放行整批」与「菜单后代可见不能推导默认 SELF 的 check 已打开祖先」（设计 §8.6 逐字对齐，061 卡验收同批补禁令）；⑤057 卡验收面补记 N04/N05 准入半边；⑥20070 抛出点口径三处统一为 057/058/059；⑦设计 §7 章首补契约 §25 指针。处置后 P0=0、P1=0；过度设计可裁剪项两通道均报「无」。（§25.1 术语笔误 MAY_ENTRY→MAY_ENTER 于提交前经本地双轨评审发现并已修正，含于 b2ece5f4e。）
