package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 组织树配置创建请求记录类
 *
 * @param orgId       根组织ID（必填）
 * @param treeName    组织树名称（必填）
 * @param treeType    树类型（必填）
 * @param singleAssoc 是否单关联（可选，POSITION 树始终为 false）
 */
public record OrgTreeConfigCreateReq(
    @NotNull(message = "组织ID不能为空")
    Long orgId,

    @NotBlank(message = "组织树名称不能为空")
    @Size(max = 128, message = "组织树名称长度不能超过128个字符")
    String treeName,

    @NotBlank(message = "树类型不能为空")
    @Size(max = 32, message = "树类型长度不能超过32个字符")
    String treeType,

    Boolean singleAssoc
) {}