---
doc_type: design
title: example-service 持久化权限演示 API 契约
status: adopted
domain: example-service
last_reviewed: 2026-10-20
---

# example-service 持久化权限演示 API 契约

本文件由 T-ACCESS-136 定稿，元数据与资源发布已由 T-ACCESS-139 交付，策略配置与记录查询/备注修改由 T-ACCESS-140 实现，验收状态见任务卡；CSV 导入由 T-ACCESS-138 实现，任务按计划实施。现役 hello/report 探针继续按[服务设计](services/example-service.md)运行，未交付的路由不描述为已上线，也不进入可用接口声明。

报表入口采用已确认的[固定主类型/主操作边界](example-permission-demo.md#admission-binding)：主类型为 DEMO_REPORT，读取/编辑/导出分别为 DATA_READ / DATA_EDIT / EXPORT；它们不作为策略可编辑字段。动态范围、字段与谓词配置继续生效。

行为规则见[演示设计](example-permission-demo.md)，本文件集中维护请求、响应与错误语义。所有普通入口采用 POST + JSON DTO，文件上传使用已允许的 multipart 例外；业务 ID 不进入路径。统一响应信封、HTTP 参数错误和分页边界沿[工程规范](project-rules.md#1-通用报文规范)，不新建同构响应类。

<a id="identity"></a>
## 身份与数据边界

- 普通请求的 tenantId/userId 只来自 Gateway 已验签上下文，DTO 无这两个字段。CSV 的 ownerUserId 是待导入记录的业务归属，不能覆盖当前操作者。
- 查询 AccessMesh 时使用可信租户选择的服务凭证及 SDK 显式凭证重载。用户主体类型使用现役 LOCAL_USER，外部标识为真实用户 ID 的字符串，不读 IAM 内部数据库。
- 新后台执行使用持久化创建者身份并重新鉴权，不带 clientIp，不恢复旧 Bearer 或请求线程上下文。旧一次性导出协议不受本文件取代。
- 数据入口固定使用本地报表的真实身份和服务端生效策略；请求不能指定主资源类型、权限操作或可执行 SQL 来替换检查对象。

<a id="import"></a>
## CSV 导入

`POST /api/example/demo-data/import`，`multipart/form-data`。

| part | 内容 | 规则 |
|---|---|---|
| `file` | CSV 文件 | UTF-8（允许 BOM）、逗号分隔、首行字段名，双引号包裹及双写引号转义；不长期保存原文件；实际写入列逐列检查编辑权限 |
| `request` | `application/json`，`ImportReq` | 使用 JSON DTO，不把身份放入表单 |

`ImportReq`：

| 字段 | 类型 | 规则 |
|---|---|---|
| `reportCode` | String | 必填，目标报表的稳定业务编码；服务端解析真实报表及其数据集 |
| `importKey` | String | 必填，脚本为一次主动导入生成的唯一请求号，网络重试沿用；使用 UUID 文本表示 |

权限为独立数据维护权、目标报表 DATA_EDIT、行编辑 ALL 和所有提交业务列的字段编辑权。只具 INSTANCE 行范围即使覆盖当前数据也拒绝。所有远程鉴权在本地写事务外完成；事务内核对捕获的策略/字段目录版本、业务状态，再原子写入批次与全部记录。

同号行为以[导入请求号与重导](example-permission-demo.md#import-idempotency)为准。服务器计算文件原始字节摘要并与来源报表、原操作者绑定；摘要不由请求自报。相同文件的新请求号不自动成为旧请求重放，有效 recordCode 冲突仍整批拒绝。

成功返回 `R<ImportResp>`：

| 字段 | 类型 | 规则 |
|---|---|---|
| `importKey` | String | 本次请求号；作为批次对外定位键，避免再要求脚本猜内部主键 |
| `status` | String | `IMPORTED` / `REVOKED`；网络重放读取原批次当前状态 |
| `recordCount` | long | 原批次提交的记录数；撤销状态仍指原批次规模，不表示记录仍有效 |

本入口是有界整批事务，不另建导入异步作业或预留 PENDING 生命周期。校验失败、数据库事务未提交不返回成功批次；技术失败后可以同号重试。并发重复请求依数据库唯一约束及共同事务守卫处理，不用内存标记代替持久化幂等。

`POST /api/example/demo-data/import-status` 与 `POST /api/example/demo-data/revoke-import` 均接收 `{importKey}`，返回 `ImportResp`。仅原操作者在可信租户下访问，其他操作者与不存在统一拒绝 30004；状态查询要求数据维护权，不返回业务字段值或文件摘要。撤销额外检查来源报表 DATA_EDIT、行编辑 ALL 和实际导入字段的编辑权，从已存批次读取来源报表及字段身份，不能让请求换用另一报表的授权。已撤销批次重复撤销返回原状态；字段身份删除失效时不按同名新字段替代，失败关闭。

CSV 只能含目标报表已登记且发布的固定数据集字段，未知或重复表头拒绝。必填业务列必须出现；可空 remark 表头可省略，省略时 SQL 不显式写该列，也不以默认值绕过任何提交列的编辑权限。字段类型见策略目录节，文件/行数按[执行预算](example-permission-demo.md#execution-budgets)；空文件或无业务记录拒绝。错误只报告受控原因、行号和字段，不回显无读权字段值、原始 CSV 行或 SQL。样例/模板由场景脚本按固定目录生成，首版无独立上传暂存、预览确认作业或任意列自动注册。

<a id="record-addressing"></a>
## 记录定位与行列响应

详情、单条修改和批量修改使用 `recordCode`；批量目标使用 `recordCodes`。这些是精确业务目标，允许调用者从外部业务流程获知，不要求为了提交已知目标而额外授予编号读权。实际行范围与各操作字段权限必须独立检查。用户自定义筛选/排序中的 recordCode 仍按普通可读字段处理。

响应只包含当前操作允许的字段。不额外返回 rowId，不返回未授权 recordCode，不用 null 占位。写操作只返回完成结果，不能因请求中出现编号或备注就把它们原样回显。批量逐项查看可用请求下标关联各项结果，不以回显隐藏编号完成关联；逐项允许/拒绝不能泄漏被拒目标的业务值。

字段目录只给读者返回当前报表下可读且启用的字段描述。元数据管理目录与读者目录是不同入口，元数据管理权限不自动提供记录字段读取权。

分页复用 `PageResp`（默认 pageNum=1、pageSize=20，上限 200，超限 HTTP 400），外层包装策略/目录版本及字段描述，不复制 PageResp 字段。EMPTY 返回空分页且 total=0，不查询业务记录；实际 total 不能出现在无可见字段响应中。具体 DTO 见下文记录 API。

<a id="permission-errors"></a>
## 权限结果与错误分类

| 情况 | 业务处理 |
|---|---|
| 网关身份头缺失 / 验签失败 | 继续使用现役 30002 / SDK 30003，不创建新身份协议 |
| 业务最终权限不足 | 使用现役 30004，不读取/修改业务记录 |
| 鉴权传输失败、信封失败或响应结构非法 | 使用现役 30005，失败关闭；不伪装为空数据或普通权限拒绝 |
| query-scopes 明确整体拒绝 | USER_NOT_FOUND / NO_PERMISSION 映射 30004；OBJECT_KEY_NOT_FOUND 表示远端主资源不可用，映射 30108；这些合法空格集合不直接当作传输故障 |
| 行或字段 EMPTY，且无任何必要维度 DENIED/配置故障 | 返回明确空态；任务进入 EMPTY；不取业务记录，不生成产物 |
| 策略无生效版本、未知范围映射、无效目录身份、版本竞争、重复记录、请求号冲突 | 使用本文件业务错误码，不挪用 hello 专属 30001，也不统一伪装为无权 |

query-scopes 的父命中与行/字段格校验由同一适配入口承担；使用[现役 SDK DTO 与契约 §18.6](access-service-api-contract.md#contract-section-18-6)，不复制协议对象。未知 reason、未知模式、重复或缺失的预期格均不可推导为允许。

SDK 适配须在对外仍返回 30005 的同时保留内部受控失败分类：连接超时/暂态依赖失败可供任务有限重试，明确认证失败或停用为不可重试 FAILED。不能沿旧调用门面的统一错误码反推“所有 30005 都应重试”；业务响应不暴露凭证或原始异常。SQL 执行期限超限属于 30106，不反复重试同一超预算查询。

<a id="metadata"></a>
## 元数据与资源发布

初始化脚本使用租户管理员的现役 API 建类型、操作及服务所有权；example 后端不拿 M2M 凭证调用管理写接口。默认演示类型为 DEMO_REPORT、DEMO_DATA_SCOPE、DEMO_REPORT_FIELD、DEMO_POLICY、DEMO_DATASET、DEMO_TASK。类型名是场景配置，不用 Java 按本人/团队/部门分支。

service-config/sync-v2 接口声明使用完整清单，须保留仍受支持的 hello/report 探针，不用仅含新业务族的 FULL 请求隐式撤销旧入口。requiredPermission 与业务门禁对应：policy/scope/publication 为 DEMO_POLICY:MANAGE，import/status/revoke 为 DEMO_DATASET:MANAGE，task/execution 按各表列出的 DEMO_TASK 操作；记录 fields/page/get/batch-get 为 DEMO_REPORT:DATA_READ，update/batch-update 为 DEMO_REPORT:DATA_EDIT；报表 create/get/update 分别为 DEMO_REPORT:CREATE/VIEW/UPDATE，字段管理为 DEMO_REPORT:UPDATE。快照入口先按 DEMO_TASK:TRIGGER 准入，example 再检查报表 EXPORT 与 DATA_READ，不把复合最终门禁塞进接口声明。

安全管理入口固定检查 `DEMO_POLICY:MANAGE`，数据维护入口固定检查 `DEMO_DATASET:MANAGE`，任务控制按 `DEMO_TASK` 族检查；这些门禁不能被被管理的策略改写。报表类型固定，字段类型由元数据登记保存，范围类型/操作按生效策略绑定；默认行/字段操作同名 DATA_READ / DATA_EDIT。业务最终检查仍读取真实报表身份，不由客户端替换主类型/主操作。

所有业务编码不为空、长度至多 64；名称至多 128。初始化类型所有权归 example-service，codeType 缺省为 default。元数据登记允许在策略生效前完成，字段只引用服务端固定目录；因此“登记 → 发布 → 配置 → 授权 → 导入”不形成循环依赖。

<a id="resource-type-purposes"></a>
同一租户内资源类型按用途隔离：DEMO_REPORT 只用于报表，DEMO_POLICY / DEMO_DATASET / DEMO_TASK 保留给对应管理门禁；字段类型与行范围类型不得混用。字段与行范围允许自定义类型名，多个报表可共用字段类型。策略历史中使用的行范围类型也占用行范围用途，ALL 不要求先造范围实例，但不能因此绕过用途隔离。创建报表和保存范围在共同数据库事务守卫内检查已登记用途，冲突整次拒绝 30102；不能仅在单份策略内检查类型不同。已有历史身份保留用途，不能通过停用、软删或同名重建绕过检查；不增加独立类型管理平台。

| POST 路径 | 请求关键字段 | 门禁与响应 |
|---|---|---|
| `/api/example/demo-report/create` | `reportCode,name,datasetCode,fieldResourceTypeCode` | 检查 DEMO_REPORT 类型级 CREATE；新建时启用，保存固定主类型和稳定字段类型身份；返回报表元数据，发布状态 PENDING |
| `/api/example/demo-report/get` | `reportCode` | 真实报表实例 VIEW；返回 `reportCode,name,datasetCode,resourceTypeCode,fieldResourceTypeCode,enabled,version,catalogVersion,publicationStatus`，不含业务记录 |
| `/api/example/demo-report/update` | `reportCode,expectedVersion,name,enabled` | 真实报表实例 UPDATE；完整修改可变信息，版本 CAS；编码、类型及数据集不可改绑 |
| `/api/example/demo-field/register` | `reportCode,expectedCatalogVersion,fieldKeys[]` | 报表 UPDATE；按目录批量新增字段；类型取报表存储的 fieldResourceTypeCode；返回该次字段元数据及新目录版本 |
| `/api/example/demo-field/list` | `reportCode,pageNum,pageSize` | 报表 UPDATE；管理目录返回 fieldId、fieldKey、启用状态、资源业务键及发布状态，无记录值 |
| `/api/example/demo-field/update` | `reportCode,expectedCatalogVersion,fieldId,enabled` | 报表 UPDATE；仅启停，不改绑物理字段、所属报表或稳定资源身份 |
| `/api/example/demo-field/remove` | `reportCode,expectedCatalogVersion,fieldIds[]` | 报表 UPDATE；软删并登记 DELETE 待发布事实；以后同名新增得到新 fieldId/资源编码 |
| `/api/example/demo-scope/save` | `resourceTypeCode,resourceCode,codeType,name,enabled,expectedVersion` | DEMO_POLICY:MANAGE；创建期望版本 0，后续 CAS；仅登记范围资源，不把谓词写进远端资源 |
| `/api/example/demo-publication/get` | `resourceTypeCode,resourceCode,codeType` | DEMO_POLICY:MANAGE；返回本地源版本、发布状态、尝试数及受控失败原因 |
| `/api/example/demo-publication/page` | `pageNum,pageSize,status?` | DEMO_POLICY:MANAGE；分页查看本租户发布记录，包含删除后的失败待发布项，便于定位重放 |
| `/api/example/demo-publication/retry` | 同上 | DEMO_POLICY:MANAGE；重试尚未发布的最新事实，保留原源版本/载荷；不能将旧事件改写成新状态 |

字段 resourceCode 服务端生成不可复用编码；字段管理响应的 fieldId 为本地稳定身份。新增同名有效字段整批拒绝，删除后的历史字段保留用于识别旧任务引用。批量元数据接口上限 100，重复目标拒绝。所有字段归属和版本检查在同一事务守卫下完成。

待发布事实与元数据同事务，事务外调用现役增量 sync。发布状态为 PENDING / PUBLISHED / FAILED，只有匹配当前本地源版本的成功响应才可将当前资源标为 PUBLISHED；对外数据操作要求当前报表与所用字段发布完成。源服务被停用、类型归属错误等明确拒绝记 FAILED，等待管理员排查后重试，不伪装成功。暂态错误有限重试，预算采用执行预算中的三次尝试和间隔，耗尽记 FAILED；手动重试不更换载荷/源版本。重启继续处理 PENDING，不建设 FULL 对账或新消息系统。

发布成功只接受首次应用的明确成功，或[严格重放确认](access-service-api-contract.md#resource-incremental-replay)；不能把 STALE、信封 200 或任意 accepted=true 当作当前载荷已应用。相同版本但不同内容及未知响应组合失败关闭。

发布查询分页只列每条资源的最新源版本，旧事件保留作冻结事实；删除字段后的最新 DELETE 事件仍可查询和重试。人工重试只重新排队最新 FAILED 事件，开始新的有界尝试周期（attempt 从 0 计）；PENDING/PUBLISHED 请求重试不改变状态。失败后的自动退避为 10/30 秒；进程中断后按持久化尝试数和下次时刻恢复，耗尽周期预算转 FAILED。首版单实例发送器串行发送，远程调用在两段本地事务之间，不持数据库锁等待网络。

源版本在同一业务事务内递增并映射为现役 `syncVersion`：occurredAt 使用持久化 UTC 事件时间，至少保持不小于同资源上次事件时间，sequenceNo 使用该资源单调版本；按数据库时间精度保存后冻结在载荷中。重试不重新取当前时间。首版为纯增量来源，不自造 sourceVersion 请求字段或启用 FULL 的 publicationGeneration 协议；字段业务归属也不写成跨类型资源树父边。

<a id="policy"></a>
## 固定字段目录与动态策略

数据集固定为 `sales-records`，不提供任意建表或数据源配置 API。物理字段名由服务端注册，不接受请求携带 SQL 列名。

| fieldKey | 业务类型 | CSV 与修改规则 |
|---|---|---|
| recordCode | String，最多 64 | 必填；同租户有效记录唯一；普通写接口不可修改 |
| ownerUserId | 正整数用户 ID | 必填，脚本回填真实 LOCAL_USER ID；仅作为记录归属，不作为当前调用者；普通写接口不可修改 |
| teamCode / departmentCode / regionCode | String，各最多 64 | 必填，普通写接口不可修改；不由角色名称推导 |
| saleDate | DATE / LocalDate | 必填，ISO `yyyy-MM-dd`，不携带时区或时间；审计时间仍统一 UTC LocalDateTime |
| productName | String，最多 128 | 必填，普通写接口不可修改 |
| amount | DECIMAL(18,2) / BigDecimal | 必填，不静默舍入超出的小数位；作为普通数据字段，不提供聚合 |
| remark | String，最多 1,000 | 可空；唯一由普通记录写接口修改的业务列 |

导入不能因缺某字段编辑权而用默认值写入。ownerUserId 的演示有效性由准备脚本创建用户并回填真实 ID；运行时只校验合法 ID 形态，不同步或维护用户目录。未知 ID 绝不能解释为所有主体；SELF 只比较已验签主体 ID，不匹配即无记录。

`POST /api/example/demo-policy/get` 请求 `{datasetCode}`，`save` 请求 `{datasetCode,expectedVersion,configuration}`；都要求 DEMO_POLICY:MANAGE。响应为 `{datasetCode,version,configuration,updatedAt}`；尚无生效策略时 get 返回 30102，首次保存 expectedVersion=0。配置完整校验、版本写入、生效引用与变更摘要同事务；绑定的范围须已登记、启用且当前版本已发布，失败保持原版本。恢复旧配置也按新一次 save 执行。

`configuration` 结构：

| 字段 | 形态与校验 |
|---|---|
| `fields` | `[{fieldKey,filterable,sortable}]`，恰好覆盖固定目录且无重复项，不暴露系统租户/软删字段；数据类型由目录推导；这两个标志只约束筛选/排序，不授予字段读写权 |
| `read` / `edit` | `{rowResourceTypeCode,rowOperationCode,fieldResourceTypeCode,fieldOperationCode}`；主操作分别固定为 DATA_READ / DATA_EDIT；默认范围操作同名；行与字段类型必须不同，字段类型必须匹配实际报表字段身份 |
| `bindings` | `[{resourceTypeCode,resourceCode,codeType,predicates}]`，绑定业务键唯一且对应已登记范围资源；不能用资源显示名称匹配 |
| `predicates` | 非空数组，每项 `{fieldKey,operator,valueSource,value?,values?}`；同资源项 AND，不同有效项 OR |

operator 仅 EQ / IN。LITERAL+EQ 要求一个类型匹配的 value；LITERAL+IN 要求非空 values 且不能同时传 value。SUBJECT_ID 只允许与用户 ID 类型字段作 EQ，不接受自报 value/values。拒绝未知字段/参数、多余值来源、不匹配类型、空谓词和超预算；不可把未知资源项忽略后继续产生部分允许结果。

操作绑定和值谓词都是运行时配置，不提供 SpEL、脚本或自由嵌套逻辑。行范围与字段范围使用各自格；查询要求的主操作未命中即拒绝。参与 INSTANCE 谓词的本地范围资源必须启用且当前版本已发布。字段范围实例仅映射当前报表启用字段，已知其他报表字段排除，未知字段身份为配置错误。ALL 不取消租户、业务有效状态或另一个维度的限制。配置与查询参数限额统一见[执行预算](example-permission-demo.md#execution-budgets)。

<a id="records"></a>
## 记录读取与备注修改

共同查询选择 `Selection` 为 `{reportCode,fieldIds?,filters?,sort?}`：

- fieldIds 省略表示当前全部可读字段；显式空数组拒绝，显式包含无权/未知/失效字段则整次拒绝，不静默裁剪。ID 必须归属当前报表。
- filters 为 `{fieldId,operator,value?,values?}[]`，仅 EQ/IN，取 AND；只能使用可读且 filterable 的字段。sort 为单项 `{fieldId,direction}`，direction 为 ASC/DESC，只能使用可读且 sortable 字段。未指定排序采用内部稳定主键顺序，但不输出该主键。
- 当前策略和目录版本由服务端捕获；权限返回后取数前再次核对，版本不兼容为冲突，不按新目录解释旧 fieldId。

查询公共元数据为 `{state,policyVersion,catalogVersion,columns}`；columns 为允许输出的 `{fieldId,fieldKey,dataType}`。state 为 DATA / EMPTY_ROWS / EMPTY_FIELDS，任一必要权限 DENIED 使用错误信封而不是空态。无可见字段时 columns 为空、数据为空、total=0；字段可见但行空时可返回已授权字段描述。

| POST 路径 | 请求 | 成功 data |
|---|---|---|
| `/api/example/demo-data/fields` | `{reportCode}` | `{policyVersion,catalogVersion,items:[{fieldId,fieldKey,dataType,filterable,sortable}]}`，仅读者可读字段，不查询业务记录 |
| `/api/example/demo-data/page` | Selection + `pageNum,pageSize` | 公共元数据 + `page:PageResp<字段键值对象>`；SQL 内过滤和分页，items/total 同快照 |
| `/api/example/demo-data/get` | Selection + `recordCode` | 公共元数据 + `record`；授权范围内不存在目标时 record=null、EMPTY_ROWS，不泄漏范围外对象存在性 |
| `/api/example/demo-data/batch-get` | Selection + `recordCodes[]` | 公共元数据 + `items:[{index,available,record}]`，与请求顺序对应；不可见/不存在项 record=null；不回显隐藏编号 |
| `/api/example/demo-data/update` | `{reportCode,recordCode,remark?,remarkClear?}` | `{updatedCount}`；按工程规范显式清空，不回传修改后的值 |
| `/api/example/demo-data/batch-update` | `{reportCode,changes:[{recordCode,remark?,remarkClear?}]}` | `{updatedCount}`；批量原子，任一目标无权/不存在/不可编辑整体失败，零部分修改 |

批量读写最多 100 个目标，重复目标拒绝。读取使用 read 绑定；修改使用 edit 绑定和 remark 字段编辑权，不把读权当写权，也不强制可写字段先可读。SQL UPDATE 仍带租户、有效批次、有效记录和权限谓词；输入只允许 remark，不把任意 JSON 属性绑定进实体。

remark 未传/null 表示不修改；`remarkClear=true` 才清空。非空新值与清空同传、空白新值、一次修改未触达任何字段均返回 400/90001。清空实际使用显式 NULL 更新，不能依赖忽略 null 的实体整行更新。批量每项独立校验形态，再整体鉴权与原子提交。

<a id="tasks"></a>
## 快照、内部定时任务与结果

一次性快照 submit 与 execution get/page/download 已实现，T-ACCESS-141 已完成验收；任务定义与 cron 触发已实现，T-ACCESS-143 已验收。下载成功返回 `text/csv;charset=UTF-8` 与 `Cache-Control: no-store`，失败使用统一错误信封。

任务权限使用类型级 `DEMO_TASK` 操作，且所有定义、执行和结果入口都校验可信租户/原创建者。其他用户与不存在统一返回 30004，不提供代他人执行或维护入口。

| POST 路径 | 请求 | 门禁与响应 |
|---|---|---|
| `/api/example/demo-task/create` | `{name,cron,selection}` | CREATE；保存可信创建者，初始停用；验证报表 EXPORT/read 与选择身份；返回任务定义 |
| `/api/example/demo-task/get` | `{taskId}` | VIEW + 本人归属；返回 `{taskId,name,cron,selection,enabled,version}` |
| `/api/example/demo-task/page` | `{pageNum,pageSize}` | VIEW；只查询当前租户、当前创建者的任务，分页返回定义 |
| `/api/example/demo-task/update` | `{taskId,expectedVersion,name,cron,selection}` | UPDATE + 本人归属；CAS，主体不可替换；对更新后选择重新校验；旧执行冻结内容不变 |
| `/api/example/demo-task/set-enabled` | `{taskId,expectedVersion,enabled}` | ENABLE + 本人归属；CAS；停用阻止新执行且执行前再次核对 |
| `/api/example/demo-task/trigger` | `{taskId,triggerKey}` | TRIGGER + 本人归属；启用任务登记一次手动执行，返回执行状态；triggerKey 由脚本生成，重试沿用 |
| `/api/example/demo-snapshot/submit` | `{triggerKey,selection}` | TRIGGER，报表 EXPORT/read；以当前操作者创建一次性快照执行，不强制先建定时任务 |
| `/api/example/demo-execution/get` | `{executionKey}` | VIEW + 本人归属；返回受控执行状态，不重新检查已成功源数据权限 |
| `/api/example/demo-execution/page` | `{taskId?,pageNum,pageSize}` | VIEW；只查询本人执行，指定 taskId 时同时核对任务归属；可发现自动触发的执行键 |
| `/api/example/demo-execution/download` | `{executionKey}` | VIEW + 本人归属；仅未到期 SUCCESS 可领取，成功直接返回 CSV 字节，失败使用错误信封 |

Selection 与记录查询共用，但不含分页；省略 fieldIds 为默认选列，显式选列/筛选/排序均存稳定 fieldId，不按名称重绑。cron 和容量、重试、到期参数按[执行预算](example-permission-demo.md#execution-budgets)，不从请求传入 runAsUserId/clientIp/目标 URL/方法/SQL。

同租户 triggerKey 唯一并绑定原操作者与请求内容，复用同号但改变任务/选择拒绝；已存在执行只返回原执行状态，不覆盖其冻结配置或生成第二份结果。定时触发以 `(tenantId,taskId,scheduledTime)` 唯一，执行键只生成一次；不同手动 triggerKey 是不同主动执行。手动与定时共用执行器，执行前检查创建者当前 TRIGGER、报表 EXPORT、read 行列权限和任务有效性。

执行响应为 `{executionKey,taskId?,status,attempt,scheduledTime?,policyVersion?,catalogVersion?,outputColumns?,recordCount?,expiresAt?,reason?}`。PENDING/RUNNING 尚未产生的版本或产物字段为空，不伪造提交时版本作为执行时版本；错误原因来自受控词表，不带原始异常或无权业务值。

冻结选择引用的字段被删除、停用或已不属于该报表时为 FAILED（OBJECT_UNAVAILABLE）；字段身份仍有效但读取授权撤回时为 DENIED。在线选择接口继续使用相同的无权/未知拒绝语义，不新增存在性反馈。

状态为 PENDING → RUNNING → SUCCESS / DENIED / EMPTY / SKIPPED / FAILED，SUCCESS 到期转 EXPIRED；暂态技术失败在预算内沿原键回 PENDING。每次抢占增加 attempt，输出字节和 SUCCESS 在同一事务按当前 attempt 提交；旧尝试不能覆盖新尝试。服务重启将预算内遗留 RUNNING 重新排队，已耗尽尝试预算的未完成记录转 FAILED，不能靠重启绕过上限。SUCCESS/EXPIRED 不重新取数；停机期间未产生记录的 cron 时刻不补发。

任务维护、手动触发和 cron 入队在同租户数据集守卫下串行，并锁内读取当前任务状态/版本。启用和修改按当前数据库 UTC 时间计算下次触发；每次调度最多入队一个当前到期时刻，并计算后续时刻。启动将启用任务的未来时刻重新初始化，既有执行保留原键与冻结选择；不按旧配置补发停机时段。执行器在远程授权之前及本地取数快照开始时检查任务当前启用状态，已进入取数快照的执行不因随后停用追溯撤销。

CSV 只输出最终授权列，沿既有引号转义与字符串公式前缀防护。返回 CSV 使用受保护 POST 下载，不返回公开 URL；新增下载路径须登记相应 HTTP 契约例外。领取不延长 expiresAt；清理仅移除到期内容并标 EXPIRED，保留执行身份/唯一键与受控状态。

<a id="business-errors"></a>
## 新业务错误码

下列编号随所属能力登记到 ExampleErrorCode；现役业务族已登记下表编号；业务失败沿现役 BizException/统一错误信封，结构与格式非法仍走公共 90001 / HTTP 400。

| 编号 | 含义 | 暴露边界 |
|---|---|---|
| 30101 | DEMO_OBJECT_UNAVAILABLE | 本地报表/字段不可用或任务已停用；任务、执行和导入批次的跨用户/不存在统一 30004 |
| 30102 | DEMO_POLICY_INVALID | 无生效策略、未知绑定或配置校验失败；不泄漏 SQL 或未授权记录值 |
| 30103 | DEMO_VERSION_CONFLICT | 配置/目录/任务期望版本不符；重新读取后发起新操作 |
| 30104 | DEMO_IMPORT_CONFLICT | importKey 换载荷，或有效 recordCode 冲突；message 使用对应的受控说明，不回显记录值，不给统一错误信封新增 reason 字段 |
| 30105 | DEMO_CSV_INVALID | 受控行号/字段/原因，不包含原始行或隐藏值 |
| 30106 | DEMO_LIMIT_EXCEEDED | 文件、行数、配置、参数、执行容量超限；不以部分成功代替 |
| 30107 | DEMO_RESULT_UNAVAILABLE | 已知本人执行尚无结果、非 SUCCESS 或已到期；状态接口仍能看到状态 |
| 30108 | DEMO_RESOURCE_NOT_READY | 当前源版本尚未发布成功；不能用历史已发布状态冒充就绪 |
| 30109 | DEMO_TRIGGER_CONFLICT | triggerKey 被用于另一份请求，不改写原执行 |

读路径使用本地只读一致快照核对策略、报表、目录及实际使用的范围版本，再查询 items/total；写路径在共同数据集行锁内核对并原子修改。SQL 采用固定字段目录的标识符与绑定参数，超时为 30106，参数预算包含固定条件和目标定位参数。

字段描述 dataType 为 STRING / USER_ID / DATE / DECIMAL；日期值使用 ISO 日期字符串，金额不经过 double。

策略版本、目录版本和任务版本都是本地乐观并发标识，不是权限中心的 permissionVersion，不新增跨服务版本协议。实现与测试不得把这些本地版本当作仍然拥有权限的证明。
