package cn.ac.fage.accessmesh.access.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SDK 直连端点线格式字段快照（T-API-002 回归锁）。
 * <p>
 * core-flows §15「SDK 四件套（check / batch-check / query-resources / query-scopes，
 * 含 Gateway 复用的 check-interface）不要求/不泄漏内部数据库 ID」的代码级钉死：
 * record 组件名快照精确比对，任何字段增删/改名都必须显式更新本清单并过设计评审。
 * </p>
 * <p>
 * check 族三个响应 DTO 在 access-service（服务端）与 perm-common（SDK）各有一份副本，
 * 双副本任意一侧漂移即破坏 HTTP 契约——本测试同时钉死两副本的组件名清单与互相同形。
 * </p>
 */
class CheckFamilyWireShapeTest {

    /** 内部数据库 id 字段族：禁止再出现在 SDK 直连端点线格式（防泄漏回潮）。 */
    private static final List<String> RETIRED_ID_FIELDS = List.of(
        "matchedRoleIds", "matchedPermissionIds", "dependOnPermissionIds",
        "parentPermissionIds", "resourceId");

    private static List<String> components(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();
    }

    @Test
    @DisplayName("query-resources/query-scopes 线格式字段快照（T-API-002 裁剪后终态）")
    void queryWireShapesAreFrozen() {
        assertThat(components(cn.ac.fage.accessmesh.perm.common.dto.req.QueryResourcesReq.class))
            .containsExactly("subjectTypeCode", "subjectExternalId", "resourceTypeCodes",
                "operationCodes", "domainCode", "codeType", "includeInherited",
                "includeChildren", "context");
        assertThat(components(cn.ac.fage.accessmesh.perm.common.dto.resp.QueryResourcesResp.class))
            .containsExactly("items", "cacheTtlSeconds");
        assertThat(components(cn.ac.fage.accessmesh.perm.common.dto.resp.QueryResourcesResp.ResourceEntry.class))
            .containsExactly("resourceTypeCode", "resourceCode", "codeType", "resourceName",
                "canGrant", "scopeMode", "operations", "grantSources");

        assertThat(components(cn.ac.fage.accessmesh.perm.common.dto.req.QueryScopesReq.class))
            .containsExactly("subjectTypeCode", "subjectExternalId", "parentResourceTypeCode",
                "parentResourceCode", "parentCodeType", "parentOperationCodes",
                "scopeResourceTypeCodes", "scopeOperationCodes", "scopeCodeType",
                "domainCode", "context");
        assertThat(components(cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp.class))
            .containsExactly("reason", "matchedParentOperations", "scopeGroups", "cacheTtlSeconds");
        assertThat(components(cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp.ScopeGroup.class))
            .containsExactly("resourceTypeCode", "operationCode", "scopeMode", "items");
        assertThat(components(cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp.ScopeItem.class))
            .containsExactly("resourceCode", "codeType", "resourceName");
    }

    @Test
    @DisplayName("check 族线格式字段快照（T-API-002 用户决策扩大裁剪后终态）")
    void checkWireShapesAreFrozen() {
        assertThat(components(cn.ac.fage.accessmesh.access.permission.dto.resp.AuthCheckResp.class))
            .containsExactly("allowed", "reason", "conditionEvaluated");
        assertThat(components(cn.ac.fage.accessmesh.access.permission.dto.resp.BatchAuthCheckResp.class))
            .containsExactly("items");
        assertThat(components(cn.ac.fage.accessmesh.access.permission.dto.resp.BatchAuthCheckResp.AuthCheckItemResult.class))
            .containsExactly("resourceTypeCode", "resourceCode", "operationCode", "allowed", "reason");
        assertThat(components(cn.ac.fage.accessmesh.access.permission.dto.resp.CheckInterfaceResp.class))
            .containsExactly("allowed", "reason", "matchedResources", "cacheTtlSeconds");
        assertThat(components(cn.ac.fage.accessmesh.access.permission.dto.resp.CheckInterfaceResp.MatchedResource.class))
            .containsExactly("resourceTypeCode", "resourceCode", "operationCode", "allowed");
    }

