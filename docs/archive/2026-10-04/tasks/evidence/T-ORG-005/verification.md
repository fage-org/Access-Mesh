# T-ORG-005 成员动作码验证

2026-10-04：createUser 保留 USER:CREATE，带 orgId 时复用 OrgOperationCodeMapper.resolveForUserOrg，普通组织 MANAGE_MEMBER、岗位 ASSIGN_POSITION_USER；目标类型与默认树范围在同一 SYS_ORG 锁内读取，复用该目标对象做投影，不增加查询。

临时恢复旧 ORG:UPDATE 门禁时，两个仅持相应成员权限的创建案例都被 SecurityException 拒绝；恢复当前实现后通过。定向命令见 [T-ACCESS-065 证据](../T-ACCESS-065/verification.md)，单测 80、容器 27 项通过，含真实 createUser 带组织投影链。最终完整回归已通过，见[最终验收](../pending-problems-clearance/final-audit.md)。

代码轨：未新增操作码或端点，USER:CREATE、事务、默认树归属与投影保持；无 orgId 时不加树锁。文档轨：契约 §3/§7.3 与组织关系契约 §5 同步；岗位候选裁剪与其他入口门禁维持 T-ACCESS-055 已确认范围，不声称本卡调整了全部前端候选入口。
