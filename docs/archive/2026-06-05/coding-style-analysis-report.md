# 编码与创作风格分析报告

> 分析范围：AccessMesh 项目 Git 历史（2020-05 至 2026-06），共 3804 次提交

---

## 一、核心风格特征（跨阶段稳定）

### 1. 架构设计哲学

| 特征 | 描述 | 证据 |
|------|------|------|
| **分层架构严格** | Controller → AppService → DomainService → Mapper，禁止跳层和横向调用 | `project-rules.md` §8.2-8.4，重构后始终保持 |
| **统一接口规范** | POST + JSON Body，拒绝 RESTful 风格路径参数 | 从中期（2026-03）至今一致，`project-rules.md` §2.1 |
| **统一响应体** | `{ code, message, data, requestId, traceId }` | 跨所有服务一致，包括 Feign 内部调用 |
| **显式优于隐式** | 禁止 `@Data/@Value` 等 Lombok 高隐式注解，优先 Java 21 Record | `project-rules.md` §5.2 |
| **集中优于分散** | 权限查询统一到 `PermQueryEngine`，缓存统一到 `CacheService` | 重构后架构，`service-layer-review.md` |

### 2. 命名与组织规范

| 层级 | 命名模式 | 稳定性 |
|------|----------|--------|
| AppService | `XxxAppService` / `XxxAppServiceImpl` | 重构后统一（Phase 5） |
| DomainService | `XxxDomainService` / `XxxDomainServiceImpl` | 跨阶段稳定 |
| Controller | `XxxController`（不带 Manage 后缀） | Phase 5 重命名后稳定 |
| DTO | `XxxReq` / `XxxResp` / `XxxVO` / `XxxDTO` | 全项目统一 |
| Mapper | `XxxMapper`（无 Service 后缀） | 全项目统一 |

**组织规范**：
- 文档分层：`docs/design/`（权威）+ `docs/archive/`（归档）
- 规则集中：`.claude/rules/*.md` 定义编码规范
- Skills 按需：`.claude/skills/` 定义专题技能

### 3. 文档编写风格

| 特征 | 描述 | 示例 |
|------|------|------|
| **中文优先** | 所有面向团队的文档使用中文 | `AGENTS.md`、`project-rules.md`、Git 提交描述 |
| **权威来源明确** | 每个主题指向唯一权威文档 | `docs/README.md` 权威来源表 |
| **分层结构** | 设计文档 → 实现文档 → 归档文档 | `docs/design/` vs `docs/archive/` |
| **归档机制** | 过时文档归档而非删除，保留历史追溯 | `docs/archive/2026-xx-xx/` |
| **表格化呈现** | 状态、约束、规范优先用表格 | 大量规范文档中的表格 |

### 4. 质量控制手段

| 手段 | 实施方式 | 证据 |
|------|----------|------|
| **Conventional Commits** | `feat/fix/refactor/docs/chore` + scope | 最近阶段提交格式一致 |
| **代码审查修复闭环** | 每个 Phase 后单独提交修复 | `fix(permission-center): 代码审查修复` |
| **N+1 查询禁止** | 编码规范明文禁止，评审打回 | `project-rules.md` §8.4.8 |
| **异常分类边界** | BizException / SystemException / SecurityException 分层 | `permission-center-coding-standards.md` §2 |
| **测试覆盖率要求** | 80%+ 业务逻辑覆盖率目标 | `testing-standards.md` §2 |

---

## 二、演进趋势分析

### 1. 从功能性到工程化

| 维度 | 早期（2020-2025） | 中期（2026-03） | 最近（2026-05） |
|------|-------------------|-----------------|-----------------|
| **提交信息** | 中文自由描述 | `feat: 中文描述` | `feat(scope): 中文描述`（Conventional Commits） |
| **接口风格** | RESTful + 若依风格 | 混合 | 统一 POST + JSON Body |
| **文档结构** | 分散 | 初步集中 | `docs/design/` + `docs/archive/` 分层 |
| **依赖管理** | 父 POM 继承 | 父 POM + BOM | 统一 BOM + 禁用清单 |

### 2. 从分散到内聚

