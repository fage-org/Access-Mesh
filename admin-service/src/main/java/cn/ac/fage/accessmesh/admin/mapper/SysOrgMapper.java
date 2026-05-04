package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SysOrgMapper extends BaseMapper<SysOrg> {

    /**
     * 批量软删除组织
     *
     * @param tenantId  租户ID
     * @param ids       组织ID列表
     * @param deletedAt 删除时间
     * @return 影响行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 使用 PostgreSQL CTE 递归查询获取所有子孙组织ID（不包括自身）
     *
     * @param tenantId 租户ID
     * @param orgId    组织ID
     * @return 子孙组织ID列表
     */
    List<Long> selectDescendantIds(@Param("tenantId") Long tenantId,
                                   @Param("orgId") Long orgId);

    /**
     * 使用 PostgreSQL CTE 递归查询获取所有子孙组织ID（包括自身）
     *
     * @param tenantId 租户ID
     * @param orgId    组织ID
     * @return 子孙组织ID列表（包含自身）
     */
    List<Long> selectDescendantIdsIncludingSelf(@Param("tenantId") Long tenantId,
                                                @Param("orgId") Long orgId);
}
