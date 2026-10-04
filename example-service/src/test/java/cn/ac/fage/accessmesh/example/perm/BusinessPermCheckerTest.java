package cn.ac.fage.accessmesh.example.perm;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryResourcesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryResourcesResp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 业务最终检查门面单测（T-ACCESS-061）：
 * 主体纪律（subject=可信请求头值）、父上下文透传、fail-closed（信封非 200/data null/
 * 传输异常一律 30005）、请求级租户凭证隔离。
 */
class BusinessPermCheckerTest {

    private final PermissionFeignClient client = mock(PermissionFeignClient.class);
    private final BusinessPermChecker checker = new BusinessPermChecker(client,
        new ExamplePermissionProperties(Map.of(
            "7", new ExamplePermissionProperties.Credential("sc-7", "sk-7"),
            "1", new ExamplePermissionProperties.Credential("sc-1", "sk-1")), true));

    @Test
    void shouldServeTwoConfiguredTenants_fromSameChecker() {
        when(client.checkAuth(any(AuthCheckReq.class), anyString(), anyString()))
            .thenReturn(R.ok(AuthCheckResp.allow(List.of(), List.of(), false)));
        assertThat(checker.check("7", "42", null,
            BusinessPermChecker.Target.of("EXAMPLE", "report-1", "VIEW")).allowed()).isTrue();
        assertThat(checker.check("1", "99", null,
            BusinessPermChecker.Target.of("EXAMPLE", "report-1", "VIEW")).allowed()).isTrue();
        verify(client).checkAuth(any(AuthCheckReq.class), eq("sc-7"), eq("sk-7"));
        verify(client).checkAuth(any(AuthCheckReq.class), eq("sc-1"), eq("sk-1"));
    }

    @Test
    void shouldRejectOtherTenant_beforeCallingPermissionService() {
        assertThatThrownBy(() -> checker.check("8", "42", null,
            BusinessPermChecker.Target.of("EXAMPLE", "report-1", "VIEW")))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(30004));
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    @Test
    @DisplayName("单目标 DECISION：subject=可信请求头 userId（非任何客户端可控值），目标字段原样透传")
    void check_buildsSubjectFromTrustedHeaderOnly() {
        when(client.checkAuth(any(AuthCheckReq.class), anyString(), anyString())).thenReturn(R.ok(AuthCheckResp.allow(List.of(1L), List.of(2L), false)));

        BusinessPermChecker.Decision d = checker.check("7", "42", null,
            BusinessPermChecker.Target.of("EXAMPLE", "report-1", "VIEW"));

        assertThat(d.allowed()).isTrue();
        ArgumentCaptor<AuthCheckReq> captor = ArgumentCaptor.forClass(AuthCheckReq.class);
        verify(client).checkAuth(captor.capture(), eq("sc-7"), eq("sk-7"));
        AuthCheckReq sent = captor.getValue();
        assertThat(sent.subjectTypeCode()).isEqualTo("LOCAL_USER");
        assertThat(sent.subjectExternalId()).isEqualTo("42");
        assertThat(sent.resourceTypeCode()).isEqualTo("EXAMPLE");
        assertThat(sent.resourceCode()).isEqualTo("report-1");
        assertThat(sent.operationCode()).isEqualTo("VIEW");
    }

    @Test
    @DisplayName("TYPE_LEVEL 目标：resourceCode=null 原样透传（CREATE 不携带实例码）")
    void check_typeLevelTargetCarriesNullResourceCode() {
        when(client.checkAuth(any(AuthCheckReq.class), anyString(), anyString())).thenReturn(R.ok(AuthCheckResp.allow(List.of(), List.of(), false)));

        checker.check("7", "42", null, BusinessPermChecker.Target.of("EXAMPLE", null, "CREATE"));

        ArgumentCaptor<AuthCheckReq> captor = ArgumentCaptor.forClass(AuthCheckReq.class);
        verify(client).checkAuth(captor.capture(), eq("sc-7"), eq("sk-7"));
        assertThat(captor.getValue().resourceCode()).isNull();
        assertThat(captor.getValue().operationCode()).isEqualTo("CREATE");
    }