| 维度 | 重构前 | 重构后 | 减少量 |
|------|--------|--------|--------|
| **权限查询路径** | 5 条独立链路 | 1 个 `PermQueryEngine` | 统一入口 |
| **ConfigManageService** | 12 个依赖的"上帝类" | 拆分为 6 个 AppService | ~500 行 |
| **PermissionServiceImpl** | 1923 行 | ~841 行 | -56% |
| **DomainService 数量** | 17 个分散 | 12 个内聚 | 合并 5 个 |

### 3. 从冗余到精简

| 操作类型 | 典型提交 | 判断标准 |
|----------|----------|----------|
| **删除过时文档** | `chore: 删除 docs/product 目录和 product-specification.md` | 功能已实现或设计已变更 |
| **删除前端项目** | `feat: 删除前端项目`（后重新添加） | 需重新设计时删除旧实现 |
| **删除不规范文件** | `chore: 删除不规范的PRP计划文件并整理文档结构` | 不符合命名/组织规范 |
| **归档过渡文档** | `docs: 归档过期设计文档和已完成PRPs计划` | 阶段性任务完成 |

---

## 三、决策模式总结

### 1. 重构触发条件

| 触发信号 | 典型场景 | 重构动作 |
|----------|----------|----------|
| **代码量超标** | 单类超过 1500 行 | 拆分为多个专注类 |
| **依赖过多** | 构造函数注入超过 8 个 | 职责拆分 |
| **重复代码** | 同一 SQL/逻辑出现在多处 | 抽取到 DomainService/Mapper |
| **N+1 查询** | 循环内单条 DB 查询 | 批量方法 + 缓存 |
| **多路径并存** | 5 条权限查询链路 | 统一 Engine |

### 2. 删除判断标准

| 类型 | 删除标准 | 归档替代 |
|------|----------|----------|
| **已实现的设计文档** | 功能代码已稳定 | 归档设计过程 |
| **过时的字段/类** | 新架构已替代 | 记录迁移路径 |
| **不规范的计划文件** | 命名/格式不符合规范 | 无需归档 |
| **临时前端实现** | 需重新设计 | 可保留后重新添加 |

### 3. 归档时机判断

| 归档批次 | 归档内容 | 归档原因 |
|----------|----------|----------|
| `2026-04-28` | 长文档、讨论清单 | 文档重整前旧版 |
| `2026-05-24` | PermQueryEngine 设计过程 | 重构完成，设计已合并 |
| `2026-05-30` | Service 层重构审查 | Phase 1-5 完成，结果已合并 |
| `2026-06-03` | Round 1-7 诊断记录 | 模块核对完成，问题已修复 |

---

## 四、对AI代码的调整重点

### 1. 拒绝过度设计

| AI 常见倾向 | 用户调整方向 | 证据 |
|-------------|--------------|------|
| 过度抽象 | 保持扁平，必要时才分层 | DomainService 合并而非继续拆分 |
| 多层继承 | 优先接口 + 单实现 | 无复杂继承链 |
| 过多策略类 | 统一入口替代 | 删除 `ResourcePermissionStrategy` 系列 |
| 过度泛型 | 具体类型优先 | `PermQuery` 使用具体工厂方法而非泛型构造 |

### 2. 统一抽象层次

| 方面 | 统一要求 |
|------|----------|
| **权限查询** | 所有判定走 `PermQueryEngine` 或 `engine.hasPermission()` |
| **缓存操作** | 所有业务缓存走 `CacheService` |
| **类型解析** | 批量方法 `batchResolveTypeValues` 而非循环单条 |
| **角色解析** | `SubjectDomainService.resolveEffectiveRoles()` 带缓存 |
| **批量加载** | Mapper 批量方法而非私有 `load*()` |

### 3. 中文优先策略

| 场景 | 中文使用 |
|------|----------|
| **提交描述** | `feat(permission-center): 新增权限中心模块` |
| **文档标题** | `# 项目开发规范` |
| **代码注释** | 功能说明、TODO 标记使用中文 |
| **异常消息** | 面向用户的错误提示使用中文 |
| **日志消息** | 关键业务节点日志使用中文 |

### 4. 测试驱动验证

