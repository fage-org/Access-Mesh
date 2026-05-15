package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.req.DictDataCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.DictDataUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.req.DictTypeCreateReq;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.DictDataResp;
import cn.ac.fage.accessmesh.admin.dto.resp.DictTypeResp;
import cn.ac.fage.accessmesh.admin.entity.SysDictData;
import cn.ac.fage.accessmesh.admin.entity.SysDictType;
import cn.ac.fage.accessmesh.admin.entity.table.SysDictDataTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysDictTypeTableDef;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysDictDataMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysDictTypeMapper;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.DictService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.mybatis.TenantSafeQuery;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 字典管理服务实现类
 * <p>
 * 提供字典类型和字典数据的CRUD操作、分页查询、列表查询等功能。
 * 字典类型定义字典分类，字典数据定义具体选项值。
 * 使用Spring Cache缓存字典类型列表，修改时自动清除缓存。
 * 支持批量操作和层级校验（删除类型前检查是否有关联数据）。
 * </p>
 */
@Service
public class DictServiceImpl implements DictService {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;
    private final AdminPermissionValidator permissionValidator;

    /**
     * 构造函数注入依赖
     *
     * @param dictTypeMapper 字典类型数据访问Mapper
     * @param dictDataMapper 字典数据数据访问Mapper
     * @param permissionValidator 权限校验器，校验字典操作权限
     */
    public DictServiceImpl(SysDictTypeMapper dictTypeMapper, SysDictDataMapper dictDataMapper,
                           AdminPermissionValidator permissionValidator) {
        this.dictTypeMapper = dictTypeMapper;
        this.dictDataMapper = dictDataMapper;
        this.permissionValidator = permissionValidator;
    }

