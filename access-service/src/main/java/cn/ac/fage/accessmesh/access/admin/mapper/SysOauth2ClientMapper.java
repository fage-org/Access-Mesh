package cn.ac.fage.accessmesh.access.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.admin.entity.SysOauth2Client;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OAuth2客户端数据访问接口
 * <p>
 * 提供OAuth2客户端表的基础CRUD操作和自定义查询方法。
 * OAuth2客户端用于第三方应用接入认证。
 * 支持批量软删除操作。
 * </p>
 */
@Mapper
public interface SysOauth2ClientMapper extends BaseMapper<SysOauth2Client> {

    /**
     * 批量软删除OAuth2客户端
     * <p>
     * 将指定客户端的delete_flag设置为id（行自身ID），deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * 包含租户ID过滤，确保租户隔离。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的客户端ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 根据主键ID安全查询客户端（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @param id       主键ID
     * @return 客户端实体，不存在则返回null
     */
    SysOauth2Client selectByIdSafe(@Param("tenantId") Long tenantId,
                                   @Param("id") Long id);

    /**
     * 根据客户端ID查询客户端（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @param clientId OAuth2客户端ID（非主键）
     * @return 客户端实体，不存在则返回null
     */
    SysOauth2Client selectByClientId(@Param("tenantId") Long tenantId,
                                     @Param("clientId") String clientId);

    /**
     * 按条件分页查询客户端列表（租户隔离 + 未删除，可选过滤条件）
     * <p>
     * XML 分页统一 offset/limit + count 双查询（仓库既定模式，见 SysUserMapper；
     * MyBatis-Flex 的 Page 参数在 XML 映射下不生效——selectOne 多行异常，
     * T-FE-022 冒烟实证有行即 TooManyResultsException 500，T-ADMIN-026 收口）
     * </p>
     *
     * @param tenantId   租户ID
     * @param clientName 客户端名称（可选，模糊匹配）
     * @param status     状态（可选，精确匹配）
     * @param offset     偏移量
     * @param limit      每页条数
     * @return 客户端列表（当前页）
     */
    List<SysOauth2Client> selectClientsByCondition(@Param("tenantId") Long tenantId,
                                                   @Param("clientName") String clientName,
                                                   @Param("status") Integer status,
                                                   @Param("offset") int offset,
                                                   @Param("limit") int limit);

    /**
     * 按条件统计客户端数（条件与 {@link #selectClientsByCondition} 一致，用于分页计算）
     */
    long countClientsByCondition(@Param("tenantId") Long tenantId,
                                 @Param("clientName") String clientName,
                                 @Param("status") Integer status);

    /**
     * 根据客户端ID查询启用状态的客户端（未删除 + 已启用）
     *
     * @param clientId OAuth2客户端ID（非主键）
     * @return 客户端实体，不存在则返回null
     */
    SysOauth2Client selectActiveByClientId(@Param("clientId") String clientId);
}