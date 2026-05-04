package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.admin.dto.req.DictDataCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.DictTypeCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.resp.DictDataResp;
import cn.ac.fage.accessmesh.admin.dto.resp.DictTypeResp;
import cn.ac.fage.accessmesh.admin.service.DictService;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.model.PermResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/dict")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @PostMapping("/type/create")
    @AuditLog(module = "字典管理", action = "创建类型", targetType = "DICT_TYPE")
    public PermResult<Long> createDictType(@Valid @RequestBody DictTypeCreateReq req) {
        return PermResult.success(dictService.createDictType(req));
    }

    @PostMapping("/type/delete")
    @AuditLog(module = "字典管理", action = "删除类型", targetType = "DICT_TYPE")
    public PermResult<Void> deleteDictType(@Valid @RequestBody IdsReq req) {
        dictService.deleteDictType(req);
        return PermResult.success();
    }

    @PostMapping("/type/list")
    public PermResult<List<DictTypeResp>> listDictTypes() {
        return PermResult.success(dictService.listDictTypes());
    }

    @PostMapping("/type/page")
    public PermResult<PaginatedResult<DictTypeResp>> pageDictTypes(@Valid @RequestBody PageReq pageReq) {
        return PermResult.success(dictService.pageDictTypes(pageReq));
    }

    @PostMapping("/data/create")
    @AuditLog(module = "字典管理", action = "创建数据", targetType = "DICT_DATA")
    public PermResult<Long> createDictData(@Valid @RequestBody DictDataCreateReq req) {
        return PermResult.success(dictService.createDictData(req));
    }

    @PostMapping("/data/update")
    @AuditLog(module = "字典管理", action = "修改数据", targetType = "DICT_DATA")
    public PermResult<Void> updateDictData(@Valid @RequestBody DictDataCreateReq req) {
        dictService.updateDictData(req);
        return PermResult.success();
    }

    @PostMapping("/data/delete")
    @AuditLog(module = "字典管理", action = "删除数据", targetType = "DICT_DATA")
    public PermResult<Void> deleteDictData(@Valid @RequestBody IdReq req) {
        dictService.deleteDictData(req);
        return PermResult.success();
    }

    @PostMapping("/data/list")
    public PermResult<List<DictDataResp>> listDictData(@Valid @RequestBody IdReq req) {
        return PermResult.success(dictService.listDictData(req.id()));
    }
}