    /**
     * 创建字典类型
     * <p>
     * 创建新的字典分类，设置类型名称、类型编码、状态等。
     * 执行类型级权限校验(CREATE)。
     * 创建成功后清除字典类型缓存。
     * </p>
     *
     * @param req 字典类型创建请求，包含类型名称、类型编码、状态、备注
     * @return 新字典类型ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "dictTypes", allEntries = true)
    public Long createDictType(DictTypeCreateReq req) {
        // Permission check - type-level CREATE
        permissionValidator.checkTypeLevel(AdminResourceType.DICT, AdminOperationCode.CREATE);

        SysDictType type = new SysDictType();
        type.setTenantId(TenantContextHolder.getTenantId());
        type.setDictType(req.dictType());
        type.setDictName(req.dictName());
        type.setStatus(req.status() != null ? req.status() : 1);
        type.setRemark(req.remark());
        type.setCreatedAt(LocalDateTime.now());
        type.setUpdatedAt(LocalDateTime.now());
        type.setDeleteFlag(0L);
        dictTypeMapper.insert(type);
        return type.getId();
    }

    /**
     * 批量删除字典类型
     * <p>
     * 执行批量实例级权限校验后软删除字典类型。
     * 删除前检查是否有关联的字典数据，有则拒绝删除。
     * 使用批量查询优化性能，避免N+1问题。
     * 删除成功后清除字典类型缓存。
     * </p>
     *
     * @param req ID集合请求，包含待删除的字典类型ID列表
     * @throws BizException 字典类型有关联数据
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "dictTypes", allEntries = true)
    public void deleteDictType(IdsReq req) {
        // Permission check - batch instance-level DELETE
        List<String> resourceCodes = req.ids().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.DICT, resourceCodes, AdminOperationCode.DELETE);

        // Batch query to check for data and filter valid IDs (performance fix: avoid N+1 queries)
        List<SysDictType> types = dictTypeMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysDictTypeTableDef.SYS_DICT_TYPE.ID.in(req.ids()))
                .and(SysDictTypeTableDef.SYS_DICT_TYPE.TENANT_ID.eq(TenantContextHolder.getTenantId()))
                .and(SysDictTypeTableDef.SYS_DICT_TYPE.DELETE_FLAG.eq(0))
        );

        // Batch check if any type has associated data (performance fix: single query with GROUP BY)
        if (!types.isEmpty()) {
            List<String> dictTypes = types.stream()
                .map(SysDictType::getDictType)
                .collect(Collectors.toList());

            List<SysDictData> dataWithTypes = dictDataMapper.selectListByQuery(
                QueryWrapper.create()
                    .select(SysDictDataTableDef.SYS_DICT_DATA.DICT_TYPE)
                    .where(SysDictDataTableDef.SYS_DICT_DATA.TENANT_ID.eq(TenantContextHolder.getTenantId()))
                    .and(SysDictDataTableDef.SYS_DICT_DATA.DICT_TYPE.in(dictTypes))
                    .and(SysDictDataTableDef.SYS_DICT_DATA.DELETE_FLAG.eq(0))
                    .groupBy(SysDictDataTableDef.SYS_DICT_DATA.DICT_TYPE)
            );

            if (!dataWithTypes.isEmpty()) {
                throw new BizException(AdminErrorCode.DICT_TYPE_HAS_DATA.getCode(), AdminErrorCode.DICT_TYPE_HAS_DATA.getMessage());
            }
        }

        // Batch soft delete (performance fix: use single SQL instead of loop)
        if (!types.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            List<Long> validIds = types.stream().map(SysDictType::getId).collect(java.util.stream.Collectors.toList());
            dictTypeMapper.softDeleteBatch(TenantContextHolder.getTenantId(), validIds, now);
        }
    }

    /**
     * 获取所有字典类型列表（含字典数据）
     * <p>
     * 查询所有字典类型及其关联的字典数据，构建完整字典结构。
     * 使用Spring Cache缓存，避免重复查询。
     * 使用批量查询优化：1次查询类型 + 1次查询数据（优化前需要N次查询）。
     * </p>
     *
     * @return 字典类型响应列表，每个类型包含其下的字典数据列表
     */
    @Override
    @Cacheable(value = "dictTypes", key = "'all'")
    public List<DictTypeResp> listDictTypes() {
        Long tenantId = TenantContextHolder.getTenantId();

        // 1. Query all dict types
        List<SysDictType> types = dictTypeMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysDictTypeTableDef.SYS_DICT_TYPE.TENANT_ID.eq(tenantId))
                .and(SysDictTypeTableDef.SYS_DICT_TYPE.DELETE_FLAG.eq(0))
                .orderBy(SysDictTypeTableDef.SYS_DICT_TYPE.CREATED_AT.asc())
        );

        if (types.isEmpty()) {
            return List.of();
        }

        // 2. Batch query all dict data (1 query instead of N queries)
        List<String> dictTypeStrings = types.stream()
            .map(SysDictType::getDictType)
            .collect(Collectors.toList());

        List<SysDictData> allData = dictDataMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysDictDataTableDef.SYS_DICT_DATA.TENANT_ID.eq(tenantId))
                .and(SysDictDataTableDef.SYS_DICT_DATA.DICT_TYPE.in(dictTypeStrings))
                .and(SysDictDataTableDef.SYS_DICT_DATA.DELETE_FLAG.eq(0))
                .orderBy(SysDictDataTableDef.SYS_DICT_DATA.SORT_ORDER.asc())
        );

        // 3. Group by dictType string (in-memory operation)
        Map<String, List<SysDictData>> dataByDictType = allData.stream()
            .collect(Collectors.groupingBy(SysDictData::getDictType));

        // 4. Build response (in-memory operation)
        return types.stream().map(t -> {
            List<SysDictData> dataList = dataByDictType.getOrDefault(t.getDictType(), List.of());
            List<DictDataResp> dataRespList = dataList.stream()
                .map(d -> new DictDataResp(d.getId(), t.getId(), d.getDictLabel(), d.getDictValue(),
                    d.getSortOrder(), d.getStatus(), d.getRemark()))
                .collect(Collectors.toList());
            return new DictTypeResp(t.getId(), t.getDictName(), t.getDictType(), t.getStatus(), t.getRemark(), t.getCreatedAt(), dataRespList);
        }).collect(Collectors.toList());
    }

    /**
     * 分页查询字典类型列表
     * <p>
     * 获取字典类型的分页列表，不含字典数据。
     * 按创建时间正序排列。
     * </p>
     *
     * @param pageReq 分页查询请求，包含分页参数
     * @return 分页字典类型列表结果
     */
    @Override
    public PaginatedResult<DictTypeResp> pageDictTypes(PageReq pageReq) {
        Page<SysDictType> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysDictType> result = dictTypeMapper.paginate(page,
            QueryWrapper.create()
                .where(SysDictTypeTableDef.SYS_DICT_TYPE.TENANT_ID.eq(TenantContextHolder.getTenantId()))
                .and(SysDictTypeTableDef.SYS_DICT_TYPE.DELETE_FLAG.eq(0))
                .orderBy(SysDictTypeTableDef.SYS_DICT_TYPE.CREATED_AT.asc()));

        List<DictTypeResp> items = result.getRecords().stream()
            .map(t -> new DictTypeResp(t.getId(), t.getDictName(), t.getDictType(), t.getStatus(), t.getRemark(), t.getCreatedAt(), List.of()))
            .collect(Collectors.toList());

        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }

    /**
     * 创建字典数据
     * <p>
     * 在指定字典类型下创建新的字典选项值。
     * 执行类型级权限校验(CREATE)。
     * 验证字典类型存在后创建数据。
     * 创建成功后清除字典类型缓存。
     * </p>
     *
     * @param req 字典数据创建请求，包含字典类型ID、标签、值、排序、状态
     * @return 新字典数据ID
     * @throws BizException 字典类型不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "dictTypes", allEntries = true)
    public Long createDictData(DictDataCreateReq req) {
        // Permission check - type-level CREATE for dict data
        permissionValidator.checkTypeLevel(AdminResourceType.DICT_DATA, AdminOperationCode.CREATE);

        SysDictType type = TenantSafeQuery.selectOneByIdSafe(
            dictTypeMapper, SysDictTypeTableDef.SYS_DICT_TYPE.ID, SysDictTypeTableDef.SYS_DICT_TYPE.TENANT_ID, SysDictTypeTableDef.SYS_DICT_TYPE.DELETE_FLAG,
            TenantContextHolder.getTenantId(), req.dictTypeId());
        if (type == null) {
            throw new BizException(AdminErrorCode.DICT_TYPE_NOT_FOUND.getCode(), AdminErrorCode.DICT_TYPE_NOT_FOUND.getMessage());
        }
        SysDictData data = new SysDictData();
        data.setTenantId(TenantContextHolder.getTenantId());
        data.setDictType(type.getDictType());
        data.setDictLabel(req.dictLabel());
        data.setDictValue(req.dictValue());
        data.setSortOrder(req.sort());
        data.setStatus(req.status() != null ? req.status() : 1);
        data.setRemark(req.remark());
        data.setCreatedAt(LocalDateTime.now());
        data.setUpdatedAt(LocalDateTime.now());
        data.setDeleteFlag(0L);
        dictDataMapper.insert(data);
        return data.getId();
    }

    /**
     * 更新字典数据
     * <p>
     * 更新字典数据的标签、值、排序、状态等属性。
     * 执行实例级权限校验(UPDATE)。
     * 更新成功后清除字典类型缓存。
     * </p>
     *
     * @param req 字典数据创建请求（复用），包含字典数据ID和新属性值
     * @throws BizException 字典数据不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "dictTypes", allEntries = true)
    public void updateDictData(DictDataUpdateReq req) {
        Long tenantId = TenantContextHolder.getTenantId();

        // Permission check - instance-level UPDATE on the data record
        permissionValidator.checkInstanceLevel(AdminResourceType.DICT_DATA,
            String.valueOf(req.id()), AdminOperationCode.UPDATE);

        SysDictData data = TenantSafeQuery.selectOneByIdSafe(
            dictDataMapper, SysDictDataTableDef.SYS_DICT_DATA.ID, SysDictDataTableDef.SYS_DICT_DATA.TENANT_ID, SysDictDataTableDef.SYS_DICT_DATA.DELETE_FLAG,
            tenantId, req.id());
        if (data == null) {
            throw new BizException(AdminErrorCode.DICT_DATA_NOT_FOUND.getCode(), AdminErrorCode.DICT_DATA_NOT_FOUND.getMessage());
        }

        SysDictType type = TenantSafeQuery.selectOneByIdSafe(
            dictTypeMapper, SysDictTypeTableDef.SYS_DICT_TYPE.ID, SysDictTypeTableDef.SYS_DICT_TYPE.TENANT_ID, SysDictTypeTableDef.SYS_DICT_TYPE.DELETE_FLAG,
            tenantId, req.dictTypeId());
        if (type == null) {
            throw new BizException(AdminErrorCode.DICT_TYPE_NOT_FOUND.getCode(), AdminErrorCode.DICT_TYPE_NOT_FOUND.getMessage());
        }

        data.setDictType(type.getDictType());
        data.setDictLabel(req.dictLabel());
        data.setDictValue(req.dictValue());
        if (req.sort() != null) {
            data.setSortOrder(req.sort());
        }
        if (req.status() != null) {
            data.setStatus(req.status());
        }
        if (req.remark() != null) {
            data.setRemark(req.remark());
        }
        data.setUpdatedAt(LocalDateTime.now());
        dictDataMapper.update(data);
    }

    /**
     * 删除字典数据
     * <p>
     * 软删除单个字典数据项。
     * 执行实例级权限校验(DELETE)。
     * 删除成功后清除字典类型缓存。
     * </p>
     *
     * @param req ID请求，包含字典数据ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "dictTypes", allEntries = true)
    public void deleteDictData(IdReq req) {
        // Permission check - instance-level DELETE
        permissionValidator.checkInstanceLevel(
            AdminResourceType.DICT_DATA,
            String.valueOf(req.id()),
            AdminOperationCode.DELETE
        );

        SysDictData data = TenantSafeQuery.selectOneByIdSafe(
            dictDataMapper, SysDictDataTableDef.SYS_DICT_DATA.ID, SysDictDataTableDef.SYS_DICT_DATA.TENANT_ID, SysDictDataTableDef.SYS_DICT_DATA.DELETE_FLAG,
            TenantContextHolder.getTenantId(), req.id());
        if (data == null) return;
        data.setDeleteFlag(data.getId());
        data.setDeletedAt(LocalDateTime.now());
        dictDataMapper.update(data);
    }

    /**
     * 获取字典类型下的字典数据列表
     * <p>
     * 根据字典类型ID查询该类型下所有字典数据。
     * 按排序字段正序排列。
     * </p>
     *
     * @param dictTypeId 字典类型ID
     * @return 字典数据响应列表
     */
    @Override
    public List<DictDataResp> listDictData(Long dictTypeId) {
        SysDictType type = TenantSafeQuery.selectOneByIdSafe(
            dictTypeMapper, SysDictTypeTableDef.SYS_DICT_TYPE.ID, SysDictTypeTableDef.SYS_DICT_TYPE.TENANT_ID, SysDictTypeTableDef.SYS_DICT_TYPE.DELETE_FLAG,
            TenantContextHolder.getTenantId(), dictTypeId);
        if (type == null) return List.of();
        return dictDataMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysDictDataTableDef.SYS_DICT_DATA.TENANT_ID.eq(TenantContextHolder.getTenantId()))
                .and(SysDictDataTableDef.SYS_DICT_DATA.DICT_TYPE.eq(type.getDictType()))
                .and(SysDictDataTableDef.SYS_DICT_DATA.DELETE_FLAG.eq(0))
                .orderBy(SysDictDataTableDef.SYS_DICT_DATA.SORT_ORDER.asc())
        ).stream().map(d -> new DictDataResp(
            d.getId(), dictTypeId, d.getDictLabel(), d.getDictValue(),
            d.getSortOrder(), d.getStatus(), d.getRemark()
        )).collect(Collectors.toList());
    }
}