package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface OperationPermissionMapper extends BaseMapper<OperationPermission> {

    /**
     * Select OperationPermissions where (binary_bit | inherit_mask) & requiredBits = requiredBits.
     * This uses SQL bitwise operations to filter at database level, avoiding full table load.
     *
     * @param tenantId tenant ID for isolation
     * @param resourceType resource type filter (can be null)
     * @param requiredBits the required bits to match
     * @return list of matching OperationPermissions
     */
    @Select("""
        SELECT id, tenant_id, resource_type, code, name, binary_bit, inherit_mask,
               created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
        FROM operation_permission
        WHERE tenant_id = #{tenantId}
          AND delete_flag = 0
          AND (#{resourceType} IS NULL OR resource_type = #{resourceType})
          AND ((binary_bit | inherit_mask) & #{requiredBits}) = #{requiredBits}
        """)
    List<OperationPermission> selectByEffectiveBitsMatch(
        @Param("tenantId") Long tenantId,
        @Param("resourceType") Integer resourceType,
        @Param("requiredBits") Long requiredBits);
}