    @Test
    @DisplayName("depend_on 父上下文：真实父资源与父操作透传（引擎验证父授权绑定，N26 载体）")
    void check_childTargetCarriesParentContext() {
        when(client.checkAuth(any(AuthCheckReq.class), anyString(), anyString())).thenReturn(R.ok(AuthCheckResp.deny("DEPENDENT_NOT_IN_PARENT_CONTEXT")));

        BusinessPermChecker.Decision d = checker.check("7", "42", null,
            BusinessPermChecker.Target.childOf("EXAMPLE", "report-1-detail", "SUB_VIEW",
                "EXAMPLE", "report-1", List.of("VIEW")));

        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("DEPENDENT_NOT_IN_PARENT_CONTEXT");
        ArgumentCaptor<AuthCheckReq> captor = ArgumentCaptor.forClass(AuthCheckReq.class);
        verify(client).checkAuth(captor.capture(), eq("sc-7"), eq("sk-7"));
        AuthCheckReq sent = captor.getValue();
        assertThat(sent.parentResourceTypeCode()).isEqualTo("EXAMPLE");
        assertThat(sent.parentResourceCode()).isEqualTo("report-1");
        assertThat(sent.parentOperationCodes()).containsExactly("VIEW");
    }

    @Test
    @DisplayName("独立批量：一次 batch-check 调用，结果按 resourceCode 对齐（逐目标独立）")
    void batchCheck_alignsPerTargetResultsByCode() {
        when(client.batchCheckAuth(any(BatchAuthCheckReq.class), anyString(), anyString())).thenReturn(R.ok(new BatchAuthCheckResp(List.of(
            new BatchAuthCheckResp.AuthCheckItemResult("EXAMPLE", "report-1", "VIEW", true, null, List.of(1L), List.of(2L)),
            new BatchAuthCheckResp.AuthCheckItemResult("EXAMPLE", "report-2", "VIEW", false, "NO_PERMISSION", List.of(), List.of())))));

        Map<String, BusinessPermChecker.Decision> byCode =
            checker.batchCheck("7", "42", null, "EXAMPLE", List.of("report-1", "report-2"), "VIEW");

        assertThat(byCode.get("report-1").allowed()).isTrue();
        assertThat(byCode.get("report-2").allowed()).isFalse();
        assertThat(byCode.get("report-2").reason()).isEqualTo("NO_PERMISSION");
        ArgumentCaptor<BatchAuthCheckReq> captor = ArgumentCaptor.forClass(BatchAuthCheckReq.class);
        verify(client).batchCheckAuth(captor.capture(), eq("sc-7"), eq("sk-7"));
        assertThat(captor.getValue().items()).hasSize(2);
        assertThat(captor.getValue().subjectExternalId()).isEqualTo("42");
    }

    @Test
    @DisplayName("范围查询：INSTANCE 行业务码去重收集（列表过滤数据源）")
    void accessibleScope_collectsDistinctInstanceCodes() {
        when(client.queryResources(any(QueryResourcesReq.class), anyString(), anyString())).thenReturn(R.ok(new QueryResourcesResp(List.of(
            new QueryResourcesResp.ResourceEntry("EXAMPLE", "report-1", "default", "销售日报", false,
                cn.ac.fage.accessmesh.perm.common.enums.ScopeMode.INSTANCE, null, null),
            new QueryResourcesResp.ResourceEntry("EXAMPLE", "report-2", "default", "库存周报", false,
                cn.ac.fage.accessmesh.perm.common.enums.ScopeMode.INSTANCE, null, null)), 10)));

        BusinessPermChecker.Scope scope = checker.accessibleScope("7", "42", null, "EXAMPLE", "VIEW");

        assertThat(scope.all()).isFalse();
        assertThat(scope.codes()).containsExactlyInAnyOrder("report-1", "report-2");
        ArgumentCaptor<QueryResourcesReq> captor = ArgumentCaptor.forClass(QueryResourcesReq.class);
        verify(client).queryResources(captor.capture(), eq("sc-7"), eq("sk-7"));
        // 单类型×单操作显式构造，不触发笛卡尔组合
        assertThat(captor.getValue().resourceTypeCodes()).containsExactly("EXAMPLE");
        assertThat(captor.getValue().operationCodes()).containsExactly("VIEW");
    }

    @Test
    @DisplayName("范围查询：scopeMode=ALL 行（resourceCode=null）→ Scope.all 表达全量，不按码过滤成空集")
    void accessibleScope_allModeProjectsToAllFlag() {
        when(client.queryResources(any(QueryResourcesReq.class), anyString(), anyString())).thenReturn(R.ok(new QueryResourcesResp(List.of(
            new QueryResourcesResp.ResourceEntry("EXAMPLE", null, null, null, false,
                cn.ac.fage.accessmesh.perm.common.enums.ScopeMode.ALL, null, null)), 10)));

        BusinessPermChecker.Scope scope = checker.accessibleScope("7", "42", null, "EXAMPLE", "VIEW");

        // ALL=类型级全量授权：contains 对任意码为真（旧实现把 null 码收进集合，全量授权列表恒空）
        assertThat(scope.all()).isTrue();
        assertThat(scope.contains("report-1")).isTrue();
        assertThat(scope.contains("anything")).isTrue();
    }

