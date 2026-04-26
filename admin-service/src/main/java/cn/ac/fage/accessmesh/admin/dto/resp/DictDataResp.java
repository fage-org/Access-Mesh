package cn.ac.fage.accessmesh.admin.dto.resp;

public record DictDataResp(
    Long id,
    Long dictTypeId,
    String dictLabel,
    String dictValue,
    Integer sort,
    Integer status,
    String remark
) {}
