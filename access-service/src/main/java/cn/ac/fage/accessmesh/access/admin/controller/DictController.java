package cn.ac.fage.accessmesh.access.admin.controller;

import cn.ac.fage.accessmesh.access.admin.dto.req.DictDataCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.DictDataUpdateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.DictTypeCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.DictDataResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.DictTypeResp;
import cn.ac.fage.accessmesh.access.admin.service.DictService;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.model.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典管理控制器
 * <p>
 * 提供字典类型和字典数据的CRUD操作。
 * 字字典用于系统中的枚举值管理，如状态、类型、分类等。
 * 字典类型定义枚举的分类，字典数据定义具体的枚举值。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/dict")
public class DictController {

    private final DictService dictService;

    /**
     * 构造函数注入依赖
     *
     * @param dictService 字典管理服务
     */
    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    /**
     * 创建字典类型
     * <p>
     * 创建新的字典类型分类，用于组织字典数据。
     * </p>
     *
     * @param req 字典类型创建请求，包含类型名称、编码、描述
     * @return 创建成功的字典类型ID
     */
    @PostMapping("/type/create")
    public R<Long> createDictType(@Valid @RequestBody DictTypeCreateReq req) {
        return R.ok(dictService.createDictType(req));
    }

    /**
     * 删除字典类型
     * <p>
     * 批量删除字典类型，会同时删除该类型下的所有字典数据。
     * </p>
     *
     * @param req ID集合请求，包含待删除的字典类型ID列表
     * @return 操作成功结果
     */
    @PostMapping("/type/delete")
    public R<Void> deleteDictType(@Valid @RequestBody IdsReq req) {
        dictService.deleteDictType(req);
        return R.ok();
    }

    /**
     * 查询字典类型列表
     * <p>
     * 返回所有的字典类型列表，用于下拉选择。
     * </p>
     *
     * @return 字典类型列表
     */
    @PostMapping("/type/list")
    public R<List<DictTypeResp>> listDictTypes() {
        return R.ok(dictService.listDictTypes());
    }

    /**
     * 分页查询字典类型
     * <p>
     * 支持分页查询字典类型列表。
     * </p>
     *
     * @param pageReq 分页查询请求
     * @return 分页字典类型列表结果
     */
    @PostMapping("/type/page")
    public R<PaginatedResult<DictTypeResp>> pageDictTypes(@Valid @RequestBody PageReq pageReq) {
        return R.ok(dictService.pageDictTypes(pageReq));
    }

    /**
     * 创建字典数据
     * <p>
     * 在指定字典类型下创建新的字典数据项。
     * </p>
     *
     * @param req 字典数据创建请求，包含类型ID、标签、值、排序
     * @return 创建成功的字典数据ID
     */
    @PostMapping("/data/create")
    public R<Long> createDictData(@Valid @RequestBody DictDataCreateReq req) {
        return R.ok(dictService.createDictData(req));
    }

    /**
     * 更新字典数据
     * <p>
     * 更新字典数据的标签、值、状态、排序等属性。
     * </p>
     *
     * @param req 字典数据更新请求，包含数据ID和新属性值
     * @return 操作成功结果
     */
    @PostMapping("/data/update")
    public R<Void> updateDictData(@Valid @RequestBody DictDataUpdateReq req) {
        dictService.updateDictData(req);
        return R.ok();
    }

    /**
     * 删除字典数据
     * <p>
     * 删除指定字典数据项。
     * </p>
     *
     * @param req ID请求，包含字典数据ID
     * @return 操作成功结果
     */
    @PostMapping("/data/delete")
    public R<Void> deleteDictData(@Valid @RequestBody IdReq req) {
        dictService.deleteDictData(req);
        return R.ok();
    }

    /**
     * 查询字典类型下的数据列表
     * <p>
     * 根据字典类型ID查询该类型下的所有字典数据项。
     * 用于前端下拉框等数据展示。
     * </p>
     *
     * @param req ID请求，包含字典类型ID
     * @return 字典数据列表
     */
    @PostMapping("/data/list")
    public R<List<DictDataResp>> listDictData(@Valid @RequestBody IdReq req) {
        return R.ok(dictService.listDictData(req.id()));
    }
}