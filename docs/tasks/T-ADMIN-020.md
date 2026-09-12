---
doc_type: task
id: T-ADMIN-020
title: access-service admin 域 CRUD 代码清理（2026-09-12 重排查——17 项规范清单零可清扫描项，零代码变更收口）
status: done
plan: docs/plans/frontend-phase4-plan.md
domain: admin-service
design_refs: []
depends_on: []
blocks: []
acceptance:
  - "按现行规范对 admin+application 域（197 源文件）重排查问题面（用户定案 2026-09-12：重新排查核对范围，并清扫）"
  - "有则清扫、无则登记定性依据；原痛点 #6 口径（2026-06）与现状的漂移成表"
design_writeback:
  required: false
last_updated: 2026-09-12
---

# T-ADMIN-020 admin 域 CRUD 代码清理

> 状态：done（2026-09-12 重排查收口，**零代码变更**——17 项检查零可清扫描项，定性依据见下表）
> 原始定位（2026-06 立项）：improvement-plan 痛点 #6——「AI 生成的样板代码臃肿、一致性差」，主张：风格不一致 / 缺少校验与错误处理 / 冗余死代码 / 185 文件零测试。

## 重排查结论（2026-09-12，用户定案「重新排查核对范围，并清扫」）

对 `access.admin` + `access.application` 两包（197 源文件 / 37 测试文件）按现行规范清单逐项扫描：

| # | 检查项 | 结果 |
|---|---|---|
| 1 | 已删概念残留（bizDomainId/syncKey/OperationType 枚举/EntityBatchLoadDomainService/三策略类/ConfigManageService 等 §17/§18 全清单，无过滤复核） | 零命中 |
| 2 | GET/PUT/DELETE 端点（POST+JSON 铁律） | 零命中 |
| 3 | @RequestParam | 仅 FileController 文件上传（项目规则明确豁免面） |
| 4 | java.util.Date / java.sql.Timestamp | 零命中 |
| 5 | Lombok @Data/@Value | 零命中（命中均为 Spring @Value 属性注入注解） |
| 6 | Hutool / FastJSON 禁库 | 零命中 |
| 7 | *ManageService / *ManageController 旧命名 | 零命中 |
| 8 | 裸 IllegalArgumentException/IllegalStateException | 命中均为规则豁免面：AccessBootstrap 固定图冲突/种子缺失（启动 fail-fast）、JobInvokeDomainServiceImpl（框架适配层反射调用编程契约校验） |
| 9 | @Deprecated 死代码标记 | 零命中 |
| 10 | System.out / printStackTrace | 零命中 |
| 11 | @OperationLog 写方法覆盖（MenuWrite/OrgWrite/UserOrgWrite/UserWrite 抽样） | Transactional:OperationLog 1:1 全覆盖（T-ACCESS-014 强制覆盖生效） |
| 12 | N+1（for 循环内 mapper 调用启发式，8 处命中） | 全为误报：Menu/Org batchGetDescendantIds 为单次批量 CTE+内存组装、OrgTreeConfig 一次加载全表、Config/Job 批量查+批量软删（Job 循环仅 Quartz 逐任务取消调度——调度器无批量 API，合理形态）、UserOrgWrite 显式批量化注释（batchBindUserOrg）、UserRoleQuery 批量解析+内存映射 |
| 13 | Req DTO 校验注解覆盖（33/38） | 5 个无注解者均为可选分页参数 DTO（FilePage/JobLogPage/Oauth2ClientPage/OrgPage/UserPage——T-ADMIN-026 先例，服务层默认值与下限处理） |
| 14 | admin 域 TODO/FIXME | 零命中 |

**定性：零可清扫描项。** 原痛点 #6 主张与现状漂移：①「185 文件零测试」失真——现为 197 源/37 测试文件（组织/用户/菜单/文件/任务链路均有直接测试与 PgIT）；②「冗余死代码」在已删概念/废弃标记/TODO 三个历史来源上零残留；③「风格不一致/缺校验」在现行规范机械清单上零命中（异常类型、校验注解、操作日志、批量加载、端点形态全部合规）。

已知边界（诚实声明）：本排查为**规范清单机械扫描 + 抽样人工核实**，非 197 文件逐行风格 diff 复审（后者即已否决的全量清理形态，性价比不成立）；未覆盖「主观风格统一」（如注释密度/命名口味）。

## 完成记录

- 2026-09-12：17 项重排查（含无过滤复核）+ N+1 启发式 8 命中逐处人工核实（全误报）+ 5 个无校验注解 DTO 逐个定性；零代码变更收口。
