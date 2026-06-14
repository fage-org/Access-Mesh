package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 用户组织关联数据访问接口
 * <p>
 * 提供用户组织关联表的基础CRUD操作。
 * 用户组织关联定义用户与组织的绑定关系，支持用户加入多个组织。
 * </p>
 */
@Mapper
public interface SysUserOrgMapper extends BaseMapper<SysUserOrg> {

    /**
     * 根据用户ID列表和租户ID查询用户组织关联
     *
     * @param tenantId 租户ID
     * @param userIds  用户ID列表
     * @return 用户组织关联列表
     */
    List<SysUserOrg> selectByUserIdsAndTenant(@Param("tenantId") Long tenantId,
                                              @Param("userIds") List<Long> userIds);

    /**
     * 根据单个用户ID和租户ID查询用户组织关联
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 用户组织关联列表
     */
    List<SysUserOrg> selectByUserIdAndTenant(@Param("tenantId") Long tenantId,
                                             @Param("userId") Long userId);

    /**
     * 删除用户的所有组织关联（物理删除，租户隔离）
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 删除行数
     */
    int deleteByUserId(@Param("tenantId") Long tenantId,
                       @Param("userId") Long userId);

    /**
     * 删除用户与指定组织的关联（物理删除，租户隔离）
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param orgId    组织ID
     * @return 删除行数
     */
    int deleteByUserIdAndOrgId(@Param("tenantId") Long tenantId,
                               @Param("userId") Long userId,
                               @Param("orgId") Long orgId);

    /**
     * 将指定组织设为用户的主组织（租户隔离 + 未删除）
     *
     * @param tenantId  租户ID
     * @param userId    用户ID
     * @param orgId     组织ID
     * @param isPrimary 是否主组织
     * @param updatedAt 更新时间
     * @return 更新行数
     */
    int updatePrimaryByUserIdAndOrgId(@Param("tenantId") Long tenantId,
                                      @Param("userId") Long userId,
                                      @Param("orgId") Long orgId,
                                      @Param("isPrimary") Boolean isPrimary,
                                      @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 将用户的其他组织设为非主组织（租户隔离 + 未删除，排除指定组织）
     *
     * @param tenantId  租户ID
     * @param userId    用户ID
     * @param orgId     排除的组织ID
     * @param isPrimary 是否主组织
     * @param updatedAt 更新时间
     * @return 更新行数
     */
    int updateNonPrimaryByUserIdExclOrgId(@Param("tenantId") Long tenantId,
                                          @Param("userId") Long userId,
                                          @Param("orgId") Long orgId,
                                          @Param("isPrimary") Boolean isPrimary,
                                          @Param("updatedAt") LocalDateTime updatedAt);

    int updatePrimaryByUserIdAndOrgIds(@Param("tenantId") Long tenantId,
                                       @Param("userId") Long userId,
                                       @Param("orgIds") List<Long> orgIds,
                                       @Param("isPrimary") Boolean isPrimary,
                                       @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 批量查询多个用户的组织关联（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @param userIds  用户ID集合
     * @return 用户组织关联列表
     */
    List<SysUserOrg> selectByUserIdsSet(@Param("tenantId") Long tenantId,
                                        @Param("userIds") Set<Long> userIds);

    /**
     * 根据组织 ID 列表和租户 ID 查询用户组织关联（租户隔离 + 未删除）。
     * <p>
     * 用于候选用户查询：获取默认树可见范围内的所有用户 ID。
     *
     * @param tenantId 租户ID
     * @param orgIds   组织ID列表
     * @return 用户组织关联列表
     */
    List<SysUserOrg> selectByOrgIdsAndTenant(@Param("tenantId") Long tenantId,
                                              @Param("orgIds") List<Long> orgIds);
}
