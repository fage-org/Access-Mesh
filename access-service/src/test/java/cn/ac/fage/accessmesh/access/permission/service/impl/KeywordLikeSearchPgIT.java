package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.admin.mapper.SysOrgMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysUserMapper;
import cn.ac.fage.accessmesh.access.application.query.mapper.UserRoleQueryMapper;
import cn.ac.fage.accessmesh.access.infrastructure.mapper.SystemConfigMapper;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.TypeDefinitionMapper;
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
 * T-FE-022 联调暴露的系统性缺陷回归锁：keyword LIKE CONCAT 参数在 stringtype=unspecified
 * （UTC TypeHandler 所需的 JDBC URL 参数）下，PostgreSQL 无法推断 CONCAT(variadic "any")
 * 中未定型参数的类型，全部关键字搜索路径一律 500（旧实现下本测试每个用例必红）。
 * <p>
 * 修法 = LIKE CONCAT 内参数显式 CAST(... AS VARCHAR)（九个 mapper 同族一并修复，
 * 锚点注释见 TypeDefinitionMapper listCondition）。本测试在真实 PostgreSQL + 真实
 * MyBatis 参数绑定下逐 mapper 锁定关键字路径可用且过滤语义正确。
 * </p>
 * <p>
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
class KeywordLikeSearchPgIT {

    private static final Long TENANT = 1L;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, KeywordLikeSearchPgIT.class);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TypeDefinitionMapper typeDefinitionMapper;
    @Autowired
    private SystemConfigMapper systemConfigMapper;
    @Autowired
    private BizDomainMapper bizDomainMapper;
    @Autowired
    private AbstractRoleMapper abstractRoleMapper;
    @Autowired
    private AbstractUserMapper abstractUserMapper;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private SysOrgMapper sysOrgMapper;
    @Autowired
    private UserRoleQueryMapper userRoleQueryMapper;

    @Test
    @DisplayName("type-definition keyword：DDL 种子内 LIKE type_code 命中（无夹具）")
    void shouldSearchTypeDefinitionByKeyword() {
        List<cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition> rows =
            typeDefinitionMapper.selectPageByCondition(TENANT, null, "LOCAL", 10, 0);
        assertThat(rows).extracting(cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition::getTypeCode)
            .contains("LOCAL_USER");
    }

    @Test
    @DisplayName("system-config keyword：config_key/description LIKE 命中")
    void shouldSearchSystemConfigByKeyword() {
        jdbc.update("INSERT INTO system_config (tenant_id, config_key, config_value, description) "
            + "VALUES (1, 'kwtest.cfg', '{}', 'keyword 回归夹具')");
        assertThat(systemConfigMapper.selectPageByCondition(TENANT, "kwtest", 10, 0))
            .extracting(cn.ac.fage.accessmesh.access.infrastructure.entity.SystemConfig::getConfigKey)
            .contains("kwtest.cfg");
    }

    @Test
    @DisplayName("biz-domain keyword：code/name/description LIKE 命中")
    void shouldSearchBizDomainByKeyword() {
        jdbc.update("INSERT INTO biz_domain (tenant_id, code, name) VALUES (1, 'KWTEST_DOMAIN', 'keyword 回归域')");
        assertThat(bizDomainMapper.selectPageByCondition(TENANT, "KWTEST", 10, 0))
            .extracting(cn.ac.fage.accessmesh.access.permission.entity.BizDomain::getCode)
            .contains("KWTEST_DOMAIN");
    }

    @Test
    @DisplayName("abstract-role / user-role-query keyword：name/external_id LIKE 命中")
    void shouldSearchAbstractRoleByKeyword() {
        jdbc.update("INSERT INTO abstract_role (tenant_id, role_type, external_id, name) "
            + "VALUES (1, 6, 'kwtest-role', 'keyword 回归角色')");
        assertThat(abstractRoleMapper.selectRoleListPaged(TENANT, null, "kwtest-role", false, 0, 10))
            .extracting(cn.ac.fage.accessmesh.access.permission.entity.AbstractRole::getExternalId)
            .contains("kwtest-role");
        assertThat(userRoleQueryMapper.selectFunctionalRoles(TENANT, null, "kwtest-role", 0, 10))
            .anySatisfy(p -> assertThat(p.externalId()).isEqualTo("kwtest-role"));
    }

    @Test
    @DisplayName("abstract-user keyword：name/external_id LIKE 命中")
    void shouldSearchAbstractUserByKeyword() {
        jdbc.update("INSERT INTO abstract_user (tenant_id, user_type, external_id, name) "
            + "VALUES (1, 3, 'kwtest-user', 'keyword 回归用户')");
        assertThat(abstractUserMapper.selectUserListPaged(TENANT, null, "kwtest-user", false, 0, 10))
            .extracting(cn.ac.fage.accessmesh.access.permission.entity.AbstractUser::getExternalId)
            .contains("kwtest-user");
    }

    @Test
    @DisplayName("sys-user keyword：username/name LIKE 命中")
    void shouldSearchSysUserByKeyword() {
        jdbc.update("INSERT INTO sys_user (id, tenant_id, username, password, name) "
            + "VALUES (990001, 1, 'kwtest_user', 'x', 'keyword 回归系统用户')");
        assertThat(sysUserMapper.selectUsersByCondition(TENANT, "kwtest_user", null, null, null, null, null, 0, 10))
            .extracting(cn.ac.fage.accessmesh.access.admin.entity.SysUser::getUsername)
            .contains("kwtest_user");
        assertThat(sysUserMapper.selectUsersByIdsAndKeyword(TENANT, List.of(990001L), "回归系统用户", 0, 10))
            .extracting(cn.ac.fage.accessmesh.access.admin.entity.SysUser::getUsername)
            .contains("kwtest_user");
    }

    @Test
    @DisplayName("sys-org keyword：org name LIKE 命中")
    void shouldSearchSysOrgByKeyword() {
        jdbc.update("INSERT INTO sys_org (tenant_id, code, name) VALUES (1, 'KWTEST_ORG', 'keyword 回归组织')");
        assertThat(sysOrgMapper.selectOrgsByCondition(TENANT, "keyword 回归组织", null, null, null, 0, 10))
            .extracting(cn.ac.fage.accessmesh.access.admin.entity.SysOrg::getName)
            .contains("keyword 回归组织");
    }
}
