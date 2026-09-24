---
doc_type: task
id: T-ACCESS-054
title: "外围任务能力与缓存过渡机制取舍"
status: done
plan: docs/plans/iam-task-closure-plan.md
domain: cross-service
design_refs:
  - docs/design/iam-task-closure.md#trimming
  - docs/design/access-service-architecture.md
depends_on:
  []
blocks: []
acceptance:
  - "U010明确现役任务/计划内Reconciler是否消费底座，保留/缩小/删除均有可追溯理由及影响面。"
  - "U011有部署证据才删除legacy；无证据明确保留及后续删除条件，不能假设旧实例为零。"
  - "若执行裁剪，调用面/配置/文档/测试同步；保留时不引入新层；需要更大改造则独立登记不伪报完成。"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-24
---

# T-ACCESS-054 外围任务能力与缓存过渡机制取舍

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 R002、R003；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- 通用任务管理真实消费者与依赖盘点；ORG_VISIBILITY_LEGACY旧部署兼容前置核验。
- 按最终决定保留、缩小当前交付面或定点裁剪；不连带删除正确性基础设施。

## 实施记录（2026-09-24 收口）

**盘点结论**：

- **U010 底座消费者**：全仓唯一 `@JobInvocable` 白名单方法=自动授权对账任务（T-PERM-073 adopted，registry 2026-09-21 定案在案「任务设施首个业务消费者」）；系统维护调度器两个（JobScheduleReconciler 60s 配置对账 / TaskLeaseTakeoverScheduler 30s 租约接管，architecture §8.1 权威叙事）；租约/fencing/幂等/线程隔离不可删。前端零任务页、契约未成册（§17.3）、外部零消费、部署扩展零。
- **U010 管理面缺口①（运营路径不可达）**：ADMIN_JOB 在 bootstrap 固定图零授权——新部署 job/trigger、toggle、CRUD 全 403（T-ADMIN-029 红跑实证的公告同款形态）；T-PERM-073 拍板「管理员按需手动触发或启用周期巡检」无正规入口。
- **U010 管理面缺口②（读端点零门禁）**：job/detail、page、log/page 三读端点无任何权限校验（JobAppServiceImpl 旧实现 384/401/431 行），任意租户登录用户可翻任务配置（invokeTarget/cron）与执行日志。
- **U011**：ORG_VISIBILITY_LEGACY=Q-006（T-ACCESS-048，2026-09-16）evict-only 兼容层；v0.1.0 tag 同日已含双 evict，只有更早构建写旧键（TTL 60s）；源码不能证明无旧实例在跑。

**两项拍板（AskUserQuestion 两问，2026-09-24）**：

- **U010=补最小授权+读端点补门禁（推荐项）**：固定图补 ADMIN_JOB VIEW/TRIGGER/ENABLE 三档类型级（不可转授，DDL 已预置位 bit2/16/64）；三读端点补 `ADMIN_JOB:VIEW` 类型级门禁（拒绝先于任何读取）；CREATE/UPDATE/DELETE 维持无种子（启用面收窄到采纳决策所需最小集，改 cron 走运维通道）。
- **U011=删除（用户确认无 pre-2026-09-16 构建实例在跑，含本机 dev 栈）**：别名条目+PermissionChangeAspect 第二次 evictAll+三处回归锁整体删除；PermCacheBoundaryValidator 未知覆盖键 WARN 保留（旧 code 天然落未知集合，告警语义不变）；BoundaryTest 册内条目锁升反射精确集双向锁（9 条目）。

**落地**：BootstrapGraphDefinition +3 GrantSpec（存量已初始化库重启按加行先例 fail-fast 拒启——缺行无墓碑不自动补种，处置=重建库，runbook 补行；双轨评审 P1 订正初稿误写）；AccessBootstrapInitializer GRANT_RESOURCE_TYPES 补 ADMIN_JOB（漏项=操作位 fail-fast，PgIT 实证后同批修）；JobAppServiceImpl 三读端点 +VIEW 门禁；AccessCacheCatalog/PermissionChangeAspect/PermCacheBoundaryValidator 别名删除；PermissionChangeAspectTest 两处 legacy verify 删除、DualInstanceCacheInvalidationTest 滚动改名用例重写为单命名空间前缀扫描锁（保留 store 层 evictAll 能力断言）、AccessCacheCatalogBoundaryTest 别名形态锁删除+册内条目锁反射化；skill 双副本机制描述改历史先例口径。

**双轨评审处置**（代码轨 P1×1+P2×1+P3×3、文档轨 P0×1+P1×1+P2×1+P3×3，核实全部成立）：①「存量库重启自动补种」声明有误（主代理亲核 initialize() 四自愈通道不含授权行，实为加行方向 fail-fast）——五处文档改口径+rebuild-runbook 补行；②读门禁拒绝路径补 count never 锁、ADMIN_JOB 负向锁；③P3-2 写端点门禁序顺手修（用户拍板）：updateJob/toggleJobStatus 先门禁后存在，红跑 1/1 红→恢复 10/10 绿；④architecture §14.4 最小集表全量对齐刷新（含 ADMIN_NOTICE 漏账补行）；⑤Q-006 死链/frontmatter 元数据直修。

**验证**：JobAppServiceImplTest 读门禁两锁（回退三行门禁红跑 **2/2 红**→恢复 9/9 绿）+ AccessBootstrapPgIT 17/17（回退三 GrantSpec 红跑计数断言红；恢复后先因 GRANT_RESOURCE_TYPES 缺 ADMIN_JOB 报「operation_permission 种子缺失: ADMIN_JOB:VIEW」fail-fast——实证固定图类型清单同步约束，补清单后全绿；计数 148→151/50→53+ADMIN_JOB 精确断言 granted_bits IN (2,16,64)=3）+ 单测轨 1397 全绿 + 全量回归 `mvn test -T 1C` 含 E2E 16 项与 heavy 组 BUILD SUCCESS、reactor 合计 2013 项 0 失败（access-service 单测 1398+容器 337）。

**回写**：契约总册 §17.3 job 族括注（门禁补齐+存量库自动补种口径）；iam-task-closure §6.2 转已实施；capability-structure §3/§8.2 终态口径；pending-problems Q-006 终结注记；dual-layer-cache-framework skill 双副本；CHANGELOG Changed 两条；registry 2026-09-24 行；rebuild-runbook 常见问题表补 T-ACCESS-054 行。

## 非目标 / 遗留

- job 管理面无前端页、契约维持未成册（§17.3 括注即登记形态）；CREATE/UPDATE/DELETE 端点维持交付但固定图无授权（真实消费者零）——任务管理 UI 如未来立项另行拍板。
- 任务底座（sys_job 调度/租约接管/幂等执行键/两系统调度器）保留为终态，非过渡物。
- catalog 改名兼容机制（evict-only 别名模式）保留在 skill 供未来复用，册内现有零别名。

## 验收对照

唯一验收清单见 frontmatter `acceptance`。

- **U010**：盘点结论可追溯（对账任务=T-PERM-073 adopted 消费者、两调度器=architecture §8.1、registry 定案在案）；处置=保留底座+最小面兑现（三档授权+读门禁），影响面同步（调用面/契约括注/文档/测试/计数锁全批）。
- **U011**：删除依据=用户部署事实确认（唯一可回答主体），非源码推断；删除面含回归锁与 skill 双副本同步；WARN 语义保留。
- **裁剪执行**：调用面/配置/文档/测试同步完成；保留部分未引入新层；无需更大改造（无独立登记项）。
