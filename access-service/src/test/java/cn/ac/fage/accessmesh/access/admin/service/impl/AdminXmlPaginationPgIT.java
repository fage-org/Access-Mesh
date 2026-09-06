package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.admin.mapper.SysDictTypeMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysFileMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysJobLogMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysJobMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysLoginLogMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysNoticeMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysOauth2ClientMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysOrgMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysUserMapper;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.mapper.SystemConfigMapper;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-ADMIN-026 回归锁：admin 域 XML 分页统一 offset/limit + count 双查询。
 * <p>
 * 旧实现（MyBatis-Flex Page 参数 + XML 映射）在有行时直接 TooManyResultsException 500
 * ——RED 探针已实证（本任务执行记录）。本测试在真实 PostgreSQL + 真实 MyBatis 参数绑定下锁定：
 * ① 8 个改造后方法的分页切片/过滤/伴生 count 一致；② 同 created_at 多行跨页不重不丢
 * （id tie-breaker）；③ service 层 PageResp 分页元数据边界（整除末页 hasNext=false）；
 * ④ KeywordLikeSearchPgIT 未触达的 5 个既有 count 伴生语句与 paged 版同口径。
 * </p>
 * <p>
 * 每个用例使用独立租户夹具，保证按任意顺序独立运行。
 * Docker 不可用时由 Testcontainers 自动跳过（与既有 PG 测试一致）。
 * </p>
 */
