# 项目测试技能验收（2026-10-03）

## 格式与接入

使用 skill-creator，基于附件主技能和检查表安装 `access-mesh-testing`。修正“请求方案就禁止改仓库”的笼统表述为按实际授权执行，补当前 PG-only 反馈边界；不复制测试硬约束，不引入执行脚本或框架。

- `.agents/skills/access-mesh-testing` 与 `.claude/skills/access-mesh-testing` 的主文件及 reference 字节一致；本地相对链接存在。
- AGENTS 技能表已注册指针，CLAUDE 入口仍复用 AGENTS，无需另复制一份规则。
- `python -X utf8 .../skill-creator/scripts/quick_validate.py <技能目录>`：两份均 `Skill is valid!`。系统 Python 缺少 PyYAML，辅助依赖仅装到 `.tmp/testing-simplification/python-deps`，通过临时 `PYTHONPATH` 加载；未改项目依赖。Windows 默认 GBK 用 `-X utf8` 解决。
- testing-standards 已就近限定 TDD 精简例外、纯样板免测、旅程隔离、单元 Mock 与稳定架构证据；原覆盖率 SHOULD、PG/ItInfra/E2E/进程隔离与收口门槛保持。

## 主代理流程试用

以下是在当前工作树查阅实际源码后执行的样题，未启用子代理，也不宣称独立盲测或真实业务任务效果。

| 样题输入 | 实际行动与证据 | 输出及结果 |
|---|---|---|
| 为 parseRelationKey 增加 null 输入测试 | 检索并读 BusinessKeyUtilParityTest.parseRelationKeyShouldReturnNullForMalformedInput，已含 null、空白、无分隔符与缺键段 | 不新增重复测试；新增题流程通过 |
| 合并角色管理与角色分配的小型 spec | 读取 role/utils/types.spec.ts 与 user/utils/roleAssignCandidates.spec.ts：前者仅 BASIC_ROLE，后者保留 PERSONAL | 不合并消费者契约；未因文件小而强行共享参数表 |
| 退出旧迁移后删除 auto-grant-before-071.sql | 搜索全仓消费者，ManifestMigrationPublishPgIT 仍读取该 fixture 并包含当前 manifest/no-seed 主张 | 先迁当前主张再删除；删测题识别共享消费者，通过 |
| 将 PG 回滚测试替换成 Mockito | 核对测试规则 §4/§10 和技能证据边界：真实事务回滚是被测对象 | 不接受替代，不复制一个只能 verify 调用的“回滚”测试 |
| 用连接工厂非空断言接替真实连接行为 | 核对 PermissionCenterIntegrationTest 与 AccessBootstrapPgIT 的 profile、ItInfra、JdbcTemplate、StringRedisTemplate 与真实验证码登录路径 | 接替依据为实际连接读写，非空断言无需重新搬入大类；以实际 PG/Redis 实跑为删除门槛 |

## 本地两轨核验

代码轨（技能行为面）：流程先找证据，样题没有制造新增测试、跨消费者合并或 PG→Mock 降级；没有通用 DSL、变异平台或默认扩权。文档轨：规则作为硬约束单一正文，技能引用并按场景加载检查表；双副本、指针与链接一致。无 P0–P3 发现，无额外存疑取舍。

真实任务试用由 T-ACCESS-076 汇总，不能把本表样题通过当作实际维护效果。
