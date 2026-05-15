package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 组织树配置更新请求记录类
 *
 * @param id          配置ID（必填）
 * @param orgId       根组织ID（可选）
 * @param treeName    组织树名称（可选）
 * @param treeType    树类型（可选）
 * @param singleAssoc 是否单关联（可选，POSITION 树始终为 false）
 */
public record OrgTreeConfigUpdateReq(
    @NotNull(message = "配置ID不能为空")
    Long id,

    Long orgId,

    @Size(max = 128, message = "组织树名称长度不能超过128个字符")
    String treeName,

    @Size(max = 32, message = "树类型长度不能超过32个字符")
    String treeType,

    Boolean singleAssoc
) {}