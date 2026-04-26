package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;

public record DictTypeCreateReq(
    @NotBlank(message = "字典类型名称不能为空")
    String dictName,
    @NotBlank(message = "字典类型不能为空")
    String dictType,
    Integer status,
    String remark
) {}
