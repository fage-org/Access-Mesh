package cn.ac.fage.accessmesh.access.application.bootstrap;

import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;

import java.util.List;

/**
 * 空库 bootstrap 固定图定义（T-ACCESS-020，定稿见 access-service-architecture.md §14.2/§14.3/§14.4）。
 * <p>
 * 纯常量：固定租户 1、首管理员与管理用功能角色的稳定业务键、bootstrap 管理 API 清单
 * （含目标接口）、业务门禁最小集。幂等三状态检测与单事务创建均以本定义为唯一事实源，
 * 禁止在检测/创建两侧各自维护清单。
 * </p>
 * <p>
 * API 资源 {@code resource_entity(API).code = "{METHOD}:{外部路径}"}（外部路径含 Gateway 路由
 * 前缀 /perm、/admin——Gateway PermissionFilter 以原始请求路径匹配 resource_api_mapping.path_pattern）；
 * 目标接口 {@code POST /admin/role/my-info} 仅预建资源并预授 API:ACCESS+canGrant，不建映射
 * （映射由 E2E 真实创建，T-ACCESS-021）。
 * </p>
 */
public final class BootstrapGraphDefinition {

    private BootstrapGraphDefinition() {}

    /** 首期固定租户（类型种子即租户 1，不做租户开通） */
    public static final Long TENANT_ID = 1L;

    /** 首管理员稳定业务键 */
    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_NAME = "Bootstrap Admin";

    /** 管理用功能角色稳定业务键（BASIC_ROLE / 全局域，domainCode=null） */
    public static final String ADMIN_ROLE_TYPE_CODE = "BASIC_ROLE";
    public static final String ADMIN_ROLE_EXTERNAL_ID = "bootstrap-admin";
    public static final String ADMIN_ROLE_NAME = "Bootstrap Admin";

    /** SERVICE 资源（固定图种子对象，DDL 无种子、本地投影不产出；MANAGE_API_MAPPING 已类型级，保留供未来实例级授权） */
    public static final String SERVICE_RESOURCE_TYPE_CODE = ResourceTypeCode.SERVICE;
    public static final String SERVICE_RESOURCE_CODE = "access-service";
    public static final String SERVICE_RESOURCE_NAME = "access-service";

    /**
     * resource_api_mapping.service_code（Gateway 路由 metadata 同值，快照查询按此过滤）与
     * resource_entity.owner_service_code。
     */
    public static final String API_SERVICE_CODE = "access-service";

    /**
     * bootstrap 管理 API 清单条目。
     *
     * @param method         HTTP 方法
     * @param path           外部路径（Gateway 视角，含 /perm、/admin 前缀）
     * @param name           资源显示名（授权页资源树可见）
     * @param withMapping    是否预建 resource_api_mapping（目标接口 false，映射归 E2E 真实创建）
     * @param grantCanGrant  该 API 实例授权是否携带 canGrant=true（仅目标接口，授权传递用）
     */
    public record ApiRoute(String method, String path, String name,
                           boolean withMapping, boolean grantCanGrant) {}

    /**
     * 业务门禁授权条目（Gateway 层 API:ACCESS 实例授权由 {@link #apiRoutes()} 派生，不在此列）。
     *
     * @param resourceTypeCode 资源类型码
     * @param operationCode    操作码
     * @param resourceCode     实例授权的资源编码；null 表示 scopeAll
     * @param canGrant         是否可转授（bootstrap 固定图业务门禁均不可转授）
     */
    public record GrantSpec(String resourceTypeCode, String operationCode,
                            String resourceCode, boolean canGrant) {}

