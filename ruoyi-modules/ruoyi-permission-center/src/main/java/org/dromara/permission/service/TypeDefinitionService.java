package org.dromara.permission.service;

import org.dromara.permission.domain.dto.TypeDefinitionListReq;
import org.dromara.permission.domain.dto.TypeDefinitionSaveReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.vo.TypeDefinitionVo;

import java.util.List;

public interface TypeDefinitionService {

    List<TypeDefinitionVo> list(TypeDefinitionListReq req);

    void save(TypeDefinitionSaveReq req);

    void remove(IdsReq req);
}