    @Test
    @DisplayName("clientIp 透传：可信链 IP 经 context.clientIp 传引擎（缺省不传，IP 条件 fail-closed 归引擎）")
    void check_carriesClientIpInContext() {
        when(client.checkAuth(any(AuthCheckReq.class), anyString(), anyString())).thenReturn(R.ok(AuthCheckResp.allow(List.of(), List.of(), false)));

        checker.check("7", "42", "10.0.0.9", BusinessPermChecker.Target.of("EXAMPLE", "report-1", "VIEW"));
        checker.check("7", "42", null, BusinessPermChecker.Target.of("EXAMPLE", "report-1", "VIEW"));

        ArgumentCaptor<AuthCheckReq> captor = ArgumentCaptor.forClass(AuthCheckReq.class);
        verify(client, org.mockito.Mockito.times(2)).checkAuth(captor.capture(), eq("sc-7"), eq("sk-7"));
        // SDK 契约键=clientIp（CallerContext.KEY_CLIENT_IP 线格式，与网关 PermissionClient 同款）
        assertThat(captor.getAllValues().get(0).context()).containsEntry("clientIp", "10.0.0.9");
        assertThat(captor.getAllValues().get(1).context()).isNull();
    }

    @Test
    @DisplayName("fail-closed：信封 code≠200 / data=null / 传输异常一律 30005，不放行")
    void call_failsClosedOnUnavailable() {
        when(client.checkAuth(any(AuthCheckReq.class), anyString(), anyString()))
            .thenReturn(R.fail(500, "boom"))          // 信封非 200
            .thenReturn(R.ok(null))                    // data=null（服务端错误信封）
            .thenThrow(new RuntimeException("connect refused")); // 传输异常

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> checker.check("7", "42", null,
                    BusinessPermChecker.Target.of("EXAMPLE", "report-1", "VIEW")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(30005));
        }
    }

    @Test
    void shouldKeepConcurrentTenantCredentialsIndependent() throws Exception {
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        when(client.checkAuth(any(AuthCheckReq.class), anyString(), anyString())).thenAnswer(inv -> {
            AuthCheckReq req = inv.getArgument(0);
            String tenant = req.subjectExternalId().equals("42") ? "7" : "1";
            barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat((String) inv.getArgument(1)).isEqualTo("sc-" + tenant);
            assertThat((String) inv.getArgument(2)).isEqualTo("sk-" + tenant);
            return R.ok(AuthCheckResp.allow(List.of(), List.of(), false));
        });
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> checker.check("7", "42", null,
                BusinessPermChecker.Target.of("EXAMPLE", "report-1", "VIEW")));
            var second = executor.submit(() -> checker.check("1", "99", null,
                BusinessPermChecker.Target.of("EXAMPLE", "report-1", "VIEW")));
            assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS).allowed()).isTrue();
            assertThat(second.get(10, java.util.concurrent.TimeUnit.SECONDS).allowed()).isTrue();
        }
    }

    @Test
    void shouldChooseCapturedTenantCredential_onExportWorker() throws Exception {
        var checked = new java.util.concurrent.CountDownLatch(2);
        when(client.checkAuth(any(AuthCheckReq.class), anyString(), anyString())).thenAnswer(inv -> {
            AuthCheckReq req = inv.getArgument(0);
            String tenant = req.subjectExternalId().equals("42") ? "7" : "1";
            assertThat(req.operationCode()).isEqualTo("EXPORT");
            assertThat((String) inv.getArgument(1)).isEqualTo("sc-" + tenant);
            assertThat((String) inv.getArgument(2)).isEqualTo("sk-" + tenant);
            checked.countDown();
            return R.ok(AuthCheckResp.allow(List.of(), List.of(), false));
        });
        var runner = new cn.ac.fage.accessmesh.example.report.ExportJobRunner(checker,
            new cn.ac.fage.accessmesh.example.report.ReportStore(), 0L);
        try {
            runner.submit("report-1", "7", "42", null);
            runner.submit("report-1", "1", "99", null);
            assertThat(checked.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        } finally {
            // 执行生命周期清理，不调用私有业务方法。
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(runner, "shutdown");
        }
    }
}
