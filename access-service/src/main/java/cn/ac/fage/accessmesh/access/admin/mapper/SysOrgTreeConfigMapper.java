package cn.ac.fage.accessmesh.access.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrgTreeConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 组织树配置数据访问接口
 * <p>
 * 提供组织树配置表的基础CRUD操作和自定义查询方法。
 * 组织树配置定义组织层级结构和展示规则。
 * 支持批量软删除操作。
 * </p>
 */
@Mapper
public interface SysOrgTreeConfigMapper extends BaseMapper<SysOrgTreeConfig> {

    /**
     * 批量软删除组织树配置
     * <p>
     * 将指定配置的delete_flag设置为id（行自身ID），deleted_at设置为当前时间。
     * 包含租户ID过滤，确保租户隔离。
     * </p>
     *
     * @param tenantId  租户ID（必传）
     * @param ids       待删除的配置ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 根据主键ID安全查询配置（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @param id       主键ID
     * @return 配置实体，不存在则返回null
     */
    SysOrgTreeConfig selectByIdSafe(@Param("tenantId") Long tenantId,
                                    @Param("id") Long id);

    /**
     * 分页查询配置列表（租户隔离 + 未删除，按创建时间倒序）
     *
     * @param tenantId 租户ID
     * @param offset   分页偏移量
     * @param limit    分页大小
     * @return 分页结果
     */
    // XML 分页统一 offset/limit + count 双查询（MyBatis-Flex Page 参数在 XML 映射下不生效，
    // T-FE-015 联调发现——与 SysUserMapper/SysOrgMapper 同款修法）
    List<SysOrgTreeConfig> selectAllByTenant(@Param("tenantId") Long tenantId,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    /**
     * 统计租户下配置总数（用于分页计算）
     */
    long countAllByTenant(@Param("tenantId") Long tenantId);

    /**
     * 查询当前租户下所有默认配置（租户隔离 + 未删除 + is_default=true）
     *
     * @param tenantId 租户ID
     * @return 默认配置列表
     */
    List<SysOrgTreeConfig> selectDefaultConfigs(@Param("tenantId") Long tenantId);

    /**
     * 查询当前租户下所有有效配置（租户隔离 + 未删除，不限 is_default）
     * <p>
     * 用于解析任意 orgId 所属组织树根：user-org 关系适用于任何已配置的树（不限默认树），
     * 因此 root 解析需要遍历该租户全部 SysOrgTreeConfig.rootOrgId 集合。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 全部有效配置列表
     */
    List<SysOrgTreeConfig> selectAllValid(@Param("tenantId") Long tenantId);

    /**
     * 清除当前租户下所有配置的默认标记（租户隔离 + 未删除 + is_default=true）
     *
     * @param tenantId   租户ID
     * @param updatedAt  更新时间
     * @return 更新的行数
     */
    int clearAllDefaults(@Param("tenantId") Long tenantId,
                         @Param("updatedAt") LocalDateTime updatedAt);
}