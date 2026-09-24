---
doc_type: task
id: T-ADMIN-029
title: "公告状态与受众生命周期闭合"
status: done
plan: docs/plans/iam-task-closure-plan.md
domain: access-service
design_refs:
  - docs/design/iam-task-closure.md#notice
  - docs/design/access-service-api-contract.md
  - docs/design/schema/access-service.sql
  - docs/design/access-service-architecture.md
depends_on:
  []
blocks: []
acceptance:
  - "草稿对接收者不可见，发布后仅目标租户/受众可见，撤回后不可读/标已读；全员公告按契约处理。"
  - "单用户/多用户/空受众和非法目标ID表示有明确语义，真实JSONB写读通过。"
  - "U007落定DTO迁移；历史错写状态不能仅按值全量翻转，恢复或无存量结论有依据。"
  - "正常业务API路径可验收，不以权限门禁403冒充公告行为验证。"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-23
---

# T-ADMIN-029 公告状态与受众生命周期闭合

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 F010；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- NoticeAppServiceImpl/Controller/Mapper的草稿、发布、撤回、我的公告和已读链；目标用户typed IDs及JSONB。
- 只读盘点存量状态与受众形态，必要时给定点迁移；不扩公告UI。

## 当前口径

方案唯一入口：[IAM闭环方案](../design/iam-task-closure.md#notice)（§4.5 实施定案已回写）。U007 四项拍板（2026-09-23）：typed IDs 一次性切换（零兼容层）/ORG 受众预留不做/严格状态机转换拒绝/my-notices+read 白名单+管理面 bootstrap 五档类型级授权——定案全文见 registry 2026-09-23 T-ADMIN-029 行。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；逐条证据见完成记录。

## 完成记录（2026-09-23）

- **后端主链**：DTO 三件迁 `targetType`(ALL/USER，缺省归一 ALL)+`targetUserIds: List<Long>`（`List<@NotNull Long>` 容器元素校验）；`NoticeAppServiceImpl` 重写——create=草稿 0、publish 0/2→1（重新发布已读延续+publishedAt 刷新）、新增 `revoke` 1→2（保留已读与 publishedAt）、非法/重复转换 10402（新错误码 NOTICE_STATUS_CONFLICT）；受众校验（ALL 带 IDs 拒 10008/USER 空/缺省拒 10008/非法目标用户拒 10001/ORG 值域拒 90001 @Pattern）；JSONB 数字数组真写读（Jackson 序列化 `[101,102]`+JsonbStringTypeHandler）；my-notices SQL 受众过滤（`target_type='ALL' OR (target_type='USER' AND target_ids @> to_jsonb(userId))`，USER 空/NULL 数组 fail-closed）；read 前置 countVisibleById 可见性校验（草稿/撤回/非受众/不存在统一 10401 不泄露存在性）；delete 级联物理清理 sys_user_notice（兑现 Controller 历来 javadoc 声称）；update 用 UpdateEntity 显式列集（USER→ALL 切换 target_ids 真置空——update(entity) 忽略 null 列会残留旧受众数组，memory 先例坑规避）；detail/page 补 ADMIN_NOTICE:VIEW 类型级门禁（原零校验）；update/delete/publish/revoke 门禁从 checkInstanceLevel/checkBatchInstanceLevel 收敛类型级（ADMIN_NOTICE 无 resource_entity 投影，实例级无资源可挂）。
- **可达性（验收④）**：bootstrap apiRoutes +9 行（管理面 7 含新 /notice/revoke + my-notices/read 白名单回滚面 2）+businessGrants +5 档（ADMIN_NOTICE VIEW/CREATE/UPDATE/DELETE/PUBLISH 类型级）；GRANT_RESOURCE_TYPES 常量同步 +ADMIN_NOTICE（缺项 fail-fast，首跑实证）；Gateway 白名单 +my-notices/read 两行（reset-password/T-GW-009 先例形态——普通用户自服务，服务层登录态+可见性校验）。**F010「请求被门禁拒绝」根因闭合**：notice 全族此前 bootstrap 零注册被网关 fail-closed 403，行为验证只能以门禁拒绝冒充；授权后行为面经真权限链可验收。
- **存量结论（验收③）**：无存量——依据三条：bootstrap 不种公告数据、项目无生产部署、开发库经 rebuild-runbook 重建即可；不出状态翻转/迁移语句（不凭值全量翻转的要求天然满足）。已初始化旧库重启因固定图缺 9 行 API 报「部分存在」fail-fast 为预期（加行方向与 T-FE-058 删行方向子集放行相反），处置=重建库（runbook T-ADMIN-029 行登记；四件套手工 INSERT 易错且无部署环境消费，不为假想环境维护未验证 SQL）。
- **红跑实证（两层）**：①typed 数组请求在旧实现下 90001 拒收（旧 DTO targetUserIds 为 String，Jackson 反序列化失败——零兼容层切换锁）；②旧逗号串请求在旧实现下 **403**（bootstrap 无 ADMIN_NOTICE 授权，管理面服务层即不可达——F010 根因实证；行为断言〔草稿 status=0/受众过滤〕在旧实现下被门禁挡住不可触达，本卡授权后新实现 2/2 全绿才可验收）。
- **验证**：NoticeLifecyclePgIT 2/2（MockMvc 真链+真登录会话+真权限引擎：生命周期主链七阶段+受众语义矩阵〔JSONB roundtrip/矛盾表达/非法目标/ORG/USER→ALL 置空/删除级联/无授权 403〕）；AccessBootstrapPgIT 17/17（计数 89→98 API 行/88→97 映射/134→148 MANUAL/45→50 scopeAll）；单测轨道 1385 项全绿；快照 8/8（+revoke 行）；gateway 全绿。
- **文档回写**：iam-task-closure §4.5 实施定案+frontmatter、registry 定案行、runbook T-ADMIN-029 行、CHANGELOG（Added/Changed/Fixed 三条）、契约总册 §17.3 notice 族括注+frontmatter（维持未成册口径）、DDL sys_notice 三注释勘误（notice_type 数字口径对齐/target_type ORG 预留注记/target_ids JSONB 数组形态——AutoGrantMigrationPgIT 快照比对不含 sys_notice，无迁移脚本联动）。
- **双轨评审处置**（2026-09-23）：代码轨 **P0×1**（亲核全链属实）——my-notices/read 真实 Gateway 链路恒 400 不可达：白名单四载体只做了 yml 一处（WhitelistFilter 置 skipAuth→HeaderEnrichFilter 不注入用户/租户头，InternalSecretFilter 无条件注密钥；下游密钥拦截器未豁免→internalAuthenticated 无用户头→RequestContextInterceptor「纯服务调用」分支缺 X-Tenant-Id 恒 400；reset-password 先例实为四载体）→ **补齐 SecurityWebMvcConfig 豁免两行+GatewayProperties defaults 两行+ConfigTest 断言两行**（豁免后走 Sa-Token 会话分支，tenant 从会话读与 StpUtil 同源），PgIT 自服务请求改真实白名单转发形态（密钥头+令牌、无用户/租户头——旧夹具注入完整凭证头属非白名单形态，假阳性根源）；**红跑实证**：移除豁免两行 my-notices 恒 400「缺少必要请求头: X-Tenant-Id」（断言消息逐字命中断链形态），恢复后 2/2 绿。**P2×2 直修**：publish/revoke 读-判-写竞态→条件 UPDATE 原子转换（publishFrom 0/2→1/revokeFrom 1→2，0 行转 10402——严格拒绝语义并发下原子成立）；markNoticeAsRead 先查后插撞 uk→ON CONFLICT DO UPDATE 幂等 upsert。**P3×2 直修**：门禁调用序统一（update/publish/revoke 先门禁后存在性查询——先查后判权的差异可探测公告 id 存在性）；normalizeAndValidateTarget 显式值域 else throw（服务层兜底 fail-closed，含拼错值）。**P3×4 维持登记遗留**（update 受众缺省=全量更新语义/noticeType parseInt 无容错/my-notices 无分页/targetUserIds 无 @Size——既有形态或惯例一致）。**裁剪×2 直修**：deserializeIds 容错分支改上抛（拍板无存量，静默吞损坏呈现为「空受众 USER 行」）；isBlank 归一分支随显式值域拒绝消解。文档轨 **P2×1 直修**（SysNotice 实体 javadoc 三处旧口径——ORG 列合法值/逗号分隔/两态描述，DDL 勘误同主题漏网）+**P3×1 直修**（architecture:304 类名漂移 NoticeServiceImpl→NoticeAppServiceImpl）。处置后定向 NoticeLifecyclePgIT 2/2+gateway 全绿+单测轨道 1385 项全绿。

- **claude 外评处置**（2026-09-24，astron-code-latest headless plan 模式，commit `1a59fa0c2` 后）：**P0/P1/P2=0**——五项专项清单全过（写 6×读 3 入口全枚举、白名单四载体一致性含第五代码载体排查、状态机逐分支对照、双轨修复复核=未发现修复引入新缺陷、JSONB 数据完整性含并发面），主体链路零生产级缺陷。**P3×2 直修**：①豁免清单枚举副本失实三处（SecurityWebMvcConfig 方法级注释去枚举化指向 excludePathPatterns——类级 T-GW-009 已改、方法级同段漏网；SecurityMatrixIT @DisplayName「只含 reset-password」改「user 族豁免仅 reset-password」；gateway.md §核心链路+§匿名白名单两处清单补 my-notices/read+last_reviewed）——同款清单漂移正是双轨 P0 单载体断链成因形态；②`selectByNoticeAndUser` 死方法删（唯一调用方随 upsertRead 改造消失，接口+XML 双残留，T-PERM-059 ⑤ 同形）。**可裁剪×2 采纳直修**：toResp targetType 空值回退删（DDL NOT NULL DEFAULT 'ALL' 焊死）；deserializeIds isBlank 收窄为 null 判断（jsonb 非空文本恒有可见字符不可达）。**存量观察 3 条不处置**（NotLoginException 无全局映射落 500——前后同形未放大且经 Gateway 不可达；deleteFlag 实体 javadoc 与 DDL 漂移——既有；平台 XML foreach 无空集守卫——调用方有守卫）。处置后定向 NoticeLifecyclePgIT 2/2+SecurityMatrixIT 14/14+BUILD SUCCESS。

## 非目标 / 遗留

- ORG 受众（按组织成员动态展开）预留未实现、传值拒绝——独立产品语义另行立项（拍板口径）。
- 前端公告 UI（lay-notice 模板 mock）零改动——「不扩公告UI」任务边界；接真实 /notice/my-notices 属前端消费任务另行立项。
- 公告编辑不设状态限制（已发布/已撤回可编辑，维持现状最小改动——已读记录挂 noticeId 语义自洽，无验收要求）。
- update 受众缺省=全量更新语义（双轨评审 P3 维持）：漏传 targetType/targetUserIds 会把 USER 公告切为全员可见——与 title/content 必填同款全量契约，防误用属前端表单任务（届时 targetType 显式必填）。
- 既有低危形态维持（双轨评审 P3）：noticeType 读取 parseInt 无容错（写入面受控 DDL 无 CHECK，旧实现同形态）；my-notices 无分页/LIMIT（旧实现同形态延续，公告中心产品化时一并设计）；targetUserIds 无 @Size 上限（IdsReq 同款惯例）。
