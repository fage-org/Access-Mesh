package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * ID列表请求记录类
 * <p>
 * 用于批量操作的请求参数，包含一组实体ID。
 * 常用于批量删除、批量启用/禁用等场景。
 * </p>
 *
 * @param ids ID列表（必填，至少包含一个ID）
 */
public record IdsReq(
    /**
     * 实体ID列表
     */
    @NotEmpty(message = "ID列表不能为空")
    List<Long> ids
) {}