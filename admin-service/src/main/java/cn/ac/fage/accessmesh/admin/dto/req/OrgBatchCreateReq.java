package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量创建组织请求记录类
 * <p>
 * 用于一次性创建多个组织的请求参数。
 * 常用于初始化组织架构或导入组织数据。
 * </p>
 *
 * @param orgs 组织创建请求列表（必填，至少包含一个组织）
 */
public record OrgBatchCreateReq(
    /**
     * 组织创建请求列表
     */
    @Valid @NotEmpty(message = "组织列表不能为空")
    List<OrgCreateReq> orgs
) {}