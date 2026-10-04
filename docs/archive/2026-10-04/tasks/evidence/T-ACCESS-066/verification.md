# T-ACCESS-066 文档与注释核对

2026-10-04，仅本卡的文档、Java 注释与 DDL COMMENT 改动；工作树另含其他任务代码，不将整个 diff 混称 doc-only。

| 主题 | 处置与依据 |
|---|---|
| Gateway 白名单 | architecture 与 access-service-architecture 不再将 auth/** 整族放行；对照 gateway.md、application.yml、SecurityWebMvcConfig 的精确清单；OAuth2 业务路径与密钥豁免分开说明 |
| 过滤器顺序 | SignatureEnrichFilter 注释改为 -35 在 -50/-40 之后，返回值未改 |
| 组织父键注释 | 当前权威 schema 的 sys_org.parent_id 和树根配置注释按 0 根口径/不重叠约束校正，不改列约束、不同步已退役迁移资产 |
| SDK 身份 | 总览补 FeignCredentialInterceptor 的精确 M2M 镜像与凭证优先；旧共享密钥仍覆盖未迁查询，阶段二目标明确另归任务 |
| 用户角色端点 | 管理面查询指向 user-role/view，未改仍在役的其他 list API |
| 旧准入协议 | v3.5 §7.2 旧 DTO/三态 matcher/stale-allow 正文由现行契约 §25 与服务/Gateway 架构链接替代；保留章节锚与其他有效语义 |
| R2 历史稿 | 两处「实施期设计权威」改为历史说明，当前协议指契约 §25 |
| 自动授权 | resolveAutoGrants TODO 注释改指当前 AutoGrantMaterializationDomainService；未更改授权逻辑 |
| 系统配置页面 | 列表改为 usePagedList＋真实 PageResp，保存改为真实 upsert；operation-log 对比同步。描述清空目标与实施差异仍由 T-API-005/006 承载 |

原样 COMMENT 随 `mvn test -pl access-service -Dtest=AccessServiceSchemaPostgresTest,ResourceOperationKeyPgIT#resourceTreeIncludesDisabledResourcesByDefault` 验证，22 项通过，零跳过。Java 注释无行为变化；其余纯文档以相关实现/契约、关键词残留及链接核对为证据，不新增业务测试。

代码轨检查本卡源码改动均为注释，DDL 仅 COMMENT；文档轨逐主题独立核对现行权威，未在本卡定案运行时身份或清空协议。未处理发现与待决项均无。
