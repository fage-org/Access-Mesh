package cn.ac.fage.accessmesh.access.architecture;

import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.menu.mapper.UserMenuQueryMapper;
import cn.ac.fage.accessmesh.access.org.mapper.OrgVisibilityQueryMapper;
import cn.ac.fage.accessmesh.access.role.mapper.UserRoleQueryMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** 真实 QueryMapper XML：租户、有效期、LEFT JOIN 与批量空集合；每个场景独立租户。 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("testcontainers")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "accessmesh.sync.scheduler.enabled=false", "access.tenant.gate-repair.enabled=false"
})
class QueryMapperPgIT {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) { ItInfra.register(registry, QueryMapperPgIT.class); }

    @Autowired JdbcTemplate jdbc;
    @Autowired UserRoleQueryMapper roles;
    @Autowired UserMenuQueryMapper menus;
    @Autowired OrgVisibilityQueryMapper orgs;

    @Test
    void roleWindowIncludesExactBoundsAndNull_butExcludesForeignDeletedAndOutsideRows() {
        jdbc.execute("""
            INSERT INTO abstract_role(id,tenant_id,role_type,external_id,name)
            SELECT n,741,6,'role-'||n,'role-'||n FROM generate_series(74101,74106) n;
            INSERT INTO user_role(tenant_id,abstract_user_id,target_type,target_id,valid_from,valid_to,delete_flag) VALUES
            (741,10,'ROLE',74101,'2026-01-01 12:00:00+00','2026-01-01 12:00:00+00',0),
            (741,10,'ROLE',74102,NULL,NULL,0),
            (741,10,'ROLE',74103,'2026-01-01 12:00:01+00',NULL,0),
            (741,10,'ROLE',74104,NULL,'2026-01-01 11:59:59+00',0),
            (749,10,'ROLE',74105,NULL,NULL,0),
            (741,10,'ROLE',74106,NULL,NULL,1);
            """);
        assertThat(roles.selectUserRoleProjections(741L, 10L, LocalDateTime.of(2026, 1, 1, 12, 0)))
            .extracting(row -> row.roleExternalId()).containsExactlyInAnyOrder("role-74101", "role-74102");
    }

    @Test
    void leftJoinsPreserveRelations_whenTargetsAreMissingForeignOrDeleted() {
        jdbc.execute("""
            INSERT INTO abstract_role(id,tenant_id,role_type,external_id,name,status,delete_flag) VALUES
            (74201,742,6,'own','own',0,0),(74202,749,6,'foreign','foreign',1,0),(74203,742,6,'deleted','deleted',1,1),
            (74211,742,1,'own-relation','own-relation',0,0),(74212,749,1,'foreign-relation','foreign-relation',1,0),
            (74213,742,1,'deleted-relation','deleted-relation',1,1);
            INSERT INTO user_role(tenant_id,abstract_user_id,target_type,target_id,relation_id) VALUES
            (742,10,'ROLE',74201,74211),(742,10,'ROLE',74202,74212),
            (742,10,'ROLE',74203,74213),(742,10,'ROLE',74204,74214);
            """);
        assertThat(roles.selectUserRoleProjections(742L, 10L, LocalDateTime.of(2026, 1, 1, 12, 0)))
            .extracting(row -> row.relationId(), row -> row.roleExternalId(), row -> row.relationExternalId())
            .containsExactlyInAnyOrder(tuple(74211L, "own", "own-relation"), tuple(74212L, null, null),
                tuple(74213L, null, null), tuple(74214L, null, null));
    }

    @Test
    void batchCollectionsAndOrganizationQueriesRespectTenantAndDeletion() {
        jdbc.execute("""
            INSERT INTO sys_org(id,tenant_id,parent_id,code,name,delete_flag) VALUES
            (74301,743,0,'root','root',0),(74302,743,74301,'child','child',0),
            (74303,749,74301,'foreign','foreign',0),(74304,743,74301,'deleted','deleted',1);
            INSERT INTO sys_org_tree_config(tenant_id,root_org_id,tree_name,is_default,delete_flag) VALUES
            (743,74301,'default',true,0),(749,74303,'foreign',true,0),(743,74304,'deleted',true,1);
            INSERT INTO sys_user_org(tenant_id,user_id,org_id,delete_flag) VALUES
            (743,10,74301,0),(749,10,74303,0),(743,10,74304,1);
            INSERT INTO sys_menu(tenant_id,display_name,menu_type,delete_flag) VALUES
            (743,'own','MENU',0),(749,'foreign','MENU',0),(743,'deleted','MENU',1);
            """);
        assertThat(roles.selectOrgBriefsByIds(743L, List.of(74301L, 74303L, 74304L)))
            .extracting(row -> row.id()).containsExactly(74301L);
        assertThat(roles.selectOrgBriefsByIds(743L, List.of())).isEmpty();
        assertThat(roles.selectOrgBriefsByIds(743L, null)).isEmpty();
        assertThat(menus.selectUserOrgsByUserIds(743L, List.of(10L)))
            .extracting(row -> row.orgId()).containsExactly(74301L);
        assertThat(menus.selectUserOrgsByUserIds(743L, List.of())).isEmpty();
        assertThat(menus.selectUserOrgsByUserIds(743L, null)).isEmpty();
        assertThat(menus.selectMenus(743L)).extracting(row -> row.name()).containsExactly("own");
        assertThat(orgs.selectDefaultTreeRootOrgIds(743L)).containsExactly(74301L);
        assertThat(orgs.selectDescendantOrgIds(743L, 74301L)).containsExactlyInAnyOrder(74301L, 74302L);
        assertThat(orgs.selectDescendantOrgIds(749L, 74301L)).isEmpty();
    }
}
