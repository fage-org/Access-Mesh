package cn.ac.fage.accessmesh.admin.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

public record DictTypeResp(
    Long id,
    String dictName,
    String dictType,
    Integer status,
    String remark,
    LocalDateTime createdAt,
    List<DictDataResp> data
) {}
