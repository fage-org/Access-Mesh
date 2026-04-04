package org.dromara.permission.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.dto.SystemConfigListReq;
import org.dromara.permission.domain.dto.SystemConfigSaveReq;
import org.dromara.permission.domain.dto.TypeDefinitionListReq;
import org.dromara.permission.domain.dto.TypeDefinitionSaveReq;
import org.dromara.permission.domain.vo.SystemConfigVo;
import org.dromara.permission.domain.vo.TypeDefinitionVo;
import org.dromara.permission.service.TypeDefinitionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 兼容旧的 system_config 接口路径，内部统一走 type_definition。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/perm/system-config")
public class SystemConfigController {

    private final TypeDefinitionService typeDefinitionService;

    @PostMapping("/list")
    public R<List<SystemConfigVo>> list(@RequestBody SystemConfigListReq req) {
        TypeDefinitionListReq inner = new TypeDefinitionListReq();
        inner.setTenantId(req.getTenantId());
        inner.setBizDomainId(req.getBizDomainId());
        inner.setTypeKey(req.getConfigKey());
        List<TypeDefinitionVo> result = typeDefinitionService.list(inner);
        return R.ok(result.stream().map(this::toVo).collect(Collectors.toList()));
    }

    @PostMapping("/save")
    public R<Void> save(@Validated @RequestBody SystemConfigSaveReq req) {
        TypeDefinitionSaveReq inner = new TypeDefinitionSaveReq();
        inner.setItems(req.getItems().stream().map(item -> {
            TypeDefinitionSaveReq.TypeDefinitionItem mapped = new TypeDefinitionSaveReq.TypeDefinitionItem();
            mapped.setId(item.getId());
            mapped.setTenantId(item.getTenantId());
            mapped.setBizDomainId(item.getBizDomainId());
            mapped.setTypeKey(item.getConfigKey());
            mapped.setTypeValue(item.getTypeValue());
            mapped.setName(item.getName());
            mapped.setDescription(item.getDescription());
            mapped.setSortOrder(item.getSortOrder());
            return mapped;
        }).collect(Collectors.toList()));
        typeDefinitionService.save(inner);
        return R.ok();
    }

    @PostMapping("/remove")
    public R<Void> remove(@Validated @RequestBody IdsReq req) {
        typeDefinitionService.remove(req);
        return R.ok();
    }

    private SystemConfigVo toVo(TypeDefinitionVo source) {
        SystemConfigVo vo = new SystemConfigVo();
        vo.setId(source.getId());
        vo.setTenantId(source.getTenantId());
        vo.setBizDomainId(source.getBizDomainId());
        vo.setConfigKey(source.getTypeKey());
        vo.setTypeValue(source.getTypeValue());
        vo.setName(source.getName());
        vo.setDescription(source.getDescription());
        vo.setSortOrder(source.getSortOrder());
        vo.setCreatedAt(source.getCreatedAt());
        return vo;
    }
}
