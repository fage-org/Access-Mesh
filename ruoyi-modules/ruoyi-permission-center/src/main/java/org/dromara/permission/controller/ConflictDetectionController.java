package org.dromara.permission.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.ConflictDetectReq;
import org.dromara.permission.domain.vo.ConflictDetectionPageVo;
import org.dromara.permission.service.ConflictRuleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/perm/conflict-detection")
public class ConflictDetectionController {

    private final ConflictRuleService conflictRuleService;

    @PostMapping
    public R<ConflictDetectionPageVo> detect(@Validated @RequestBody ConflictDetectReq req) {
        return R.ok(conflictRuleService.detectPage(req));
    }
}