    @Test
    @DisplayName("check 族双副本同形：access-service 与 perm-common 副本组件名完全一致（HTTP 契约等价）")
    void checkFamilyDualCopiesStayShapeEqual() {
        assertThat(components(cn.ac.fage.accessmesh.access.permission.dto.resp.AuthCheckResp.class))
            .as("AuthCheckResp 双副本漂移会破坏 SDK 消费方反序列化")
            .isEqualTo(components(cn.ac.fage.accessmesh.perm.common.dto.resp.AuthCheckResp.class));
        assertThat(components(cn.ac.fage.accessmesh.access.permission.dto.resp.BatchAuthCheckResp.class))
            .as("BatchAuthCheckResp 双副本漂移会破坏 SDK 消费方反序列化")
            .isEqualTo(components(cn.ac.fage.accessmesh.perm.common.dto.resp.BatchAuthCheckResp.class));
        assertThat(components(cn.ac.fage.accessmesh.access.permission.dto.resp.BatchAuthCheckResp.AuthCheckItemResult.class))
            .isEqualTo(components(cn.ac.fage.accessmesh.perm.common.dto.resp.BatchAuthCheckResp.AuthCheckItemResult.class));
        assertThat(components(cn.ac.fage.accessmesh.access.permission.dto.resp.CheckInterfaceResp.class))
            .as("CheckInterfaceResp 双副本漂移会破坏 Gateway 反序列化")
            .isEqualTo(components(cn.ac.fage.accessmesh.perm.common.dto.resp.CheckInterfaceResp.class));
        assertThat(components(cn.ac.fage.accessmesh.access.permission.dto.resp.CheckInterfaceResp.MatchedResource.class))
            .isEqualTo(components(cn.ac.fage.accessmesh.perm.common.dto.resp.CheckInterfaceResp.MatchedResource.class));
    }

    @Test
    @DisplayName("内部 id 字段族禁止回潮：SDK 直连端点全部响应 DTO 不得再声明任何内部行 id 组件")
    void retiredIdFieldsMustNotResurface() {
        List<Class<?>> wireRecords = Stream.<Class<?>>of(
                cn.ac.fage.accessmesh.perm.common.dto.resp.QueryResourcesResp.class,
                cn.ac.fage.accessmesh.perm.common.dto.resp.QueryResourcesResp.ResourceEntry.class,
                cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp.class,
                cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp.ScopeGroup.class,
                cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp.ScopeItem.class,
                cn.ac.fage.accessmesh.access.permission.dto.resp.AuthCheckResp.class,
                cn.ac.fage.accessmesh.perm.common.dto.resp.AuthCheckResp.class,
                cn.ac.fage.accessmesh.access.permission.dto.resp.BatchAuthCheckResp.class,
                cn.ac.fage.accessmesh.perm.common.dto.resp.BatchAuthCheckResp.class,
                cn.ac.fage.accessmesh.access.permission.dto.resp.BatchAuthCheckResp.AuthCheckItemResult.class,
                cn.ac.fage.accessmesh.perm.common.dto.resp.BatchAuthCheckResp.AuthCheckItemResult.class,
                cn.ac.fage.accessmesh.access.permission.dto.resp.CheckInterfaceResp.class,
                cn.ac.fage.accessmesh.perm.common.dto.resp.CheckInterfaceResp.class,
                cn.ac.fage.accessmesh.access.permission.dto.resp.CheckInterfaceResp.MatchedResource.class,
                cn.ac.fage.accessmesh.perm.common.dto.resp.CheckInterfaceResp.MatchedResource.class)
            .toList();
        for (Class<?> record : wireRecords) {
            assertThat(components(record))
                .as("%s 不得再声明内部数据库 id 字段（core-flows §15 口径）", record.getName())
                .doesNotContainAnyElementsOf(RETIRED_ID_FIELDS);
        }
    }
}
