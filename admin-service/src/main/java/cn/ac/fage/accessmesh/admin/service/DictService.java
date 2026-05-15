package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.DictDataCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.DictDataUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.req.DictTypeCreateReq;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.DictDataResp;
import cn.ac.fage.accessmesh.admin.dto.resp.DictTypeResp;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

import java.util.List;

/**
 * 字典服务接口
 * <p>
 * 提供字典管理相关的服务方法，包括字典类型和字典数据的管理。
 * 字典用于系统中各类枚举值的定义和维护，如状态类型、分类类型等。
 * </p>
 */
public interface DictService {

    /**
     * 创建字典类型
     * <p>
     * 创建新的字典类型定义。
     * 字典类型用于分类管理不同的字典数据集。
     * </p>
     *
     * @param req 字典类型创建请求
     * @return 创建的字典类型ID
     */
    Long createDictType(DictTypeCreateReq req);

    /**
     * 删除字典类型
     * <p>
     * 批量删除字典类型及其关联的字典数据。
     * 使用软删除方式，保留数据记录。
     * </p>
     *
     * @param req 待删除的字典类型ID列表请求
     */
    void deleteDictType(IdsReq req);

    /**
     * 获取所有字典类型列表
     * <p>
     * 查询系统中所有字典类型。
     * 用于字典类型选择和列表展示。
     * </p>
     *
     * @return 字典类型列表
     */
    List<DictTypeResp> listDictTypes();

    /**
     * 分页查询字典类型列表
     * <p>
     * 根据条件分页查询字典类型列表。
     * 支持按类型名称、编码等条件筛选。
     * </p>
     *
     * @param pageReq 分页查询请求
     * @return 分页字典类型列表结果
     */
    PaginatedResult<DictTypeResp> pageDictTypes(PageReq pageReq);

    /**
     * 创建字典数据
     * <p>
     * 在指定字典类型下创建新的字典数据项。
     * 字典数据是字典类型的具体枚举值。
     * </p>
     *
     * @param req 字典数据创建请求
     * @return 创建的字典数据ID
     */
    Long createDictData(DictDataCreateReq req);

    /**
     * 更新字典数据
     * <p>
     * 更新指定字典数据的基本信息。
     * 包括数据标签、数值、排序等属性。
     * </p>
     *
     * @param req 字典数据更新请求
     */
    void updateDictData(DictDataUpdateReq req);

    /**
     * 删除字典数据
     * <p>
     * 删除指定的字典数据项。
     * 使用软删除方式，保留数据记录。
     * </p>
     *
     * @param req 待删除的字典数据ID请求
     */
    void deleteDictData(IdReq req);

    /**
     * 获取字典类型下的字典数据列表
     * <p>
     * 查询指定字典类型下的所有字典数据项。
     * 用于下拉选择和数据展示。
     * </p>
     *
     * @param dictTypeId 字典类型ID
     * @return 字典数据列表
     */
    List<DictDataResp> listDictData(Long dictTypeId);
}