# T-PERM-102 外部 TRACE 边界验证

2026-10-04：生产调用盘点为 QueryGate（minimal）、check/batch-check 与 admission（kept）、query-resources/query-scopes、权限视图和转授检查（显式 trace=false）。`OutputSpec.full()` 无生产调用，外部 DTO 无 trace 透传字段；不存在可复现的旧版外部诊断直通。

已在现有 check、资源/范围查询和权限视图测试的实际引擎请求捕获点断言 trace=false；未另建身份、权限码或诊断入口。临时只将 check 适配输出的 trace 改为 true，8 项中 5 项因边界断言失败；恢复后[完整回归](../pending-problems-clearance/batch-2026-10-04.md)通过。QueryAuditAndTraceTest 18 项保持，内部显式 TRACE 的真实阶段与零额外读取语义未变。

代码轨：新增的是已存在外部禁止能力的行为验证，未声称修复不可达泄露。文档轨：实现 §3.5、契约 §1.2、OutputSpec 注释对齐「外部不开放，未来诊断先定义身份授权」，替代此前未实施门禁的含混表述。无待决项。
