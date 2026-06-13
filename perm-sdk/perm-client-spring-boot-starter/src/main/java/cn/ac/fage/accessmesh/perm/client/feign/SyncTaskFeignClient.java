package cn.ac.fage.accessmesh.perm.client.feign;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 同步任务 Feign 客户端
 * <p>
 * admin-service S5 调度器使用本客户端调用 permission-center 的
 * 8 个 sync/full-sync 端点。请求体使用 {@link Map}（admin-service 已序列化好的 JSON
 * 反序列化为 Map），避免在 perm-sdk 重复声明各 sync 接口的强类型 DTO。
 * </p>
 * <p>
 * Header 注入约定：
 * <ul>
 *   <li>{@code X-Tenant-Id}：由调用方业务侧的 Feign 拦截器（如 admin-service 的
 *       {@code FeignTenantInterceptor}）从 ThreadLocal 注入；调度端在
 *       {@code processOne} 中按任务 {@code tenantId} 设置 {@code TenantContextHolder}。</li>
 *   <li>{@code X-Service-Code} + {@code X-Internal-Secret}：由 perm-sdk 提供的
 *       {@link FeignInternalSyncInterceptor} 统一注入，无需在方法签名声明。</li>
 * </ul>
 * 因此本接口的方法签名禁止声明 {@code @RequestHeader("X-Service-Code")}。
 * </p>
 */
@FeignClient(name = "permission-center", contextId = "syncTaskFeignClient")
public interface SyncTaskFeignClient {

    // ========== abstract-user ==========

    /** 抽象用户增量同步：UPSERT / DISABLE / DELETE。 */
    @PostMapping("/api/perm/abstract-user/sync")
    PermResult<SyncResultResp> syncAbstractUser(@RequestBody Map<String, Object> payload);

    /** 抽象用户全量同步：scope 内未出现的业务键自动 DELETE。 */
    @PostMapping("/api/perm/abstract-user/full-sync")
    PermResult<SyncResultResp> fullSyncAbstractUser(@RequestBody Map<String, Object> payload);

    // ========== abstract-role ==========

    /** 抽象角色增量同步。 */
    @PostMapping("/api/perm/abstract-role/sync")
    PermResult<SyncResultResp> syncAbstractRole(@RequestBody Map<String, Object> payload);

    /** 抽象角色全量同步。 */
    @PostMapping("/api/perm/abstract-role/full-sync")
    PermResult<SyncResultResp> fullSyncAbstractRole(@RequestBody Map<String, Object> payload);

    // ========== user-role ==========

    /** 用户-角色绑定增量同步。 */
    @PostMapping("/api/perm/user-role/sync")
    PermResult<SyncResultResp> syncUserRole(@RequestBody Map<String, Object> payload);

    /** 用户-角色绑定全量同步。 */
    @PostMapping("/api/perm/user-role/full-sync")
    PermResult<SyncResultResp> fullSyncUserRole(@RequestBody Map<String, Object> payload);

    // ========== resource-entity ==========

    /** 资源实体增量同步。 */
    @PostMapping("/api/perm/resource-entity/sync")
    PermResult<SyncResultResp> syncResourceEntity(@RequestBody Map<String, Object> payload);

    /** 资源实体全量同步。 */
    @PostMapping("/api/perm/resource-entity/full-sync")
    PermResult<SyncResultResp> fullSyncResourceEntity(@RequestBody Map<String, Object> payload);
}
