package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 批量创建组织请求
 */
public record OrgBatchCreateReq(
    @Valid @NotEmpty(message = "组织列表不能为空")
    List<OrgCreateReq> orgs
) {}