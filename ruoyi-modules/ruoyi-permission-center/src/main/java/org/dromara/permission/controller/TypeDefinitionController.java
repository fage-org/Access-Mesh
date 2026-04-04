package org.dromara.permission.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.dto.TypeDefinitionListReq;
import org.dromara.permission.domain.dto.TypeDefinitionSaveReq;
import org.dromara.permission.domain.vo.TypeDefinitionVo;
import org.dromara.permission.service.TypeDefinitionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 类型定义 type_definition 接口
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/perm/type-definitions")
public class TypeDefinitionController {

    private final TypeDefinitionService typeDefinitionService;

    @PostMapping("/list")
    public R<List<TypeDefinitionVo>> list(@RequestBody TypeDefinitionListReq req) {
        return R.ok(typeDefinitionService.list(req));
    }

    @PostMapping("/save")
    public R<Void> save(@Validated @RequestBody TypeDefinitionSaveReq req) {
        typeDefinitionService.save(req);
        return R.ok();
    }

    @PostMapping("/remove")
    public R<Void> remove(@Validated @RequestBody IdsReq req) {
        typeDefinitionService.remove(req);
        return R.ok();
    }
}
