package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 资源实体数据访问接口
 * <p>
 * 提供资源实体表的基础CRUD操作和自定义查询方法。
 * 资源实体是权限系统的核心实体，代表可被权限控制的资源对象。
 * 支持批量软删除、祖先和后代递归查询等操作。
 * </p>
 */
public interface ResourceEntityMapper extends BaseMapper<ResourceEntity> {

    /**
     * 批量软删除资源实体
     * <p>
     * 将指定资源实体的delete_flag设置为id，deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的资源实体ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 使用PostgreSQL递归CTE查询所有后代资源ID（不含自身）
     * <p>
     * 从指定资源实体开始，递归查询所有子孙资源的ID。
     * 用于级联删除和权限继承计算场景。
     * </p>
     *
     * @param tenantId         租户ID
     * @param resourceEntityId 资源实体ID
     * @return 后代资源ID列表（无后代或资源不存在时返回空列表）
     */
    List<Long> selectDescendantIds(@Param("tenantId") Long tenantId,
                                   @Param("resourceEntityId") Long resourceEntityId);

    /**
     * 使用PostgreSQL递归CTE查询所有后代资源ID（含自身）
     * <p>
     * 从指定资源实体开始，递归查询所有子孙资源的ID，包含自身。
     * 用于批量操作场景，确保自身也被包含。
     * </p>
     *
     * @param tenantId         租户ID
     * @param resourceEntityId 资源实体ID
     * @return 后代资源ID列表（包含自身）
     */
    List<Long> selectDescendantIdsIncludingSelf(@Param("tenantId") Long tenantId,
                                                @Param("resourceEntityId") Long resourceEntityId);

    /**
     * 使用PostgreSQL递归CTE查询所有祖先资源ID
     * <p>
     * 从指定资源实体开始，向上递归查询所有祖先资源的ID。
     * 用于权限继承计算和资源路径查询场景。
     * </p>
     *
     * @param tenantId         租户ID
     * @param resourceEntityId 资源实体ID
     * @return 祖先资源ID列表（从父到根顺序，无祖先或资源不存在时返回空列表）
     */
    List<Long> selectAncestorIds(@Param("tenantId") Long tenantId,
                                  @Param("resourceEntityId") Long resourceEntityId);

    /**
     * 使用PostgreSQL递归CTE批量查询多个资源的所有祖先资源ID
     * <p>
     * 从多个资源实体开始，向上递归查询所有祖先资源的ID。
     * 用于批量权限计算场景，提高查询效率。
     * </p>
     *
     * @param tenantId          租户ID
     * @param resourceEntityIds 资源实体ID集合
     * @return 祖先查询结果列表，包含resourceId和ancestorId对
     */
    List<AncestorResult> selectAncestorIdsBatch(@Param("tenantId") Long tenantId,
                                                 @Param("resourceEntityIds") java.util.Set<Long> resourceEntityIds);

    /**
     * 使用PostgreSQL递归CTE批量查询多个资源的所有后代资源ID
     * <p>
     * 从多个资源实体开始，向下递归查询所有后代资源的ID。
     * 用于批量级联操作场景，提高查询效率。
     * </p>
     *
     * @param tenantId          租户ID
     * @param resourceEntityIds 资源实体ID集合
     * @return 后代查询结果列表，包含resourceId和descendantId对
     */
    List<DescendantResult> selectDescendantIdsBatch(@Param("tenantId") Long tenantId,
                                                     @Param("resourceEntityIds") java.util.Set<Long> resourceEntityIds);

    /**
     * 批量祖先查询结果类
     * <p>
     * 用于封装批量祖先查询的结果，包含资源ID和祖先ID对。
     * </p>
     */
    class AncestorResult {
        private Long resourceId;
        private Long ancestorId;

        public Long getResourceId() { return resourceId; }
        public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
        public Long getAncestorId() { return ancestorId; }
        public void setAncestorId(Long ancestorId) { this.ancestorId = ancestorId; }
    }

    /**
     * 批量后代查询结果类
     * <p>
     * 用于封装批量后代查询的结果，包含资源ID和后代ID对。
     * </p>
     */
    class DescendantResult {
        private Long resourceId;
        private Long descendantId;

        public Long getResourceId() { return resourceId; }
        public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
        public Long getDescendantId() { return descendantId; }
        public void setDescendantId(Long descendantId) { this.descendantId = descendantId; }
    }
}