package cn.ac.fage.accessmesh.access.infrastructure.credential.mapper;

import cn.ac.fage.accessmesh.access.infrastructure.credential.entity.ServiceCredential;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务凭证数据访问接口（T-PERM-070）。
 * <p>
 * credential_id 为<b>全局唯一</b>定位键（跨租户——认证链先按 credential_id 定位凭证行、
 * 再由行派生 tenantId，租户条件在定位之后才可知）；「该服务有效凭证」查询按
 * (tenant_id, service_code) 过滤。
 * </p>
 */
public interface ServiceCredentialMapper extends BaseMapper<ServiceCredential> {

    /**
     * 按 credential_id 全局定位有效凭证（delete_flag=0；含停用/过期行——
     * 认证链须先定位再判定状态，以区分 20065/20066/20067 三态）。
     */
    ServiceCredential selectByCredentialId(@Param("credentialId") String credentialId);

    /** 按主键定位有效凭证（管理面 update/remove 前置定位）。 */
    ServiceCredential selectByIdAndTenant(@Param("tenantId") Long tenantId,
                                          @Param("id") Long id);

    /** 该服务的有效凭证列表（delete_flag=0，含停用/过期行，轮换状态可见）。 */
    List<ServiceCredential> selectByTenantAndServiceCode(@Param("tenantId") Long tenantId,
                                                         @Param("serviceCode") String serviceCode);

    /** 租户全部有效凭证（serviceCode 过滤在服务层做或传 null 全量；管理面列表）。 */
    List<ServiceCredential> selectByTenantId(@Param("tenantId") Long tenantId);

    /** 软删除（delete_flag=id、deleted_at/deleted_by 审计）。 */
    int softDelete(@Param("tenantId") Long tenantId,
                   @Param("id") Long id,
                   @Param("deletedBy") Long deletedBy,
                   @Param("deletedAt") LocalDateTime deletedAt);

    /** 状态更新（delete_flag=0 条件；rotatedAt 仅停用时写入，启用传 null 不更新该列）。 */
    int updateStatus(@Param("tenantId") Long tenantId,
                     @Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("rotatedAt") LocalDateTime rotatedAt,
                     @Param("updatedBy") Long updatedBy,
                     @Param("updatedAt") LocalDateTime updatedAt);

    /** 过期时间更新（delete_flag=0 条件；仅改期语义，清除不提供）。 */
    int updateExpiresAt(@Param("tenantId") Long tenantId,
                        @Param("id") Long id,
                        @Param("expiresAt") LocalDateTime expiresAt,
                        @Param("updatedBy") Long updatedBy,
                        @Param("updatedAt") LocalDateTime updatedAt);
}