| 验证点 | 要求 |
|--------|------|
| **提交前验证** | `pnpm build && pnpm typecheck && pnpm lint` |
| **覆盖率目标** | 业务逻辑 80%+ |
| **边界测试** | 空值、空集合、重复值、状态冲突 |
| **Mock 规范** | Mock 外部依赖，不 Mock 被测单元 |

---

## 五、独特风格标识

### 1. "归档而非删除"的文档治理

区别于常见直接删除过时文档的做法，用户建立了完善的归档机制：
- 每批次归档有独立 README 说明归档原因
- 归档文档保留历史追溯价值
- 权威文档与归档文档明确分离

### 2. "Phase 分阶段重构"的工程化演进

重构不是一次性大改动，而是分 Phase 精细推进：
- Phase 1-7 有明确的阶段目标
- 每阶段有独立提交和审查修复闭环
- 阶段完成后归档过程文档

### 3. "显式 Cache Aside"的缓存哲学

拒绝 Spring Cache 注解的隐式缓存，坚持显式模式：
- `get` → miss → 业务加载 → `put` → `evictAfterCommit`
- 缓存失效绑定事务提交后执行
- 集中在 `CacheService` 统一入口

### 4. "统一 POST 接口"的务实选择

拒绝 RESTful 路径参数，统一 POST + JSON Body：
- 规避 URL 长度限制
- 统一鉴权拦截逻辑
- 与权限中心内部规范一致

### 5. "异常三层分类"的语义边界

明确区分 BizException / SystemException / SecurityException：
- 不为"统一"而混淆安全拒绝与业务拒绝
- 编码规范明文禁止混用

---

## 六、编码建议（给AI）

### 生成代码时必查清单

| 检查点 | 查询位置 |
|--------|----------|
| **是否有可复用 DomainService 方法？** | `.claude/rules/permission-center-coding-standards.md` §6 |
| **是否已触发缓存 Skills？** | `.claude/skills/dual-layer-cache-framework/SKILL.md` |
| **命名是否符合规范？** | `.claude/rules/permission-center-coding-standards.md` §3 |
| **是否有 N+1 查询风险？** | `docs/design/project-rules.md` §8.4.8 |
| **异常类型是否正确？** | `.claude/rules/permission-center-coding-standards.md` §2（异常边界） |
| **是否使用已删除的类？** | `.claude/rules/permission-center-coding-standards.md` §17 |

### 文档生成时必遵规范

| 要求 | 说明 |
|------|------|
| **中文优先** | 所有面向团队的文档使用中文标题和描述 |
| **表格化状态** | 状态清单、规范约束优先用表格呈现 |
| **指向权威** | 引用设计时指向 `docs/design/` 而非归档 |
| **归档时机** | 阶段性完成后归档过程文档到 `docs/archive/YYYY-MM-DD/` |

### Git 提交格式要求

```
<type>(<scope>): <中文描述>

[可选 body - 中文说明]

[可选 footer]
```

**type 枚举**：`feat / fix / refactor / perf / docs / chore / ci`

---

## 附录：关键提交节点

| 时间节点 | 关键提交 | 风格转变 |
|----------|----------|----------|
| 2026-03-07 | `feat(permission-center): 新增权限中心模块` | 从若依框架转向独立权限中心 |
| 2026-04-18 | `feat: 删除 Phase 0 相关文档` | 精简过时设计 |
| 2026-04-25 | `feat: 创建项目骨架并将项目重命名为 AccessMesh` | 项目品牌化 |
| 2026-05-22 | `refactor(permission-center): 删除EntityBatchLoadDomainService` | 批量查询下沉到 Mapper |
| 2026-05-24 | `refactor(permission-center): 重命名+AOP日志统一+新编码规范(Phase 5)` | 命名规范统一 |
| 2026-05-30 | `feat: 删除前端项目`（后重新添加） | 需重新设计时删除旧实现 |
| 2026-06-01 | `chore: 删除 docs/product 目录和 product-specification.md` | 精简冗余文档 |

---

**报告生成时间**：2026-06-05

**分析依据**：
- Git 历史 3804 次提交
- `docs/design/project-rules.md` 编码规范
- `.claude/rules/*.md` 项目规则
- `docs/archive/` 归档文档结构
- `service-layer-review.md` 重构审查总结