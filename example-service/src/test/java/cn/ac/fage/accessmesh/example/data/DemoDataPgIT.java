package cn.ac.fage.accessmesh.example.data;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.example.data.domain.DemoDataDomainService;
import cn.ac.fage.accessmesh.example.data.domain.DemoPolicyDomainService;
import cn.ac.fage.accessmesh.example.data.dto.DataDtos.*;
import cn.ac.fage.accessmesh.example.data.mapper.DemoDataMapper;
import cn.ac.fage.accessmesh.example.data.policy.PolicyCompiler;
import cn.ac.fage.accessmesh.example.data.service.DemoDataAppService;
import cn.ac.fage.accessmesh.example.it.ExamplePgInfra;
import cn.ac.fage.accessmesh.example.metadata.domain.DemoMetadataDomainService;
import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoReportField;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp.*;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker=true)
@SpringBootTest(properties={"spring.main.web-application-type=none","example.publication.enabled=false","example.execution.enabled=false",
    "spring.cloud.nacos.config.enabled=false","spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false","spring.config.import=optional:classpath:/test-nope.yml",
    "example.permission.allow-insecure=true","example.permission.tenant-credentials.1.credential-id=test-tenant-1",
    "example.permission.tenant-credentials.1.credential-secret=test-only-secret",
    "example.permission.tenant-credentials.2.credential-id=test-tenant-2","example.permission.tenant-credentials.2.credential-secret=test-only-secret"})
class DemoDataPgIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) { ExamplePgInfra.PG.register(r,DemoDataPgIT.class); }
    @Autowired cn.ac.fage.accessmesh.example.task.service.DemoExecutionAppService executions;
    @Autowired cn.ac.fage.accessmesh.example.task.domain.DemoExecutionDomainService executionDomain;
    @Autowired cn.ac.fage.accessmesh.example.task.service.DemoTaskAppService tasks;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DemoMetadataDomainService metadata;
    @Autowired DemoPolicyDomainService policies;
    @Autowired PolicyCompiler compiler;
    @Autowired DemoDataAppService app;
    @Autowired DemoDataDomainService domain;
    @Autowired cn.ac.fage.accessmesh.example.data.service.DemoImportAppService importer;
    @Autowired cn.ac.fage.accessmesh.example.data.domain.DemoImportDomainService importDomain;
    @Autowired JsonMapper json;
    @Autowired org.apache.ibatis.session.SqlSessionFactory sessions;
    @MockitoBean PermissionFeignClient client;
    @MockitoSpyBean DemoDataMapper records;
    private final Caller actor=new Caller(1,42,null);
    private Map<String,DemoReportField> fields;
    private List<String> readCodes;
    private List<String> editCodes;
    private List<String> rowCodes;
    private ScopeMode rowMode;
    private ScopeMode fieldMode;
    private boolean denyEdit;
    private TransactionTemplate tx;

    @BeforeEach void fixtures() {
        tx=new TransactionTemplate(transactions);
        for(String table:List.of("demo_execution","demo_task","demo_record","demo_import_batch","demo_policy","demo_publication","demo_report_field","demo_scope","demo_report","demo_dataset")) jdbc.update("DELETE FROM "+table);
        var keys=List.of("recordCode","ownerUserId","teamCode","departmentCode","regionCode","saleDate","productName","amount","remark");
        tx.executeWithoutResult(s -> {
            metadata.createReport(1,42,"r1","第一报表","sales-records","FIELDS");
            metadata.createReport(1,42,"r2","第二报表","sales-records","FIELDS");
            metadata.createReport(2,42,"r1","另一租户","sales-records","FIELDS");
            metadata.registerFields(1,42,"r1",0,keys);
            metadata.registerFields(1,42,"r2",0,keys);
            for(String code:List.of("self","team-t1","department-d1","region-r1")) metadata.saveScope(1,42,"SCOPES",code,null,"display name is not a predicate",true,0);
        });
        jdbc.update("UPDATE demo_report SET publication_status='PUBLISHED'");
        jdbc.update("UPDATE demo_report_field SET publication_status='PUBLISHED'");
        jdbc.update("UPDATE demo_scope SET publication_status='PUBLISHED'");
        var initial=configuration(); var initialBindings=(tools.jackson.databind.node.ArrayNode)initial.path("bindings");
        initialBindings.remove(initialBindings.size()-1);
        tx.executeWithoutResult(s -> policies.save(1,42,0,compiler.parse(initial)));
        fields=metadata.getFields(1,"r1").stream().collect(Collectors.toMap(DemoReportField::getFieldKey,f -> f));
        long batch=batch(1,metadata.getReport(1,"r1").getId(),5);
        record(1,batch,"A",42,"T1","D1","R1","Alpha","10.25","old-A");
        record(1,batch,"B",43,"T1","D1","R2","Beta","20.50",null);
        record(1,batch,"C",44,"T2","D1","R1","Gamma","30.00","old-C");
        record(1,batch,"D",45,"T3","D2","R2","Delta","40.00","old-D");
        record(1,batch,"F",42,"T1","D1","R1","Deleted","50.00","deleted");
        jdbc.update("UPDATE demo_record SET delete_flag=id WHERE tenant_id=1 AND record_code='F'");
        long revoked=batch(1,metadata.getReport(1,"r1").getId(),1);
        record(1,revoked,"E",42,"T1","D1","R1","Revoked","60.00","revoked");
        jdbc.update("UPDATE demo_import_batch SET status='REVOKED' WHERE id=?",revoked);
        record(2,batch(2,metadata.getReport(2,"r1").getId(),1),"A",42,"T1","D1","R1","Other tenant","99.00","private");
        rowCodes=List.of("team-t1"); rowMode=ScopeMode.INSTANCE; fieldMode=ScopeMode.INSTANCE; denyEdit=false;
        grantRead("recordCode","productName"); editCodes=List.of(fields.get("remark").getResourceCode());
        when(client.queryScopes(any(),eq("test-tenant-1"),eq("test-only-secret"))).thenAnswer(invocation -> R.ok(scopeResponse(invocation.getArgument(0))));
        clearInvocations(records);
    }

    @Test void shouldPersistBoundedSnapshotAndKeepCompletedOwnershipAfterRevocation() {
        allowExecution();
        var request=new cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.SubmitReq(UUID.randomUUID(),new Selection("r1",null,null,null));
        var queued=executions.submit(new Caller(1,42,"192.0.2.10"),request);
        assertEquals("PENDING",queued.status());
        assertNull(queued.policyVersion());
        clearInvocations(client);
        assertTrue(executions.runNext());
        var done=executions.get(actor,queued.executionKey());
        assertEquals("SUCCESS",done.status()); assertEquals(2L,done.recordCount()); assertEquals(1,done.attempt());
        assertEquals(List.of("recordCode","productName"),done.outputColumns().stream().map(Column::fieldKey).toList());
        String csv=new String(executions.download(actor,done.executionKey()),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("Alpha")); assertTrue(csv.contains("Beta")); assertFalse(csv.contains("amount")); assertFalse(csv.contains("Gamma"));
        verify(client).queryScopes(argThat(r->r.context()==null),anyString(),anyString());
        verify(client,atLeastOnce()).checkAuth(argThat(r->r.context()==null),anyString(),anyString());
        grantRead(); rowMode=ScopeMode.EMPTY;
        assertArrayEquals(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8),executions.download(actor,done.executionKey()));
        assertEquals(30004,assertThrows(BizException.class,()->executions.get(new Caller(1,43,null),done.executionKey())).getErrorCode());
        assertEquals(30004,assertThrows(BizException.class,()->executions.get(new Caller(2,42,null),done.executionKey())).getErrorCode());
        assertFalse(executions.runNext());
    }

    @Test void shouldRejectExplicitRevokedFieldWithoutAnArtifact() {
        allowExecution();
        var request=new cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.SubmitReq(UUID.randomUUID(),new Selection("r1",List.of(fields.get("productName").getId()),null,null));
        var execution=executions.submit(actor,request);
        grantRead("recordCode");
        assertTrue(executions.runNext());
        assertEquals("DENIED",executions.get(actor,execution.executionKey()).status());
        assertNull(jdbc.queryForObject("SELECT result_bytes FROM demo_execution WHERE execution_key=?",byte[].class,execution.executionKey()));
        assertEquals(30107,assertThrows(BizException.class,()->executions.download(actor,execution.executionKey())).getErrorCode());
    }

    @Test void shouldBindTriggerToOwnerAndPayloadAndExpireWithoutReexecution() {
        allowExecution();
        var request=new cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.SubmitReq(UUID.randomUUID(),new Selection("r1",null,null,null));
        var first=executions.submit(actor,request);
        assertEquals(first.executionKey(),executions.submit(actor,request).executionKey());
        assertEquals(30109,assertThrows(BizException.class,()->executions.submit(actor,
            new cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.SubmitReq(request.triggerKey(),new Selection("r2",null,null,null)))).getErrorCode());
        assertEquals(30004,assertThrows(BizException.class,()->executions.submit(new Caller(1,43,null),request)).getErrorCode());
        executions.runNext();
        jdbc.update("UPDATE demo_execution SET expires_at=now()-interval '1 second' WHERE execution_key=?",first.executionKey());
        assertEquals(30107,assertThrows(BizException.class,()->executions.download(actor,first.executionKey())).getErrorCode());
        executions.expire(); executions.recover();
        assertEquals("EXPIRED",executions.get(actor,first.executionKey()).status());
        assertFalse(executions.runNext());
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM demo_execution",Long.class));
    }

    @Test void shouldFenceStaleAttemptsAndRollbackResultWithTerminalStatus() {
        allowExecution();
        var queued=executions.submit(actor,new cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.SubmitReq(UUID.randomUUID(),new Selection("r1",null,null,null)));
        var first=tx.execute(s -> executionDomain.claim());
        first.setStatus("SUCCESS"); first.setResultBytes(new byte[]{1}); first.setResultRowCount(1L);
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s -> {
            assertTrue(executionDomain.finish(first)); throw new IllegalStateException("before commit");
        }));
        assertEquals("RUNNING",executions.get(actor,queued.executionKey()).status());
        assertNull(jdbc.queryForObject("SELECT result_bytes FROM demo_execution WHERE execution_key=?",byte[].class,queued.executionKey()));
        executions.recover();
        jdbc.update("UPDATE demo_execution SET next_attempt_at=now() WHERE execution_key=?",queued.executionKey());
        var second=tx.execute(s -> executionDomain.claim()); assertEquals(2,second.getAttempt());
        assertEquals(Boolean.FALSE,tx.execute(s -> executionDomain.finish(first)));
        second.setStatus("SUCCESS"); second.setResultBytes(new byte[]{2}); second.setResultRowCount(1L);
        tx.executeWithoutResult(s -> assertTrue(executionDomain.finish(second)));
        executions.recover(); assertFalse(executions.runNext());
        assertArrayEquals(new byte[]{2},executions.download(actor,queued.executionKey()));
        assertEquals(Boolean.FALSE,tx.execute(s -> executionDomain.finish(first)));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void shouldFailEntireSnapshotAtRowOrEncodedByteBudget(boolean byteBudget) {
        allowExecution(); rowMode=ScopeMode.ALL; grantRead("remark");
        jdbc.update("""
            INSERT INTO demo_record(tenant_id,batch_id,record_code,owner_user_id,team_code,department_code,region_code,sale_date,product_name,amount,remark)
            SELECT 1,batch_id,'budget-'||n,42,'T1','D1','R1',sale_date,'budget',1,repeat('界',1000)
            FROM demo_record CROSS JOIN generate_series(1,?) n WHERE tenant_id=1 AND record_code='A'
            """,byteBudget?996:997);
        var queued=executions.submit(actor,new cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.SubmitReq(UUID.randomUUID(),new Selection("r1",null,null,null)));
        executions.runNext(); var result=executions.get(actor,queued.executionKey());
        assertEquals("FAILED",result.status()); assertEquals("LIMIT_EXCEEDED",result.reason());
        assertEquals(30107,assertThrows(BizException.class,()->executions.download(actor,queued.executionKey())).getErrorCode());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void shouldReauthorizeRetriesAndStopAtThreeAttempts(boolean temporary) {
        allowExecution();
        var queued=executions.submit(actor,new cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.SubmitReq(UUID.randomUUID(),new Selection("r1",null,null,null)));
        when(client.queryScopes(any(),anyString(),anyString())).thenReturn(R.fail(temporary?99999:401,"service failure"));
        executions.runNext();
        var first=executions.get(actor,queued.executionKey());
        assertEquals(temporary?"PENDING":"FAILED",first.status());
        if(temporary) {
            assertFalse(executions.runNext());
            for(int attempt=2;attempt<=3;attempt++) {
                jdbc.update("UPDATE demo_execution SET next_attempt_at=now() WHERE execution_key=?",queued.executionKey());
                assertTrue(executions.runNext()); assertEquals(attempt,executions.get(actor,queued.executionKey()).attempt());
            }
            assertEquals("FAILED",executions.get(actor,queued.executionKey()).status());
        }
        executions.recover(); assertFalse(executions.runNext());
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM demo_execution",Long.class));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void shouldCreateNoArtifactForEmptyRowsOrFields(boolean emptyFields) {
        allowExecution();
        var queued=executions.submit(actor,new cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.SubmitReq(UUID.randomUUID(),new Selection("r1",null,null,null)));
        if(emptyFields) fieldMode=ScopeMode.EMPTY; else rowMode=ScopeMode.EMPTY;
        executions.runNext(); assertEquals("EMPTY",executions.get(actor,queued.executionKey()).status());
        verify(records,never()).page(any(),anyInt(),anyLong());
        assertEquals(30107,assertThrows(BizException.class,()->executions.download(actor,queued.executionKey())).getErrorCode());
    }

    @Test void shouldPreserveRowsWhoseOnlyAuthorizedValueIsNull() {
        grantRead("remark");
        var result=app.get(actor,new GetReq("r1",null,null,null,"B"));
        assertEquals("DATA",result.state());
        assertTrue(result.record().containsKey("remark")); assertNull(result.record().get("remark"));
    }

    @Test void shouldUseCurrentDefaultColumnsAndRowsOnExecution() {
        allowExecution();
        var queued=executions.submit(actor,new cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.SubmitReq(UUID.randomUUID(),new Selection("r1",null,null,null)));
        grantRead("productName"); rowCodes=List.of("self");
        executions.runNext();
        var result=executions.get(actor,queued.executionKey());
        assertEquals(1L,result.recordCount()); assertEquals(List.of("productName"),result.outputColumns().stream().map(Column::fieldKey).toList());
        String csv=new String(executions.download(actor,queued.executionKey()),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("Alpha")); assertFalse(csv.contains("Beta")); assertFalse(csv.contains("recordCode"));
    }

    @Test void shouldKeepFrozenFieldIdsAfterRemovalAndSameNameRegistration() {
        allowExecution(); fieldMode=ScopeMode.ALL;
        long field=fields.get("productName").getId();
        var selections=List.of(new Selection("r1",List.of(field),null,null),
            new Selection("r1",null,List.of(new Filter(field,"EQ",json.valueToTree("Alpha"),null)),null),
            new Selection("r1",null,null,new Sort(field,"ASC")));
        var queued=selections.stream().map(selection->executions.submit(actor,new cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.SubmitReq(UUID.randomUUID(),selection))).toList();
        tx.executeWithoutResult(s->{metadata.removeFields(1,42,"r1",1,List.of(field)); metadata.registerFields(1,42,"r1",2,List.of("productName"));});
        jdbc.update("UPDATE demo_report_field SET publication_status='PUBLISHED'");
        for(var execution:queued) {
            assertTrue(executions.runNext()); assertEquals("DENIED",executions.get(actor,execution.executionKey()).status());
        }
        verify(records,never()).page(any(),anyInt(),anyLong());
    }

    @Test void shouldSerializeConcurrentSameTriggerSubmissions() throws Exception {
        allowExecution();
        var request=new cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.SubmitReq(UUID.randomUUID(),new Selection("r1",null,null,null));
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var a=pool.submit(()->{start.await(); return executions.submit(actor,request);});
            var b=pool.submit(()->{start.await(); return executions.submit(actor,request);});
            start.countDown();
            assertEquals(a.get(10,java.util.concurrent.TimeUnit.SECONDS).executionKey(),b.get(10,java.util.concurrent.TimeUnit.SECONDS).executionKey());
        }
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM demo_execution",Long.class));
    }

    @Test void shouldOwnTaskAndFreezeEachManualExecutionAcrossConfigurationChanges() {
        allowExecution();
        var task=tasks.create(actor,new cn.ac.fage.accessmesh.example.task.dto.TaskDtos.CreateReq("snapshot","0 0 0 * * *",new Selection("r1",List.of(fields.get("productName").getId()),null,null)));
        assertFalse(task.enabled());
        final long taskId=task.taskId();
        var trigger=new cn.ac.fage.accessmesh.example.task.dto.TaskDtos.TriggerReq(task.taskId(),UUID.randomUUID());
        assertEquals(30101,assertThrows(BizException.class,()->tasks.trigger(actor,trigger)).getErrorCode());
        assertEquals(30004,assertThrows(BizException.class,()->tasks.get(new Caller(1,43,null),taskId)).getErrorCode());
        task=tasks.setEnabled(actor,new cn.ac.fage.accessmesh.example.task.dto.TaskDtos.EnabledReq(task.taskId(),task.version(),true));
        var execution=tasks.trigger(actor,trigger);
        assertEquals(execution.executionKey(),tasks.trigger(actor,trigger).executionKey());
        tasks.update(actor,new cn.ac.fage.accessmesh.example.task.dto.TaskDtos.UpdateReq(task.taskId(),task.version(),"changed","0 0 1 * * *",new Selection("r1",List.of(fields.get("recordCode").getId()),null,null)));
        assertTrue(executions.runNext());
        var result=executions.get(actor,execution.executionKey());
        assertEquals("SUCCESS",result.status()); assertEquals(List.of("productName"),result.outputColumns().stream().map(Column::fieldKey).toList());
        assertEquals(execution.executionKey(),tasks.trigger(actor,trigger).executionKey());
    }

    @Test void shouldSkipDisabledPendingTaskAndRestrictAllOwnerViews() {
        allowExecution();
        var task=enabledTask(); long id=task.taskId();
        var trigger=new cn.ac.fage.accessmesh.example.task.dto.TaskDtos.TriggerReq(id,UUID.randomUUID());
        var queued=tasks.trigger(actor,trigger);
        tasks.setEnabled(actor,new cn.ac.fage.accessmesh.example.task.dto.TaskDtos.EnabledReq(id,task.version(),false));
        clearInvocations(client,records);
        assertTrue(executions.runNext());
        assertEquals("SKIPPED",executions.get(actor,queued.executionKey()).status());
        verify(client,never()).queryScopes(any(),anyString(),anyString()); verify(records,never()).page(any(),anyInt(),anyLong());
        var other=new Caller(1,43,null);
        assertEquals(0,tasks.page(other,new cn.ac.fage.accessmesh.example.task.dto.TaskDtos.PageReq(1,20)).total());
        assertEquals(30004,assertThrows(BizException.class,()->tasks.setEnabled(other,new cn.ac.fage.accessmesh.example.task.dto.TaskDtos.EnabledReq(id,task.version(),true))).getErrorCode());
        assertEquals(30004,assertThrows(BizException.class,()->tasks.trigger(other,trigger)).getErrorCode());
        assertEquals(30004,assertThrows(BizException.class,()->executions.page(other,new cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.PageReq(id,1,20))).getErrorCode());
        assertEquals(30004,assertThrows(BizException.class,()->tasks.get(new Caller(2,42,null),id)).getErrorCode());
    }

    @Test void shouldDeduplicateCronAndResetOnlyFutureScheduleOnRecovery() {
        allowExecution(); var task=enabledTask();
        var due=java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(2).withSecond(0).withNano(0);
        jdbc.update("UPDATE demo_task SET next_scheduled_time=? WHERE id=?",due,task.taskId());
        assertTrue(tasks.scheduleNext()); assertFalse(tasks.scheduleNext());
        var page=executions.page(actor,new cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.PageReq(task.taskId(),1,20));
        assertEquals(1,page.total()); assertEquals(due,page.items().getFirst().scheduledTime());
        var key=page.items().getFirst().executionKey();
        // Revisit precisely the same planned instant: the frozen execution is retained.
        jdbc.update("UPDATE demo_task SET next_scheduled_time=? WHERE id=?",due,task.taskId());
        assertTrue(tasks.scheduleNext()); assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM demo_execution",Long.class));
        jdbc.update("UPDATE demo_task SET next_scheduled_time=now()-interval '1 hour' WHERE id=?",task.taskId());
        tasks.resetSchedule(); executions.recover(); assertFalse(tasks.scheduleNext());
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM demo_execution",Long.class));
        assertTrue(jdbc.queryForObject("SELECT next_scheduled_time>clock_timestamp() FROM demo_task WHERE id=?",Boolean.class,task.taskId()));
        assertTrue(executions.runNext()); assertEquals("SUCCESS",executions.get(actor,key).status());
        assertEquals(task.taskId(),tasks.get(actor,task.taskId()).taskId());
    }

    @Test void shouldRejectStaleTaskVersionAndChangedManualTriggerTarget() {
        allowExecution(); var a=enabledTask(); var b=enabledTask();
        var trigger=new cn.ac.fage.accessmesh.example.task.dto.TaskDtos.TriggerReq(a.taskId(),UUID.randomUUID()); tasks.trigger(actor,trigger);
        assertEquals(30109,assertThrows(BizException.class,()->tasks.trigger(actor,new cn.ac.fage.accessmesh.example.task.dto.TaskDtos.TriggerReq(b.taskId(),trigger.triggerKey()))).getErrorCode());
        tasks.setEnabled(actor,new cn.ac.fage.accessmesh.example.task.dto.TaskDtos.EnabledReq(a.taskId(),a.version(),false));
        assertEquals(30103,assertThrows(BizException.class,()->tasks.update(actor,new cn.ac.fage.accessmesh.example.task.dto.TaskDtos.UpdateReq(a.taskId(),a.version(),"stale","0 * * * * *",new Selection("r1",null,null,null)))).getErrorCode());
        assertEquals(a.name(),tasks.get(actor,a.taskId()).name()); assertFalse(tasks.get(actor,a.taskId()).enabled());
    }

    @Test void shouldRecheckTaskTriggerPermissionAndHonorRevocationDuringRetry() {
        allowExecution(); var task=enabledTask();
        var queued=tasks.trigger(actor,new cn.ac.fage.accessmesh.example.task.dto.TaskDtos.TriggerReq(task.taskId(),UUID.randomUUID()));
        when(client.queryScopes(any(),anyString(),anyString())).thenReturn(R.fail(99999,"temporary"));
        executions.runNext(); assertEquals("PENDING",executions.get(actor,queued.executionKey()).status());
        when(client.checkAuth(any(),anyString(),anyString())).thenAnswer(i->{
            var r=(cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq)i.getArgument(0);
            return R.ok("TRIGGER".equals(r.operationCode())?cn.ac.fage.accessmesh.perm.common.dto.resp.AuthCheckResp.deny("NO_PERMISSION",false):
                cn.ac.fage.accessmesh.perm.common.dto.resp.AuthCheckResp.allow(List.of(),List.of(),false));
        });
        jdbc.update("UPDATE demo_execution SET next_attempt_at=now() WHERE execution_key=?",queued.executionKey());
        clearInvocations(client); executions.runNext();
        assertEquals("DENIED",executions.get(actor,queued.executionKey()).status());
        verify(client,never()).queryScopes(any(),anyString(),anyString());
    }

    private cn.ac.fage.accessmesh.example.task.dto.TaskDtos.TaskResp enabledTask() {
        var task=tasks.create(actor,new cn.ac.fage.accessmesh.example.task.dto.TaskDtos.CreateReq("snapshot","0 0 0 * * *",new Selection("r1",null,null,null)));
        return tasks.setEnabled(actor,new cn.ac.fage.accessmesh.example.task.dto.TaskDtos.EnabledReq(task.taskId(),task.version(),true));
    }

    private void allowExecution() {
        when(client.checkAuth(any(),anyString(),anyString())).thenReturn(R.ok(
            cn.ac.fage.accessmesh.perm.common.dto.resp.AuthCheckResp.allow(List.of(),List.of(),false)));
    }

    @Test void shouldApplyRowUnionBeforePagingAndProjectOnlyAuthorizedColumns() {
        rowCodes=List.of("self","team-t1");
        var first=app.page(actor,page("r1",1,1));
        assertEquals(2,first.page().total()); assertTrue(first.page().hasNext());
        assertEquals(Map.of("recordCode","A","productName","Alpha"),first.page().items().getFirst());
        var second=app.page(actor,page("r1",2,1));
        assertEquals("B",second.page().items().getFirst().get("recordCode")); assertFalse(second.page().hasNext());
        assertEquals(List.of("recordCode","productName"),first.columns().stream().map(Column::fieldKey).toList());
    }

    @Test void shouldAddressKnownCodesWithoutLeakingHiddenIdentifierOrInvisibleRows() {
        grantRead("productName");
        assertEquals(Map.of("productName","Alpha"),app.get(actor,new GetReq("r1",null,null,null,"A")).record());
        assertNull(app.get(actor,new GetReq("r1",null,null,null,"C")).record());
        var result=app.batch(actor,new BatchGetReq("r1",null,null,null,List.of("B","C","A")));
        assertEquals(Map.of("productName","Beta"),result.items().get(0).record());
        assertFalse(result.items().get(1).available()); assertNull(result.items().get(1).record());
        assertEquals(Map.of("productName","Alpha"),result.items().get(2).record());
        assertEquals(2,result.items().get(2).index());
    }

    @Test void shouldRejectHiddenColumnFilteringAndKeepValuesParameterized() {
        grantRead("productName");
        var hidden=new Filter(fields.get("amount").getId(),"EQ",json.valueToTree(10.25),null);
        assertEquals(30004,assertThrows(BizException.class,()->app.page(actor,new PageReq("r1",null,List.of(hidden),null,1,20))).getErrorCode());
        verify(records,never()).page(any(),anyInt(),anyLong());
        var injected=new Filter(fields.get("productName").getId(),"EQ",json.valueToTree("' OR 1=1 --"),null);
        assertEquals(0,app.page(actor,new PageReq("r1",null,List.of(injected),null,1,20)).page().total());
        assertEquals(7L,jdbc.queryForObject("SELECT count(*) FROM demo_record",Long.class));
    }

    @Test void shouldNotQueryBusinessRecordsWhenRowsOrColumnsAreEmptyAndKeepReportsIsolated() {
        grantRead("productName");
        var other=app.page(actor,page("r2",1,20));
        assertEquals("EMPTY_FIELDS",other.state()); assertEquals(0,other.page().total());
        rowMode=ScopeMode.EMPTY;
        var emptyRows=app.page(actor,page("r1",1,20));
        assertEquals("EMPTY_ROWS",emptyRows.state()); assertEquals(1,emptyRows.columns().size());
        fieldMode=ScopeMode.EMPTY; rowMode=ScopeMode.ALL;
        assertEquals("EMPTY_FIELDS",app.page(actor,page("r1",1,20)).state());
        verify(records,never()).page(any(),anyInt(),anyLong()); verify(records,never()).count(any());
    }

    @Test void shouldKeepTenantBatchAndDeletionGuardsWhenRowScopeIsAll() {
        rowMode=ScopeMode.ALL; grantRead("productName","remark","saleDate","amount");
        var result=app.page(actor,page("r1",1,20));
        assertEquals(4,result.page().total());
        assertEquals(new java.math.BigDecimal("10.25"),result.page().items().getFirst().get("amount"));
        assertEquals("2026-10-19",result.page().items().getFirst().get("saleDate"));
        assertTrue(result.page().items().get(1).containsKey("remark")); assertNull(result.page().items().get(1).get("remark"));
        assertFalse(result.page().items().getFirst().containsKey("recordCode"));
        var date=new Filter(fields.get("saleDate").getId(),"EQ",json.valueToTree("2026-10-19"),null);
        assertEquals(4,app.page(actor,new PageReq("r1",null,List.of(date),null,1,20)).page().total());
    }

    @Test void shouldNeverSelectHiddenBusinessColumnsEvenForBatchAddressing() {
        grantRead("productName");
        var plan=domain.authorizeRead(actor,new Selection("r1",null,null,null));
        for(String method:List.of("page","one","batch")) {
            var args=new HashMap<String,Object>(); args.put("plan",plan); args.put("limit",20); args.put("offset",0L);
            args.put("code","A"); args.put("codes",List.of("A","B"));
            String sql=sessions.getConfiguration().getMappedStatement("cn.ac.fage.accessmesh.example.data.mapper.DemoDataMapper."+method)
                .getBoundSql(args).getSql();
            String projection=sql.substring(0,sql.indexOf(" FROM ")).toLowerCase(java.util.Locale.ROOT).replace("\"","");
            assertFalse(projection.contains("*"));
            assertTrue(projection.contains("r.product_name"));
            for(var field:cn.ac.fage.accessmesh.example.data.catalog.DemoField.values()) {
                if(field!=cn.ac.fage.accessmesh.example.data.catalog.DemoField.PRODUCT_NAME) assertFalse(projection.contains("r."+field.column()),method+": "+field);
            }
        }
    }

    @Test void shouldRollbackMixedWriteAndAllowExplicitClearWithoutReadingRemark() {
        assertEquals(30004,assertThrows(BizException.class,()->app.batchUpdate(actor,new BatchUpdateReq("r1",List.of(
            new Change("A","changed",false),new Change("C","forbidden",false))))).getErrorCode());
        assertEquals("old-A",remark("A")); assertEquals("old-C",remark("C"));
        assertEquals(1,app.update(actor,new UpdateReq("r1","A",null,true)).updatedCount());
        assertNull(remark("A"));
        denyEdit=true;
        assertEquals(30004,assertThrows(BizException.class,()->app.update(actor,new UpdateReq("r1","B","no",false))).getErrorCode());
        assertNull(remark("B"));
    }

    @Test void shouldRejectLocalScopeVersionChangeAndNeverUseReadAuthorizationForWrites() {
        var captured=domain.authorizeRead(actor,new Selection("r1",null,null,null));
        assertEquals(30004,assertThrows(BizException.class,()->tx.execute(s -> domain.update(captured,42,List.of(new Change("A","no",false))))).getErrorCode());
        tx.executeWithoutResult(s -> metadata.saveScope(1,42,"SCOPES","team-t1",null,"changed",false,1));
        assertEquals(30103,assertThrows(BizException.class,()->tx.execute(s -> domain.page(captured,1,20))).getErrorCode());
        verify(records,never()).page(any(),anyInt(),anyLong()); verify(records,never()).update(any(),anyLong(),anyList());
    }

    @Test void shouldApplyRegionConfigurationWithoutChangingJavaOrAuthorizationAlgorithm() {
        tx.executeWithoutResult(s -> policies.save(1,42,1,compiler.parse(configuration())));
        rowCodes=List.of("region-r1"); grantRead("recordCode");
        var result=app.page(actor,page("r1",1,20));
        assertEquals(2,result.policyVersion());
        assertEquals(List.of("A","C"),result.page().items().stream().map(r -> r.get("recordCode")).toList());
        rowCodes=List.of("team-t1","not-configured");
        assertEquals(30102,assertThrows(BizException.class,()->app.page(actor,page("r1",1,20))).getErrorCode());
    }

    @ParameterizedTest @ValueSource(strings={"policy","catalog","report"})
    void shouldRejectLocalVersionChangesAfterAuthorization(String changed) {
        var captured=domain.authorizeRead(actor,new Selection("r1",null,null,null));
        tx.executeWithoutResult(s -> {
            switch(changed) {
                case "policy" -> policies.save(1,42,1,compiler.parse(configuration()));
                case "catalog" -> metadata.updateField(1,42,"r1",1,fields.get("remark").getId(),false);
                case "report" -> metadata.updateReport(1,42,"r1",1,"changed",true);
                default -> throw new AssertionError(changed);
            }
        });
        assertEquals(30103,assertThrows(BizException.class,()->tx.execute(s -> domain.page(captured,1,20))).getErrorCode());
        verify(records,never()).page(any(),anyInt(),anyLong());
    }

    @ParameterizedTest @ValueSource(ints={4095,4800})
    void shouldRejectExpandedSqlBudgetWithoutTruncatingPermissionPredicates(int parameters) {
        var configuration=configuration(); var bindings=configuration.putArray("bindings");
        List<String> codes=new ArrayList<>();
        int left=parameters,scopeIndex=0;
        while(left>0) {
            String code="budget-"+scopeIndex++; codes.add(code);
            tx.executeWithoutResult(s -> metadata.saveScope(1,42,"SCOPES",code,null,code,true,0));
            var predicates=bindings.addObject().put("resourceTypeCode","SCOPES").put("resourceCode",code).putArray("predicates");
            for(int clause=0;clause<8 && left>0;clause++) {
                var values=predicates.addObject().put("fieldKey","ownerUserId").put("operator","IN").put("valueSource","LITERAL").putArray("values");
                int size=Math.min(left,100); for(int i=1;i<=size;i++) values.add(i); left-=size;
            }
        }
        jdbc.update("UPDATE demo_scope SET publication_status='PUBLISHED'");
        tx.executeWithoutResult(s -> policies.save(1,42,1,compiler.parse(configuration)));
        rowCodes=List.copyOf(codes);
        assertEquals(30106,assertThrows(BizException.class,()->app.page(actor,page("r1",1,20))).getErrorCode());
    }

    @Test void shouldCancelBlockedSqlAtBudgetAndRollBackTheWrite() throws Exception {
        java.util.concurrent.CompletableFuture<UpdatedResp> work=null;
        try(var connection=Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("SELECT id FROM demo_record WHERE tenant_id=1 AND record_code='A' FOR UPDATE")) {
                statement.executeQuery().close();
            }
            try {
                work=java.util.concurrent.CompletableFuture.supplyAsync(()->app.update(actor,new UpdateReq("r1","A","should roll back",false)));
                var pending=work;
                var failure=assertThrows(java.util.concurrent.ExecutionException.class,()->pending.get(15,java.util.concurrent.TimeUnit.SECONDS));
                assertInstanceOf(BizException.class,failure.getCause());
                assertEquals(30106,((BizException)failure.getCause()).getErrorCode());
            } finally {
                connection.rollback();
                if(work!=null) work.handle((result,error)->null).get(10,java.util.concurrent.TimeUnit.SECONDS);
            }
        }
        assertEquals("old-A",remark("A"));
    }

    @Test void shouldImportReplayRevokeAndReimportOnlyWithNewKey() {
        allowImport(); String key=UUID.randomUUID().toString(); var request=new cn.ac.fage.accessmesh.example.data.dto.ImportDtos.ImportReq("r1",key);
        var first=importer.submit(actor,request,csv("X")); assertEquals("IMPORTED",first.status()); assertEquals(1,first.recordCount());
        assertEquals(first,importer.submit(actor,request,csv("X")));
        assertEquals(30104,assertThrows(BizException.class,()->importer.submit(actor,request,csv("Y"))).getErrorCode());
        var keyReq=new cn.ac.fage.accessmesh.example.data.dto.ImportDtos.KeyReq(key);
        assertEquals(30004,assertThrows(BizException.class,()->importer.status(new Caller(1,43,null),keyReq)).getErrorCode());
        assertEquals("REVOKED",importer.revoke(actor,keyReq).status());
        assertEquals("REVOKED",importer.submit(actor,request,csv("X")).status());
        assertEquals("REVOKED",importer.revoke(actor,keyReq).status());
        var next=importer.submit(actor,new cn.ac.fage.accessmesh.example.data.dto.ImportDtos.ImportReq("r1",UUID.randomUUID().toString()),csv("X"));
        assertEquals("IMPORTED",next.status());
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM demo_record WHERE tenant_id=1 AND record_code='X' AND delete_flag=0",Long.class));
    }

    @Test void shouldRejectInstanceEvenWhenItCoversAllRowsAndRejectMissingSubmittedColumnGrant() {
        allowImport(); rowMode=ScopeMode.INSTANCE;
        jdbc.update("UPDATE demo_record SET team_code='T1' WHERE tenant_id=1");
        assertEquals(4,app.page(actor,page("r1",1,20)).page().total());
        var request=new cn.ac.fage.accessmesh.example.data.dto.ImportDtos.ImportReq("r1",UUID.randomUUID().toString());
        assertEquals(30004,assertThrows(BizException.class,()->importer.submit(actor,request,csv("X"))).getErrorCode());
        rowMode=ScopeMode.ALL; fieldMode=ScopeMode.INSTANCE;
        editCodes=fields.values().stream().filter(f -> !f.getFieldKey().equals("amount")).map(DemoReportField::getResourceCode).toList();
        assertEquals(30004,assertThrows(BizException.class,()->importer.submit(actor,request,csv("X"))).getErrorCode());
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM demo_import_batch WHERE import_key=?::uuid",Long.class,request.importKey()));
    }

    @Test void shouldRollbackEveryChunkWhenLastRecordConflicts() {
        allowImport(); String key=UUID.randomUUID().toString(); StringBuilder input=new StringBuilder(DemoCsvReaderTest.HEADER);
        for(int i=0;i<256;i++) input.append(csvRow("late-"+i)); input.append(csvRow("A"));
        var file=new org.springframework.mock.web.MockMultipartFile("file","records.csv","text/csv",input.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(30104,assertThrows(BizException.class,()->importer.submit(actor,new cn.ac.fage.accessmesh.example.data.dto.ImportDtos.ImportReq("r1",key),file)).getErrorCode());
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM demo_record WHERE record_code LIKE 'late-%'",Long.class));
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM demo_import_batch WHERE import_key=?::uuid",Long.class,key));
    }

    @Test void shouldSerializeConcurrentSameKeyToOneBatch() throws Exception {
        allowImport(); var request=new cn.ac.fage.accessmesh.example.data.dto.ImportDtos.ImportReq("r1",UUID.randomUUID().toString());
        var start=new java.util.concurrent.CountDownLatch(1);
        java.util.function.Supplier<cn.ac.fage.accessmesh.example.data.dto.ImportDtos.ImportResp> submit=()->{
            try { assertTrue(start.await(10,java.util.concurrent.TimeUnit.SECONDS)); }
            catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            return importer.submit(actor,request,csv("parallel"));
        };
        var first=java.util.concurrent.CompletableFuture.supplyAsync(submit); var second=java.util.concurrent.CompletableFuture.supplyAsync(submit); start.countDown();
        assertEquals(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM demo_import_batch WHERE import_key=?::uuid",Long.class,request.importKey()));
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM demo_record WHERE record_code='parallel'",Long.class));
    }

    @Test void shouldKeepOriginalReportAndFieldIdentitiesForRevocation() {
        allowImport(); String key=UUID.randomUUID().toString();
        importer.submit(actor,new cn.ac.fage.accessmesh.example.data.dto.ImportDtos.ImportReq("r1",key),csv("X"));
        var wrongReport=domain.authorizeImport(actor,"r2",EnumSet.allOf(cn.ac.fage.accessmesh.example.data.catalog.DemoField.class));
        assertEquals(30004,assertThrows(BizException.class,()->tx.execute(s -> importDomain.revoke(wrongReport,42,UUID.fromString(key)))).getErrorCode());
        tx.executeWithoutResult(s -> {
            metadata.removeFields(1,42,"r1",1,List.of(fields.get("amount").getId()));
            metadata.registerFields(1,42,"r1",2,List.of("amount"));
        });
        jdbc.update("UPDATE demo_report_field SET publication_status='PUBLISHED'");
        assertEquals(30102,assertThrows(BizException.class,()->importer.revoke(actor,new cn.ac.fage.accessmesh.example.data.dto.ImportDtos.KeyReq(key))).getErrorCode());
        assertEquals("IMPORTED",importer.status(actor,new cn.ac.fage.accessmesh.example.data.dto.ImportDtos.KeyReq(key)).status());
    }

    @Test void shouldNotWriteAnOmittedOptionalColumnWithoutItsGrant() {
        allowImport(); fieldMode=ScopeMode.INSTANCE;
        editCodes=fields.values().stream().filter(f -> !f.getFieldKey().equals("remark")).map(DemoReportField::getResourceCode).toList();
        var file=new org.springframework.mock.web.MockMultipartFile("file","records.csv","text/csv",
            ("recordCode,ownerUserId,teamCode,departmentCode,regionCode,saleDate,productName,amount\nwithout-remark,42,T1,D1,R1,2026-10-19,item,1.00\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(1,importer.submit(actor,new cn.ac.fage.accessmesh.example.data.dto.ImportDtos.ImportReq("r1",UUID.randomUUID().toString()),file).recordCount());
        assertNull(remark("without-remark"));
    }

    private void allowImport() {
        rowMode=ScopeMode.ALL; fieldMode=ScopeMode.ALL;
        when(client.checkAuth(any(),anyString(),anyString())).thenReturn(R.ok(cn.ac.fage.accessmesh.perm.common.dto.resp.AuthCheckResp.allow(List.of(),List.of(),false)));
    }
    private org.springframework.mock.web.MockMultipartFile csv(String code) {
        return new org.springframework.mock.web.MockMultipartFile("file","records.csv","text/csv",(DemoCsvReaderTest.HEADER+csvRow(code)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static String csvRow(String code) { return code+",42,T1,D1,R1,2026-10-19,Imported,1.00,note\r\n"; }

    private PageReq page(String report,int page,int size) { return new PageReq(report,null,null,null,page,size); }
    private void grantRead(String... keys) { readCodes=Arrays.stream(keys).map(k -> fields.get(k).getResourceCode()).toList(); }
    private QueryScopesResp scopeResponse(QueryScopesReq request) {
        boolean edit=request.parentOperationCodes().contains("DATA_EDIT");
        if(edit&&denyEdit) return new QueryScopesResp("NO_PERMISSION",List.of(),List.of(),0);
        List<ScopeGroup> groups=new ArrayList<>();
        String rowOp=edit?"CUSTOM_EDIT":"CUSTOM_READ",fieldOp=edit?"DATA_EDIT":"DATA_READ";
        for(String type:request.scopeResourceTypeCodes()) for(String op:request.scopeOperationCodes()) {
            ScopeMode mode=ScopeMode.DENIED; List<ScopeItem> items=List.of();
            if(type.equals("SCOPES")&&op.equals(rowOp)) {
                mode=rowMode; if(mode==ScopeMode.INSTANCE) items=rowCodes.stream().map(c -> new ScopeItem(c,"default","ignored name")).toList();
            }
            if(type.equals("FIELDS")&&op.equals(fieldOp)) {
                mode=fieldMode; if(mode==ScopeMode.INSTANCE) items=(edit?editCodes:readCodes).stream().map(c -> new ScopeItem(c,"default","ignored name")).toList();
            }
            groups.add(new ScopeGroup(type,op,mode,items));
        }
        return new QueryScopesResp(null,request.parentOperationCodes(),groups,0);
    }
    private ObjectNode configuration() {
        var value=PolicyCompilerTest.valid(); var bindings=value.putArray("bindings");
        var self=bindings.addObject().put("resourceTypeCode","SCOPES").put("resourceCode","self");
        self.putArray("predicates").addObject().put("fieldKey","ownerUserId").put("operator","EQ").put("valueSource","SUBJECT_ID");
        for(var row:List.of(List.of("team-t1","teamCode","T1"),List.of("department-d1","departmentCode","D1"),List.of("region-r1","regionCode","R1"))) {
            bindings.addObject().put("resourceTypeCode","SCOPES").put("resourceCode",row.get(0)).putArray("predicates")
                .addObject().put("fieldKey",row.get(1)).put("operator","EQ").put("valueSource","LITERAL").put("value",row.get(2));
        }
        return value;
    }
    private long batch(long tenant,long report,int count) {
        return jdbc.queryForObject("""
            INSERT INTO demo_import_batch(tenant_id,report_id,import_key,file_sha256,imported_fields,record_count,policy_version,catalog_version,created_by)
            VALUES(?,?,?::uuid,?,CAST(? AS jsonb),?,1,1,42) RETURNING id
            """,Long.class,tenant,report,UUID.randomUUID().toString(),"0".repeat(64),"[1]",count);
    }
    private void record(long tenant,long batch,String code,long owner,String team,String department,String region,String product,String amount,String remark) {
        jdbc.update("""
            INSERT INTO demo_record(tenant_id,batch_id,record_code,owner_user_id,team_code,department_code,region_code,sale_date,product_name,amount,remark)
            VALUES(?,?,?,?,?,?,?,DATE '2026-10-19',?,?,?)
            """,tenant,batch,code,owner,team,department,region,product,new java.math.BigDecimal(amount),remark);
    }
    private String remark(String code) { return jdbc.queryForObject("SELECT remark FROM demo_record WHERE tenant_id=1 AND record_code=?",String.class,code); }
}
