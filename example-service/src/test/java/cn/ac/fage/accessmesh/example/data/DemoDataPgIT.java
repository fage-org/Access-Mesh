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
@SpringBootTest(properties={"spring.main.web-application-type=none","example.publication.enabled=false",
    "spring.cloud.nacos.config.enabled=false","spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false","spring.config.import=optional:classpath:/test-nope.yml",
    "example.permission.allow-insecure=true","example.permission.tenant-credentials.1.credential-id=test-tenant-1",
    "example.permission.tenant-credentials.1.credential-secret=test-only-secret"})
class DemoDataPgIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) { ExamplePgInfra.PG.register(r,DemoDataPgIT.class); }
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DemoMetadataDomainService metadata;
    @Autowired DemoPolicyDomainService policies;
    @Autowired PolicyCompiler compiler;
    @Autowired DemoDataAppService app;
    @Autowired DemoDataDomainService domain;
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
        for(String table:List.of("demo_record","demo_import_batch","demo_policy","demo_publication","demo_report_field","demo_scope","demo_report","demo_dataset")) jdbc.update("DELETE FROM "+table);
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
