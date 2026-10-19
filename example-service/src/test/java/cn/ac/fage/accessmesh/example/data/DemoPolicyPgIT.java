package cn.ac.fage.accessmesh.example.data;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.data.domain.DemoPolicyDomainService;
import cn.ac.fage.accessmesh.example.data.policy.PolicyCompiler;
import cn.ac.fage.accessmesh.example.data.service.DemoPolicyAppService;
import cn.ac.fage.accessmesh.example.data.dto.PolicyDtos.SaveReq;
import cn.ac.fage.accessmesh.example.metadata.domain.DemoMetadataDomainService;
import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker;
import cn.ac.fage.accessmesh.example.it.ExamplePgInfra;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker=true)
@SpringBootTest(properties={"spring.main.web-application-type=none","example.publication.enabled=false",
    "spring.cloud.nacos.config.enabled=false","spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false","spring.config.import=optional:classpath:/test-nope.yml"})
class DemoPolicyPgIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) { ExamplePgInfra.PG.register(r,DemoPolicyPgIT.class); }
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DemoMetadataDomainService metadata;
    @Autowired DemoPolicyDomainService policies;
    @Autowired DemoPolicyAppService app;
    @Autowired PolicyCompiler compiler;
    @Autowired tools.jackson.databind.json.JsonMapper json;
    @MockitoBean BusinessPermChecker permissionChecker;
    private TransactionTemplate tx;

    @BeforeEach void fixtures() {
        tx=new TransactionTemplate(transactions);
        for(String table:java.util.List.of("demo_policy","demo_publication","demo_report_field","demo_scope","demo_report","demo_dataset")) jdbc.update("DELETE FROM "+table);
        tx.executeWithoutResult(s -> {
            metadata.createReport(1,42,"policy-report","策略报表","sales-records","FIELDS");
            metadata.saveScope(1,42,"SCOPES","self",null,"本人",true,0);
        });
        jdbc.update("UPDATE demo_scope SET publication_status='PUBLISHED'");
    }

    @Test void shouldAtomicallyActivateNewVersionAndKeepHistoryOnConflict() {
        var config=compiler.parse(PolicyCompilerTest.valid());
        var first=tx.execute(s -> policies.save(1,42,0,config));
        assertEquals(1L,first.getVersion()); assertEquals(42L,first.getCreatedBy());
        assertEquals(30103,assertThrows(BizException.class,()->tx.execute(s -> policies.save(1,42,0,config))).getErrorCode());
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM demo_policy",Long.class));
        assertEquals(2L,tx.execute(s -> policies.save(1,42,1,config)).getVersion());
        assertEquals(2L,policies.current(1).getVersion());
        assertEquals(2L,jdbc.queryForObject("SELECT count(*) FROM demo_policy",Long.class));
        assertEquals(30102,assertThrows(BizException.class,()->policies.current(2)).getErrorCode());
    }

    @Test void shouldKeepPreviousPolicyWhenBindingIsUnknownOrNotPublished() {
        tx.executeWithoutResult(s -> policies.save(1,42,0,compiler.parse(PolicyCompilerTest.valid())));
        var unknown=PolicyCompilerTest.valid();
        ((tools.jackson.databind.node.ObjectNode)unknown.path("bindings").get(0)).put("resourceCode","unknown");
        assertEquals(30102,assertThrows(BizException.class,()->tx.execute(s -> policies.save(1,42,1,compiler.parse(unknown)))).getErrorCode());
        jdbc.update("UPDATE demo_scope SET publication_status='PENDING'");
        assertEquals(30108,assertThrows(BizException.class,()->tx.execute(s -> policies.save(1,42,1,compiler.parse(PolicyCompilerTest.valid())))).getErrorCode());
        assertEquals(1L,policies.current(1).getVersion());
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM demo_policy",Long.class));
    }

    @Test void shouldReservePolicyRowTypeEvenWithAllRowsAndNoScopeInstances() {
        var input=PolicyCompilerTest.valid(); input.putArray("bindings");
        ((tools.jackson.databind.node.ObjectNode)input.path("read")).put("rowResourceTypeCode","ALL_ROWS");
        ((tools.jackson.databind.node.ObjectNode)input.path("edit")).put("rowResourceTypeCode","ALL_ROWS");
        tx.executeWithoutResult(s -> policies.save(1,42,0,compiler.parse(input)));
        assertEquals(30102,assertThrows(BizException.class,()->tx.execute(s ->
            metadata.createReport(1,42,"collision","报表","sales-records","ALL_ROWS"))).getErrorCode());
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM demo_report",Long.class));
    }

    @Test void shouldRollBackPolicyAndActiveReferenceTogether() {
        assertThrows(ExpectedRollback.class,()->tx.executeWithoutResult(s -> {
            policies.save(1,42,0,compiler.parse(PolicyCompilerTest.valid()));
            throw new ExpectedRollback();
        }));
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM demo_policy",Long.class));
        assertEquals(0L,jdbc.queryForObject("SELECT active_policy_version FROM demo_dataset",Long.class));
    }

    @Test void shouldRequirePolicyManagementWithoutUsingDataGrants() {
        when(permissionChecker.check(anyString(),anyString(),any(),any())).thenReturn(new BusinessPermChecker.Decision(false,"NO_PERMISSION"));
        assertEquals(30004,assertThrows(BizException.class,()->app.save(new Caller(1,42,null),
            new SaveReq("sales-records",0L,PolicyCompilerTest.valid()))).getErrorCode());
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM demo_policy",Long.class));
        when(permissionChecker.check(anyString(),anyString(),any(),any())).thenReturn(new BusinessPermChecker.Decision(true,null));
        assertEquals(1L,app.save(new Caller(1,42,null),new SaveReq("sales-records",0L,PolicyCompilerTest.valid())).version());
        verify(permissionChecker,times(2)).check("1","42",null,BusinessPermChecker.Target.of("DEMO_POLICY",null,"MANAGE"));
    }
    @Test void shouldPreserveDecimalPrecisionInPolicyJsonBeforeSqlCompilation() {
        var input=PolicyCompilerTest.valid();
        var predicates=((tools.jackson.databind.node.ObjectNode)input.path("bindings").get(0)).putArray("predicates");
        predicates.addObject().put("fieldKey","amount").put("operator","EQ").put("valueSource","LITERAL")
            .put("value",new java.math.BigDecimal("9999999999999999.99"));
        var received=json.readTree(input.toString());
        var parsed=compiler.parse(received);
        var literal=parsed.bindings().values().iterator().next().getFirst().literals().getFirst();
        assertEquals(new java.math.BigDecimal("9999999999999999.99"),literal);
    }
    private static class ExpectedRollback extends RuntimeException {}
}