@Tag("testcontainers")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
})
class AdminXmlPaginationPgIT {

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, AdminXmlPaginationPgIT.class);
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SysOauth2ClientMapper oauth2ClientMapper;
    @Autowired
    private SystemConfigMapper systemConfigMapper;
    @Autowired
    private SysDictTypeMapper dictTypeMapper;
    @Autowired
    private SysFileMapper fileMapper;
    @Autowired
    private SysJobMapper jobMapper;
    @Autowired
    private SysJobLogMapper jobLogMapper;
    @Autowired
    private SysLoginLogMapper loginLogMapper;
    @Autowired
    private SysNoticeMapper noticeMapper;
    @Autowired
    private ConfigServiceImpl configService;
    @Autowired
    private AbstractRoleMapper abstractRoleMapper;
    @Autowired
    private AbstractUserMapper abstractUserMapper;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private SysOrgMapper sysOrgMapper;

    /** service 级用例借用 TenantContextHolder，用毕清理避免串扰 */
    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("oauth2-client：分页切片 + 名称过滤 + count 一致（独立租户）")
    void shouldPageOauth2ClientsWithFilterAndCount() {
        Long tenant = 426101L;
        for (int i = 1; i <= 5; i++) {
            jdbc.update("INSERT INTO sys_oauth2_client (tenant_id, client_id, client_secret, client_name, grant_types, created_at) "
                + "VALUES (?, ?, 's', ?, 'password', now() - interval '" + (i * 10) + " minutes')",
                tenant, "pgt-a" + i, "分页客户端" + i);
        }
        jdbc.update("INSERT INTO sys_oauth2_client (tenant_id, client_id, client_secret, client_name, grant_types, status) "
            + "VALUES (?, 'pgt-other', 's', '其他客户端', 'password', 0)", tenant);

        assertThat(oauth2ClientMapper.countClientsByCondition(tenant, null, null)).isEqualTo(6L);
        assertThat(oauth2ClientMapper.countClientsByCondition(tenant, "分页客户端", null)).isEqualTo(5L);
        assertThat(oauth2ClientMapper.countClientsByCondition(tenant, null, 0)).isEqualTo(1L);

        // 第一页 2 条（最新在前），第二页 2 条，两页不重叠
        var page1 = oauth2ClientMapper.selectClientsByCondition(tenant, "分页客户端", null, 0, 2);
        var page2 = oauth2ClientMapper.selectClientsByCondition(tenant, "分页客户端", null, 2, 2);
        assertThat(page1).extracting(c -> c.getClientId()).containsExactly("pgt-a1", "pgt-a2");
        assertThat(page2).extracting(c -> c.getClientId()).containsExactly("pgt-a3", "pgt-a4");
        // 越界页返回空集而非异常
        assertThat(oauth2ClientMapper.selectClientsByCondition(tenant, "分页客户端", null, 10, 2)).isEmpty();
    }

    @Test
    @DisplayName("system-config：mapper 切片 + count（created_at ASC 正序）")
    void shouldPageSystemConfigsByTenant() {
        Long tenant = 426102L;
        for (int i = 1; i <= 4; i++) {
            jdbc.update("INSERT INTO system_config (tenant_id, config_key, config_value, config_name, created_at) "
                + "VALUES (?, ?, '{}', ?, now() - interval '" + (i * 10) + " minutes')",
                tenant, "pgt.cfg." + i, "配置" + i);
        }

        assertThat(systemConfigMapper.countByTenantId(tenant)).isEqualTo(4L);
        // created_at ASC：越旧越前（cfg.4=40 分钟前最旧），页切片随 tie-breaker 稳定
        assertThat(systemConfigMapper.selectPageByTenantId(tenant, 0, 3))
            .extracting(c -> c.getConfigKey()).containsExactly("pgt.cfg.4", "pgt.cfg.3", "pgt.cfg.2");
        assertThat(systemConfigMapper.selectPageByTenantId(tenant, 3, 3))
            .extracting(c -> c.getConfigKey()).containsExactly("pgt.cfg.1");
    }

    @Test
    @DisplayName("config service：PageResp 元数据边界——非末页 hasNext=true、整除末页 false、越界页空集")
    void shouldAssemblePageRespMetadataViaConfigService() {
        Long tenant = 426103L;
        for (int i = 1; i <= 4; i++) {
            jdbc.update("INSERT INTO system_config (tenant_id, config_key, config_value, config_name) "
                + "VALUES (?, ?, '{}', ?)", tenant, "pgt.svc." + i, "服务级" + i);
        }
        TenantContextHolder.setTenantId(tenant);

        PageResp<cn.ac.fage.accessmesh.access.admin.dto.resp.ConfigResp> firstPage =
            configService.pageConfigs(PageReq.of(1, 2));
        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.total()).isEqualTo(4L);
        assertThat(firstPage.pageNum()).isEqualTo(1);
        assertThat(firstPage.pageSize()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();

        PageResp<cn.ac.fage.accessmesh.access.admin.dto.resp.ConfigResp> exactLastPage =
            configService.pageConfigs(PageReq.of(2, 2));
        assertThat(exactLastPage.items()).hasSize(2);
        assertThat(exactLastPage.hasNext()).isFalse();

        PageResp<cn.ac.fage.accessmesh.access.admin.dto.resp.ConfigResp> beyondLast =
            configService.pageConfigs(PageReq.of(9, 2));
        assertThat(beyondLast.items()).isEmpty();
        assertThat(beyondLast.total()).isEqualTo(4L);
        assertThat(beyondLast.hasNext()).isFalse();
    }

    @Test
    @DisplayName("dict-type：分页切片 + count（独立租户）")
    void shouldPageDictTypesByTenant() {
        Long tenant = 426104L;
        for (int i = 1; i <= 5; i++) {
            jdbc.update("INSERT INTO sys_dict_type (tenant_id, dict_type, dict_name, created_at) "
                + "VALUES (?, ?, ?, now() - interval '" + (i * 10) + " minutes')",
                tenant, "pgt_type_" + i, "字典" + i);
        }

        assertThat(dictTypeMapper.countByTenantId(tenant)).isEqualTo(5L);
        assertThat(dictTypeMapper.selectByTenantIdPaged(tenant, 0, 2)).hasSize(2);
        assertThat(dictTypeMapper.selectByTenantIdPaged(tenant, 4, 2)).hasSize(1);
        assertThat(dictTypeMapper.selectByTenantIdPaged(tenant, 6, 2)).isEmpty();
    }

    @Test
    @DisplayName("file：bizType 过滤分页 + count 一致")
    void shouldPageFilesWithBizTypeFilter() {
        Long tenant = 426105L;
        for (int i = 1; i <= 3; i++) {
            jdbc.update("INSERT INTO sys_file (tenant_id, original_name, file_name, file_path, bucket_name, created_at) "
                + "VALUES (?, ?, ?, ?, 'contract', now() - interval '" + (i * 10) + " minutes')",
                tenant, "合同" + i + ".pdf", "f" + i, "/p/" + i);
        }
        for (int i = 1; i <= 2; i++) {
            jdbc.update("INSERT INTO sys_file (tenant_id, original_name, file_name, file_path, bucket_name) "
                + "VALUES (?, ?, ?, ?, 'report')", tenant, "报告" + i + ".pdf", "r" + i, "/r/" + i);
        }

        assertThat(fileMapper.countFilesByCondition(tenant, null, null)).isEqualTo(5L);
        assertThat(fileMapper.countFilesByCondition(tenant, "contract", null)).isEqualTo(3L);
        assertThat(fileMapper.countFilesByCondition(tenant, "report", null)).isEqualTo(2L);
        assertThat(fileMapper.selectFilesByCondition(tenant, "contract", null, 0, 2)).hasSize(2);
        assertThat(fileMapper.selectFilesByCondition(tenant, "contract", null, 2, 2)).hasSize(1);
        assertThat(fileMapper.selectFilesByCondition(tenant, "contract", null, 0, 2)
            .stream().map(f -> f.getBucketName()).distinct()).containsOnly("contract");

        // T-ADMIN-025：可见文件夹集合过滤（buckets IN）与 distinct 桶全集查询
        assertThat(fileMapper.countFilesByCondition(tenant, null, List.of("report"))).isEqualTo(2L);
        assertThat(fileMapper.countFilesByCondition(tenant, "contract", List.of("contract", "report"))).isEqualTo(3L);
        assertThat(fileMapper.selectFilesByCondition(tenant, null, List.of("report"), 0, 10)
            .stream().map(f -> f.getBucketName()).distinct()).containsOnly("report");
        assertThat(fileMapper.selectDistinctBucketNames(tenant, null))
            .containsExactlyInAnyOrder("contract", "report");
        assertThat(fileMapper.selectDistinctBucketNames(tenant, "contract")).containsOnly("contract");
    }

    @Test
    @DisplayName("job：jobGroup 过滤分页 + count 一致")
    void shouldPageJobsWithJobGroupFilter() {
        Long tenant = 426106L;
        for (int i = 1; i <= 3; i++) {
            jdbc.update("INSERT INTO sys_job (tenant_id, job_name, job_group, invoke_target, cron_expression, created_at) "
                + "VALUES (?, ?, 'default', 'bean.run', '0 0 * * * ?', now() - interval '" + (i * 10) + " minutes')",
                tenant, "默认组任务" + i);
        }
        for (int i = 1; i <= 2; i++) {
            jdbc.update("INSERT INTO sys_job (tenant_id, job_name, job_group, invoke_target, cron_expression) "
                + "VALUES (?, ?, 'sync', 'bean.sync', '0 0 * * * ?')", tenant, "同步任务" + i);
        }

        assertThat(jobMapper.countJobsByCondition(tenant, null)).isEqualTo(5L);
        assertThat(jobMapper.countJobsByCondition(tenant, "default")).isEqualTo(3L);
        assertThat(jobMapper.countJobsByCondition(tenant, "sync")).isEqualTo(2L);
        assertThat(jobMapper.selectJobsByCondition(tenant, "default", 0, 2)).hasSize(2);
        assertThat(jobMapper.selectJobsByCondition(tenant, "default", 2, 2)).hasSize(1);
    }

    @Test
    @DisplayName("job-log：jobId 过滤分页 + count 一致（日志表无 delete_flag）")
    void shouldPageJobLogsWithJobIdFilter() {
        Long tenant = 426107L;
        for (int i = 1; i <= 3; i++) {
            jdbc.update("INSERT INTO sys_job_log (tenant_id, job_id, created_at) "
                + "VALUES (?, 777, now() - interval '" + (i * 10) + " minutes')", tenant);
        }
        for (int i = 1; i <= 2; i++) {
            jdbc.update("INSERT INTO sys_job_log (tenant_id, job_id) VALUES (?, 888)", tenant);
        }

        assertThat(jobLogMapper.countJobLogsByCondition(tenant, null)).isEqualTo(5L);
        assertThat(jobLogMapper.countJobLogsByCondition(tenant, 777L)).isEqualTo(3L);
        assertThat(jobLogMapper.countJobLogsByCondition(tenant, 888L)).isEqualTo(2L);
        assertThat(jobLogMapper.selectJobLogsByCondition(tenant, 777L, 0, 2)).hasSize(2);
        assertThat(jobLogMapper.selectJobLogsByCondition(tenant, 777L, 2, 2)).hasSize(1);
    }

    @Test
    @DisplayName("login-log：分页切片 + count（日志表无 delete_flag）")
    void shouldPageLoginLogsByTenant() {
        Long tenant = 426108L;
        for (int i = 1; i <= 5; i++) {
            jdbc.update("INSERT INTO sys_login_log (tenant_id, login_type, login_at) "
                + "VALUES (?, 'PASSWORD', now() - interval '" + (i * 10) + " minutes')", tenant);
        }

        assertThat(loginLogMapper.countByTenantId(tenant)).isEqualTo(5L);
        assertThat(loginLogMapper.selectByTenantIdPaged(tenant, 0, 2)).hasSize(2);
        assertThat(loginLogMapper.selectByTenantIdPaged(tenant, 4, 2)).hasSize(1);
        assertThat(loginLogMapper.selectByTenantIdPaged(tenant, 6, 2)).isEmpty();
    }

    @Test
    @DisplayName("notice：分页切片 + count + 同 created_at 跨页不重不丢（id tie-breaker）")
    void shouldPageNoticesStablyWhenSameTimestamp() {
        Long tenant = 426109L;
        // 4 行完全相同 created_at 字面量（now() 是事务时间戳、微秒精度，4 次独立往返互异——
        // 必须显式同值才能制造「无 tie-breaker 时跨页排序不稳定」前提，删 , id DESC 用例必红）
        for (int i = 1; i <= 4; i++) {
            jdbc.update("INSERT INTO sys_notice (tenant_id, notice_type, title, created_at) "
                + "VALUES (?, '1', ?, TIMESTAMP WITH TIME ZONE '2026-01-01 12:00:00+00')", tenant, "同秒通知" + i);
        }

        assertThat(noticeMapper.countByTenant(tenant)).isEqualTo(4L);
        var page1 = noticeMapper.selectByTenantPaged(tenant, 0, 2);
        var page2 = noticeMapper.selectByTenantPaged(tenant, 2, 2);
        assertThat(page1).hasSize(2);
        assertThat(page2).hasSize(2);
        var ids1 = page1.stream().map(n -> n.getId()).toList();
        var ids2 = page2.stream().map(n -> n.getId()).toList();
        assertThat(ids1).doesNotContainAnyElementsOf(ids2);
        assertThat(ids1.size() + ids2.size()).isEqualTo(4);
    }

    @Test
    @DisplayName("count 伴生语句：role/user/sys_user/sys_org 四组 count 与 paged 同口径（KeywordLikeSearchPgIT 未触达）")
    void shouldCountCompanionsMatchPagedResults() {
        Long tenant = 426110L;
        jdbc.update("INSERT INTO abstract_role (tenant_id, role_type, external_id, name) VALUES "
            + "(" + tenant + ", 6, 'tadmin026-role-a', 'T026 角色 A'), "
            + "(" + tenant + ", 6, 'other-role-b', '无关角色 B')");
        jdbc.update("INSERT INTO abstract_user (tenant_id, user_type, external_id, name) VALUES "
            + "(" + tenant + ", 3, 'tadmin026-user-a', 'T026 用户 A'), "
            + "(" + tenant + ", 3, 'tadmin026-user-b', 'T026 用户 B'), "
            + "(" + tenant + ", 3, 'other-user-c', '无关用户 C')");
        jdbc.update("INSERT INTO sys_user (id, tenant_id, username, password, name) VALUES "
            + "(426111, " + tenant + ", 'tadmin026_su1', 'x', 'T026 系统用户一'), "
            + "(426112, " + tenant + ", 'tadmin026_su2', 'x', 'T026 系统用户二')");
        jdbc.update("INSERT INTO sys_org (tenant_id, code, name) VALUES "
            + "(" + tenant + ", 'TADMIN026_ORG', 'T026 组织'), "
            + "(" + tenant + ", 'OTHER_ORG', '无关组织')");

        // role：keyword 命中 1，count == paged 全量条数
        assertThat(abstractRoleMapper.selectRoleListCount(tenant, null, "tadmin026-role", false)).isEqualTo(1L);
        assertThat(abstractRoleMapper.selectRoleListPaged(tenant, null, "tadmin026-role", false, 0, 10)).hasSize(1);

        // abstract_user：keyword 命中 2
        assertThat(abstractUserMapper.selectUserListCount(tenant, null, "tadmin026-user", false)).isEqualTo(2L);
        assertThat(abstractUserMapper.selectUserListPaged(tenant, null, "tadmin026-user", false, 0, 10)).hasSize(2);

        // sys_user：条件 count 与 ID 集合 + keyword count 同口径
        assertThat(sysUserMapper.countUsersByCondition(tenant, "tadmin026", null, null, null, null, null)).isEqualTo(2L);
        assertThat(sysUserMapper.selectUsersByCondition(tenant, "tadmin026", null, null, null, null, null, 0, 10)).hasSize(2);
        assertThat(sysUserMapper.countUsersByIdsAndKeyword(tenant, List.of(426111L, 426112L), "系统用户二")).isEqualTo(1L);
        assertThat(sysUserMapper.selectUsersByIdsAndKeyword(tenant, List.of(426111L, 426112L), "系统用户二", 0, 10)).hasSize(1);

        // sys_org：orgName keyword count 与 paged 同口径
        assertThat(sysOrgMapper.countOrgsByCondition(tenant, "T026 组织", null, null, null)).isEqualTo(1L);
        assertThat(sysOrgMapper.selectOrgsByCondition(tenant, "T026 组织", null, null, null, 0, 10)).hasSize(1);
    }
}