    /**
     * bootstrap 管理 API 清单（§14.3；含目标接口，计数以清单本身为准）。
     */
    public static List<ApiRoute> apiRoutes() {
        return List.of(
            new ApiRoute("POST", "/perm/api/perm/abstract-role/tree", "bootstrap:授权页角色树", true, false),
            new ApiRoute("POST", "/perm/api/perm/type-definition/list", "bootstrap:授权页类型定义列表", true, false),
            new ApiRoute("POST", "/perm/api/perm/resource-entity/tree", "bootstrap:授权页资源树", true, false),
            new ApiRoute("POST", "/perm/api/perm/operation-permission/list", "bootstrap:授权页操作列表", true, false),
            new ApiRoute("POST", "/perm/api/perm/permission-condition/list", "bootstrap:授权页条件列表", true, false),
            new ApiRoute("POST", "/perm/api/perm/role-resource-permission/list", "bootstrap:授权页既有授权查询", true, false),
            new ApiRoute("POST", "/perm/api/perm/role-resource-permission/sub-perm-allowed-types", "bootstrap:授权页子权限类型查询", true, false),
            new ApiRoute("POST", "/admin/user/create", "bootstrap:创建用户", true, false),
            new ApiRoute("POST", "/perm/api/perm/abstract-role/create", "bootstrap:创建角色", true, false),
            new ApiRoute("POST", "/perm/api/perm/resource-api-mapping/create", "bootstrap:创建API映射", true, false),
            new ApiRoute("POST", "/perm/api/perm/role-resource-permission/apply-grant-plan", "bootstrap:授权与回收", true, false),
            new ApiRoute("POST", "/perm/api/perm/user-role/assign", "bootstrap:分配角色", true, false),
            // 目标接口（§14.6）：仅预建资源 + API:ACCESS+canGrant，不建映射
            new ApiRoute("POST", "/admin/role/my-info", "bootstrap:目标接口(my-info)", false, true));
    }

    /**
     * 业务门禁最小集（§14.4；ROLE:MANAGE 掩码已含 VIEW，不重复授 ROLE:VIEW）。
     */
    public static List<GrantSpec> businessGrants() {
        return List.of(
            new GrantSpec(ResourceTypeCode.USER, OperationCodeConstants.CREATE, null, false),
            new GrantSpec(ResourceTypeCode.ROLE, OperationCodeConstants.CREATE, null, false),
            new GrantSpec(ResourceTypeCode.ROLE, OperationCodeConstants.MANAGE, null, false),
            // T-API-001：类型级（scopeAll）——接入新服务（如 example-service）的首条 API 映射
            // 创建必须由首管理员完成，实例级会造成鸡生蛋（无正规入口补授新服务实例）
            new GrantSpec(ResourceTypeCode.SERVICE, OperationCodeConstants.MANAGE_API_MAPPING,
                null, false),
            new GrantSpec(ResourceTypeCode.TYPE_DEFINITION, OperationCodeConstants.VIEW, null, false),
            new GrantSpec(ResourceTypeCode.RESOURCE, OperationCodeConstants.VIEW, null, false),
            new GrantSpec(ResourceTypeCode.OPERATION, OperationCodeConstants.VIEW, null, false),
            // T-PERM-025 审计分离：操作日志查询独立门禁——新权限码需固定图持否则无授予起点（死锁）
            new GrantSpec(ResourceTypeCode.OPERATION_LOG, OperationCodeConstants.VIEW, null, false),
            // T-API-001：类型级 API:ACCESS + canGrant——新接入服务接口的授权必须由首管理员完成，
            // 实例级（仅清单内管理接口）会造成鸡生蛋（无正规入口给新接口授权）。
            // ACCESS 为网关接口鉴权专用操作码（api-contract/DDL 运行时种子），此处按契约字符串声明
            new GrantSpec(ResourceTypeCode.API, "ACCESS", null, true));
    }

    /**
     * Gateway 层实例级 API:ACCESS 授权（由管理 API 清单派生）。
     * ACCESS 为网关接口鉴权专用操作码（api-contract/DDL 运行时种子），OperationCodeConstants
     * 未收录该码，此处按契约字符串声明。
     */
    public static List<GrantSpec> apiAccessGrants() {
        return apiRoutes().stream()
            .map(route -> new GrantSpec(ResourceTypeCode.API, "ACCESS",
                apiResourceCode(route.method(), route.path()), route.grantCanGrant()))
            .toList();
    }

    /** 全部固定图授权（业务门禁 + 实例级 API:ACCESS；计数以 AccessBootstrapPgIT 断言为准） */
    public static List<GrantSpec> allGrants() {
        return java.util.stream.Stream.concat(businessGrants().stream(), apiAccessGrants().stream()).toList();
    }

    /** API 资源稳定业务键 */
    public static String apiResourceCode(String method, String path) {
        return method + ":" + path;
    }
}
