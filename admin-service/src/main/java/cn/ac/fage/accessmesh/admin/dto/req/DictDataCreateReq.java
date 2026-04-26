package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DictDataCreateReq(
    @NotNull(message = "字典类型ID不能为空")
    Long dictTypeId,
    @NotBlank(message = "字典标签不能为空")
    String dictLabel,
    @NotBlank(message = "字典键值不能为空")
    String dictValue,
    Integer sort,
    Integer status,
    String remark
) {}
