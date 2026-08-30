package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 权限条件批量删除请求体
 * <p>
 * 以业务键 code 集合批量软删除权限条件（T-PERM-029 从内部主键 ids 切换）。
 * 请求中不存在的 code 静默跳过（幂等语义，与 resource-entity/remove 一致）。
 * </p>
 *
 * @param codes 条件编码列表，必填且不能为空
 */
public record ConditionRemoveReq(
    @NotEmpty List<@NotBlank @Size(max = 64) String> codes
) {}
