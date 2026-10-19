package cn.ac.fage.accessmesh.example.data;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.example.data.policy.PolicyConfiguration.Operations;
import cn.ac.fage.accessmesh.example.data.security.DemoDataPermissions;
import cn.ac.fage.accessmesh.example.data.security.DemoPermissionUnavailable;
import cn.ac.fage.accessmesh.example.metadata.DemoPermission;
import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.example.perm.ExamplePermissionProperties;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp.*;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class DemoDataPermissionsTest {
    private final PermissionFeignClient client=mock(PermissionFeignClient.class);
    private final DemoDataPermissions permissions=new DemoDataPermissions(client,new ExamplePermissionProperties(
        Map.of("1",new ExamplePermissionProperties.Credential("test-tenant-1","test-secret")),true));
    private final Operations operations=new Operations("SCOPES","CUSTOM_READ","FIELDS","DATA_READ");
    private final Caller actor=new Caller(1,42,"192.0.2.1");
    private DemoDataPermissions.Scopes query() { return permissions.query(actor,"report-a",operations,DemoPermission.REPORT_READ); }

    @Test void shouldUseActualParentAndConsumeOnlyConfiguredCellsInFullCartesianResponse() {
        when(client.queryScopes(any(),anyString(),anyString())).thenReturn(R.ok(response(ScopeMode.INSTANCE,ScopeMode.ALL)));
        var result=query(); assertEquals(ScopeMode.ALL,result.fields().scopeMode());
        assertEquals("team-t1",result.rows().items().getFirst().resourceCode());
        var sent=org.mockito.ArgumentCaptor.forClass(QueryScopesReq.class);
        verify(client).queryScopes(sent.capture(),eq("test-tenant-1"),eq("test-secret"));
        assertEquals("report-a",sent.getValue().parentResourceCode());
        assertEquals("DEMO_REPORT",sent.getValue().parentResourceTypeCode());
        assertEquals("42",sent.getValue().subjectExternalId());
        assertEquals(List.of("DATA_READ"),sent.getValue().parentOperationCodes());
        assertEquals(Map.of("clientIp","192.0.2.1"),sent.getValue().context());
    }

    @ParameterizedTest @ValueSource(strings={"missing","duplicate","unknown-mode","unknown-reason","contradictory-denial"})
    void shouldRejectMalformedScopeResponse(String defect) {
        var normal=response(ScopeMode.ALL,ScopeMode.ALL);
        var groups=new ArrayList<>(normal.scopeGroups());
        String reason=null;
        switch(defect) {
            case "missing" -> groups.removeLast();
            case "duplicate" -> groups.add(groups.getFirst());
            case "unknown-mode" -> groups.set(0,new ScopeGroup("SCOPES","CUSTOM_READ",null,List.of()));
            case "unknown-reason" -> reason="UNKNOWN";
            case "contradictory-denial" -> reason="NO_PERMISSION";
            default -> throw new AssertionError();
        }
        when(client.queryScopes(any(),anyString(),anyString())).thenReturn(R.ok(new QueryScopesResp(reason,List.of("DATA_READ"),groups,0)));
        var failure=assertThrows(DemoPermissionUnavailable.class,this::query);
        assertEquals(30005,failure.getErrorCode()); assertFalse(failure.retryable());
    }

    @Test void shouldTreatLegitimateEmptyRejectionGroupsAsDenialAndDeniedBeforeEmpty() {
        when(client.queryScopes(any(),anyString(),anyString())).thenReturn(R.ok(new QueryScopesResp("NO_PERMISSION",List.of(),List.of(),0)));
        assertEquals(30004,assertThrows(BizException.class,this::query).getErrorCode());
        when(client.queryScopes(any(),anyString(),anyString())).thenReturn(R.ok(response(ScopeMode.EMPTY,ScopeMode.DENIED)));
        assertEquals(30004,assertThrows(BizException.class,this::query).getErrorCode());
        when(client.queryScopes(any(),anyString(),anyString())).thenReturn(R.ok(response(ScopeMode.EMPTY,ScopeMode.ALL)));
        assertEquals(ScopeMode.EMPTY,query().rows().scopeMode());
    }

    @Test void shouldDistinguishTemporaryTransportFromCredentialFailureWithoutRestoringIp() {
        var request=feign.Request.create(feign.Request.HttpMethod.POST,"http://test.invalid/scopes",Map.of(),null,
            java.nio.charset.StandardCharsets.UTF_8,null);
        var response=feign.Response.builder().request(request).status(503).reason("unavailable").headers(Map.of()).build();
        when(client.queryScopes(any(),anyString(),anyString())).thenThrow(feign.FeignException.errorStatus("query",response));
        assertTrue(assertThrows(DemoPermissionUnavailable.class,this::query).retryable());
        reset(client);
        when(client.queryScopes(any(),anyString(),anyString())).thenReturn(R.fail(20067,"credential disabled"));
        assertFalse(assertThrows(DemoPermissionUnavailable.class,this::query).retryable());
        reset(client);
        when(client.queryScopes(any(),anyString(),anyString())).thenReturn(R.ok(response(ScopeMode.ALL,ScopeMode.ALL)));
        permissions.query(new Caller(1,42,null),"report-a",operations,DemoPermission.REPORT_READ);
        verify(client).queryScopes(argThat(r -> r.context()==null),anyString(),anyString());
    }

    private static QueryScopesResp response(ScopeMode row,ScopeMode field) {
        return new QueryScopesResp(null,List.of("DATA_READ"),List.of(
            new ScopeGroup("SCOPES","CUSTOM_READ",row,row==ScopeMode.INSTANCE?List.of(new ScopeItem("team-t1","default","not-used")):List.of()),
            new ScopeGroup("FIELDS","DATA_READ",field,field==ScopeMode.INSTANCE?List.of(new ScopeItem("1","default","not-used")):List.of()),
            new ScopeGroup("SCOPES","DATA_READ",ScopeMode.DENIED,List.of()),
            new ScopeGroup("FIELDS","CUSTOM_READ",ScopeMode.DENIED,List.of())),0);
    }
}
