package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.DictDataCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.DictTypeCreateReq;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.DictDataResp;
import cn.ac.fage.accessmesh.admin.dto.resp.DictTypeResp;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

import java.util.List;

public interface DictService {

    Long createDictType(DictTypeCreateReq req);

    void deleteDictType(IdsReq req);

    List<DictTypeResp> listDictTypes();

    PaginatedResult<DictTypeResp> pageDictTypes(PageReq pageReq);

    Long createDictData(DictDataCreateReq req);

    void updateDictData(DictDataCreateReq req);

    void deleteDictData(IdReq req);

    List<DictDataResp> listDictData(Long dictTypeId);
}
