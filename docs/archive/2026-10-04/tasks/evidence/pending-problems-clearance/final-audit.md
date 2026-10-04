# pending-problems-clearance 最终验收

2026-10-04，计划内任务全部完成。定案任务按原计划边界交付当前契约与后续实施载体，不把后续代码目标冒充已实现。

## 实际执行

- `mvn test -T 1C`：2026-10-04 12:32（Asia/Shanghai）BUILD SUCCESS，16:37；按实际测试类报告聚合 2468 项，失败 0、错误 0、跳过 0。E2E 与 heavy 均运行，未带跳过开关；输出整文件落盘后解析，未重复累加 execution 汇总。运行期间未修改源码。
- E2E execution 29 项通过：BasicRoleGrantVerticalSliceE2EIT 8、ExampleBusinessFinalCheckE2EIT 11、ExampleProtectedApiE2EIT 8、E2eProcessSupportTest 2。
- 前端 `pnpm test`：41 文件、487 项通过；`pnpm typecheck`、`pnpm lint`、`pnpm build` 退出 0。
- 原样 schema COMMENT 与资源过滤接线定向验证：`mvn test -pl access-service -Dtest=AccessServiceSchemaPostgresTest,ResourceOperationKeyPgIT#resourceTreeIncludesDisabledResourcesByDefault`，22 项通过，零跳过。

## 范围与结论

代码轨核对入口校验与门禁、既有树锁、批量加载、父名可见性、过滤后展示根、服务调用形状、调度注册观测与共享遍历。文档轨独立核对权威契约、schema/注释、任务状态、问题收敛、后续安排与链接。无未处理 P0–P3 或待用户决策项；未引入新的诊断授权框架、锁框架或通用 patch 机制。

后续实施卡不属于本计划任务清单：

- [T-PERM-105](../../T-PERM-105.md)：实际删除 grant_dep_id。
- [T-ADMIN-035](../../T-ADMIN-035.md)：OAuth2 同租户与动态用户检查。
- [T-API-006](../../T-API-006.md)：剩余字段 Clear 协议实现。
- [T-ACCESS-079](../../T-ACCESS-079.md)：四查询端点/SDK/示例服务凭证同批硬切。
- [T-ACCESS-080](../../T-ACCESS-080.md)：剩余同步凭证化与外部共享密钥退出。

这些卡仍为 proposed；当前实现差异保留在对应设计，不隐去。Q-056/Q-058 按原计划排除范围维持 open。用户进入本任务前已有未提交的测试精简改动得到保留；本次未将它们回滚或自动提交。

## 归档检查

计划、所属任务卡与附属证据一同迁至本日归档；索引仍保留任务终态。检查本计划所有任务终态、来源问题去向、后续任务号唯一和依赖无环、活动文档及本批档案相对链接可解析，旧活路径无残留。
