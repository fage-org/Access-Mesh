package cn.ac.fage.accessmesh.access.permission.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.dto.req.IdReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.TypeCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.TypeListReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.TypeUpdateReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.TypeDefinitionResp;
import cn.ac.fage.accessmesh.access.permission.service.TypeDefinitionAppService;
import cn.ac.fage.accessmesh.access.permission.util.PageUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 类型定义管理控制器
 * <p>
 * 提供类型定义的CRUD操作和查询功能。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/type-definition")
public class TypeDefinitionController {

    private final TypeDefinitionAppService typeDefinitionAppService;

    public TypeDefinitionController(TypeDefinitionAppService typeDefinitionAppService) {
        this.typeDefinitionAppService = typeDefinitionAppService;
    }

    @PostMapping("/create")
    public R<TypeDefinitionResp> createType(@Valid @RequestBody TypeCreateReq req) {
        return R.ok(typeDefinitionAppService.createType(TenantContextHolder.getTenantId(), req, null));
    }

    @PostMapping("/detail")
    public R<TypeDefinitionResp> getType(@Valid @RequestBody IdReq req) {
        return R.ok(typeDefinitionAppService.getType(TenantContextHolder.getTenantId(), req.id()));
    }

    @PostMapping("/list")
    public R<PageResp<TypeDefinitionResp>> listTypes(@Valid @RequestBody TypeListReq req) {
        // pageNum/pageSize 均未传 = 字典全量场景（授权页/冲突规则/资源操作等下拉数据源），
        // 取 PageUtil.MAX_PAGE_SIZE 上限（先例 /role/list LIMIT 0,200）；显式分页走 PageUtil 默认值。
        boolean paged = req.pageNum() != null || req.pageSize() != null;
        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = paged ? PageUtil.pageSize(req.pageSize()) : PageUtil.MAX_PAGE_SIZE;
        int offset = PageUtil.offset(pageNum, pageSize);
        Long tenantId = TenantContextHolder.getTenantId();
        long total = typeDefinitionAppService.countTypes(tenantId, req.typeKey(), req.keyword());
        List<TypeDefinitionResp> items =
            typeDefinitionAppService.listTypes(tenantId, req.typeKey(), req.keyword(), offset, pageSize);
        return R.ok(new PageResp<>(
            items, total, pageNum, pageSize, PageUtil.hasNext(offset, items.size(), total)));
    }

    @PostMapping("/remove")
    public R<Void> deleteType(@Valid @RequestBody IdsReq req) {
        typeDefinitionAppService.deleteTypesByIds(TenantContextHolder.getTenantId(), req.ids(), null);
        return R.ok();
    }

    @PostMapping("/update")
    public R<TypeDefinitionResp> updateType(@Valid @RequestBody TypeUpdateReq req) {
        return R.ok(typeDefinitionAppService.updateType(TenantContextHolder.getTenantId(), req, null));
    }
}
