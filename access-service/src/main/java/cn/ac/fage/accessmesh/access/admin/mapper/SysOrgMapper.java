package cn.ac.fage.accessmesh.access.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrg;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 系统组织数据访问接口
 * <p>
 * 提供组织表的基础CRUD操作和自定义查询方法。
 * 支持批量软删除、子孙组织递归查询等操作。
 * </p>
 */
@Mapper
public interface SysOrgMapper extends BaseMapper<SysOrg> {

    /**
     * 分页查询组织列表
     *
     * @param page     分页参数
     * @param tenantId 租户ID
     * @param orgName  组织名称模糊过滤，可选
     * @param orgType  组织类型过滤，可选
     * @param status   状态过滤，可选
     * @return 分页结果
     */
    // XML 分页统一 offset/limit + count 双查询（MyBatis-Flex Page 参数在 XML 映射下不生效，
    // T-FE-015 联调发现——与 SysUserMapper 同款修法）
    List<SysOrg> selectOrgsByCondition(@Param("tenantId") Long tenantId,
                                       @Param("orgName") String orgName,
                                       @Param("orgType") String orgType,
                                       @Param("status") Integer status,
                                       @Param("orgIds") Set<Long> orgIds,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    /**
     * 按条件统计组织数（条件与 {@link #selectOrgsByCondition} 一致，用于分页计算）
     */
    long countOrgsByCondition(@Param("tenantId") Long tenantId,
                              @Param("orgName") String orgName,
                              @Param("orgType") String orgType,
                              @Param("status") Integer status,
                              @Param("orgIds") Set<Long> orgIds);

    /**
     * 查询组织列表（用于构建树）
     *
     * @param tenantId 租户ID
     * @param orgType  组织类型过滤，可选
     * @param status   状态过滤，可选
     * @return 组织列表
     */
    List<SysOrg> selectOrgsForTree(@Param("tenantId") Long tenantId,
                                   @Param("orgType") String orgType,
                                   @Param("status") Integer status);

    /**
     * 批量软删除组织
     * <p>
     * 将指定组织的delete_flag设置为id（行自身ID），deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的组织ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 使用PostgreSQL CTE递归查询获取所有子孙组织ID（不含自身）
     * <p>
     * 从指定组织开始，递归查询所有子孙组织的ID。
     * 用于级联删除和权限计算场景。
     * </p>
     *
     * @param tenantId 租户ID
     * @param orgId    组织ID
     * @return 子孙组织ID列表
     */
    List<Long> selectDescendantIds(@Param("tenantId") Long tenantId,
                                   @Param("orgId") Long orgId);

    /**
     * 使用PostgreSQL CTE递归查询获取所有子孙组织ID（含自身）
     * <p>
     * 从指定组织开始，递归查询所有子孙组织的ID，包含自身。
     * 用于批量操作场景，确保自身也被包含。
     * </p>
     *
     * @param tenantId 租户ID
     * @param orgId    组织ID
     * @return 子孙组织ID列表（包含自身）
     */
    List<Long> selectDescendantIdsIncludingSelf(@Param("tenantId") Long tenantId,
                                                @Param("orgId") Long orgId);

    /**
     * 使用PostgreSQL CTE递归查询批量获取多个组织的所有子孙组织ID
     * <p>
     * 从多个组织开始，递归查询所有子孙组织的ID。
     * 返回DescendantResult对象列表，映射每个组织ID到其子孙ID。
     * 用于批量级联操作场景，提高查询效率。
     * </p>
     *
     * @param tenantId 租户ID
     * @param orgIds   组织ID集合
     * @return 子孙查询结果列表（org_id, descendant_id对）
     */
    List<DescendantResult> selectBatchDescendantIds(@Param("tenantId") Long tenantId,
                                                    @Param("orgIds") Set<Long> orgIds);

    /**
     * 批量子孙查询结果类
     * <p>
     * 用于封装批量子孙查询的结果，包含组织ID和子孙ID对。
     * </p>
     */
    @Getter
    @Setter
    class DescendantResult {
        private Long orgId;
        private Long descendantId;
    }

    /**
     * 根据主键ID查询有效组织（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @param orgId    组织ID
     * @return 组织实体，不存在则返回null
     */
    SysOrg selectValidById(@Param("tenantId") Long tenantId,
                           @Param("orgId") Long orgId);

    /**
     * 根据组织ID集合批量查询有效组织（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @param orgIds   组织ID集合
     * @return 组织实体列表
     */
    List<SysOrg> selectValidByIds(@Param("tenantId") Long tenantId,
                                  @Param("orgIds") Set<Long> orgIds);

    /**
     * 根据组织ID集合查询组织（租户隔离 + 未删除），用于祖先链加载
     *
     * @param tenantId 租户ID
     * @param orgIds   组织ID集合
     * @return 组织实体列表
     */
    List<SysOrg> selectByIdsForAncestors(@Param("tenantId") Long tenantId,
                                         @Param("orgIds") Set<Long> orgIds);

    /**
     * 统计指定组织的子组织数量（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @param orgId    父组织ID
     * @return 子组织数量
     */
    long countChildren(@Param("tenantId") Long tenantId,
                       @Param("orgId") Long orgId);

    /**
     * 根据组织编码查询组织（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @param code     组织编码
     * @return 组织实体，不存在则返回null
     */
    SysOrg selectByCode(@Param("tenantId") Long tenantId,
                        @Param("code") String code);

    /**
     * 根据组织编码集合查询已存在的组织（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @param codes    组织编码集合
     * @return 组织实体列表
     */
    List<SysOrg> selectExistingByCodes(@Param("tenantId") Long tenantId,
                                       @Param("codes") Set<String> codes);

    /**
     * 批量调整组织层级（组织移动后子树 level 同步）
     * <p>
     * 单条 SQL 增量更新：level = level + delta。租户隔离 + 未删除过滤。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待调整的组织ID列表
     * @param delta     层级增量（可正可负）
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    int batchUpdateLevel(@Param("tenantId") Long tenantId,
                         @Param("ids") List<Long> ids,
                         @Param("delta") int delta,
                         @Param("updatedAt") LocalDateTime updatedAt);
}
