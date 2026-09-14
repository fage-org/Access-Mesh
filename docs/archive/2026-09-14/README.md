# 2026-09-14 归档批次

## 批次一：access-service 能力包融合计划终态归档

## 归档原因

T-ACCESS-032~041 十任务全部 done（末卡 041 规则与技能文件重写于 2026-09-14 收口），验收五条达成、capability-structure §9 定案修订对照全部落地；本地双轨评审 + claude/grok/codex-sol 三通道外评全处置后，用户确认批次定稿并归档。

## 本批次内容

| 内容 | 去向 |
|------|------|
| access-capability-fusion-plan.md | 本目录（status: archived；十任务 T-ACCESS-032~041 全 done） |
| 任务卡十张（T-ACCESS-032~041） | 本目录 `tasks/` |

## 终态权威去向

- 结构契约：`docs/design/access-service-capability-structure.md`（adopted——§8 归属清单十项裁决、§8.4 架构断言五测试）
- 单册设施：`engine.constant.OperationCode`（034）、`infrastructure.enums.AccessErrorCode`（038）、`infrastructure.cache.AccessCacheCatalog`（039）
- 契约与内档：`docs/design/access-service-api-contract.md` 总册（040）+ `docs/design/engine/` 三册
- 规则与口径：`.claude/rules/permission-coding-standards.md`（041）+ AGENTS.md 指针表；域叙事词汇=管理面/权限面（registry 2026-09-14 行）
- 定案：decision-registry 2026-09-13 融合定案行 + 038/039/041 拍板行
- 遗留登记：pending-problems（Q-001 URL 两风格、Q-006 滚动发布/指标面、Q-009 冻结白名单收敛、Q-010 死方法等）

## 批次二：frontend-phase2 与 design-audit-followup 计划归档（轻量清扫）

### 归档原因

frontend-phase2（2026-06-29 立项）与 design-audit-followup（2026-09-05 立项）的执行任务全部 done，仅剩暂缓项——T-PERM-035/036（design-review §11 暂缓门禁，等 PM 重申）与 T-PERM-054（关联权限自动授权方向已定 2026-09-09、方案未定）随本批次脱出计划挂任务看板（plan 字段 —、门禁不变），两计划按剩余范围归档（2026-09-14 用户拍板「轻量清扫+计划归档」）。无计划归属的终态卡 T-PERM-065 一并单卡归档（归档滞留禁令补账，2026-09-12 收口后滞留 docs/tasks/）。

### 本批次内容

| 内容 | 去向 |
|------|------|
| frontend-phase2-plan.md | 本目录（status: archived；T-PERM-025~031/033/034/037/040/041、T-FE-036/038~040、T-ADMIN-021 十七卡随迁 tasks/） |
| design-audit-followup-plan.md | 本目录（status: archived；T-PERM-052/053、T-API-002、T-ACCESS-029 四卡随迁 tasks/） |
| T-PERM-065.md | 本目录 tasks/（单卡归档，原无计划归属） |

### 暂缓项去向

T-PERM-035/036/054 留 `docs/tasks/`（⚙️ proposed、plan 字段 —，看板行已标注脱出来源）；重启经 PM 重申 / 方案定案后直接以看板任务推进，不再依赖已归档计划。
