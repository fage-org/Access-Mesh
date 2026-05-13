package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysOrg;
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
}