package org.dromara.permission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.condition.PermissionConditionExpressionEvaluator;
import org.dromara.permission.condition.PermissionConditionPresetHandlerRegistry;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcPermissionCondition;
import org.dromara.permission.domain.dto.ConditionListReq;
import org.dromara.permission.domain.dto.ConditionSaveReq;
import org.dromara.permission.domain.dto.ConditionUpdateReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.vo.PermissionConditionVo;
import org.dromara.permission.mapper.PcPermissionConditionMapper;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.dromara.permission.service.PermissionConditionService;
import org.dromara.permission.service.support.PermissionAuditSupport;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionConditionServiceImpl implements PermissionConditionService {

    private final PcPermissionConditionMapper mapper;
    private final PermissionConditionPresetHandlerRegistry presetHandlerRegistry;
    private final PermissionConditionExpressionEvaluator expressionEvaluator;

    @Override
    public List<PermissionConditionVo> list(ConditionListReq req) {
        assertValidTenantRequest(req == null ? null : req.getTenantId());
        assertValidConditionSource(req.getConditionSource());
        assertValidConditionStatus(req.getStatus());
        LambdaQueryWrapper<PcPermissionCondition> q = new LambdaQueryWrapper<PcPermissionCondition>()
            .eq(PcPermissionCondition::getTenantId, req.getTenantId())
            .eq(PcPermissionCondition::getDeleteFlag, PermissionConstants.NOT_DELETED);
        if (StrUtil.isNotBlank(req.getCode())) {
            q.eq(PcPermissionCondition::getCode, req.getCode());
        }
        String conditionSource = normalizeConditionSource(req.getConditionSource(), null);
        if (StrUtil.isNotBlank(req.getConditionSource())) {
            q.eq(PcPermissionCondition::getConditionSource, conditionSource);
        }
        if (StrUtil.isNotBlank(req.getStatus())) {
            String conditionStatus = req.getStatus().trim().toUpperCase();
            if (PermissionConstants.CONDITION_SOURCE_PRESET.equals(conditionSource)
                && !PermissionConstants.CONDITION_STATUS_APPROVED.equals(conditionStatus)) {
                return List.of();
            }
            q.eq(PcPermissionCondition::getStatus, conditionStatus);
        }
        if (req.getEnabled() != null) {
            q.eq(PcPermissionCondition::getEnabled, req.getEnabled());
        }
        q.orderByAsc(PcPermissionCondition::getId);
        return mapper.selectList(q).stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(ConditionSaveReq req) {
        assertValidTenantRequest(req == null ? null : req.getTenantId());
        LocalDateTime now = LocalDateTime.now();
        if (req.getId() != null) {
            PcPermissionCondition entity = loadActiveCondition(req.getTenantId(), req.getId());
            validateSaveRequest(req, entity);
            applySaveRequest(entity, req, now, false);
            entity.setUpdatedAt(now);
            mapper.updateById(entity);
            return;
        }

        PcPermissionCondition entity = new PcPermissionCondition();
        entity.setTenantId(req.getTenantId());
        entity.setDeleteFlag(PermissionConstants.NOT_DELETED);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        validateSaveRequest(req, null);
        applySaveRequest(entity, req, now, true);
        mapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long conditionId, ConditionUpdateReq req) {
        if (conditionId == null) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "conditionId missing");
        }
        assertValidTenantRequest(req == null ? null : req.getTenantId());
        PcPermissionCondition existing = loadActiveCondition(req.getTenantId(), conditionId);
        ConditionSaveReq merged = mergeUpdateRequest(conditionId, req, existing);
        save(merged);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(IdsReq req) {
        assertValidTenantRequest(req == null ? null : req.getTenantId());
        if (req.getIds() == null || req.getIds().isEmpty()) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "condition ids missing");
        }
        LocalDateTime now = LocalDateTime.now();
        List<PcPermissionCondition> entities = req.getIds().stream().map(id -> mapper.selectOne(new LambdaQueryWrapper<PcPermissionCondition>()
                .eq(PcPermissionCondition::getTenantId, req.getTenantId())
                .eq(PcPermissionCondition::getId, id)
                .eq(PcPermissionCondition::getDeleteFlag, PermissionConstants.NOT_DELETED)))
            .collect(Collectors.toList());
        long foundCount = entities.stream().filter(entity -> entity != null && PermissionConstants.NOT_DELETED.equals(entity.getDeleteFlag())).count();
        if (foundCount != req.getIds().size()) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "condition not found");
        }
        for (PcPermissionCondition entity : entities) {
            PermissionAuditSupport.markDeleted(entity, entity.getId(), now);
            mapper.updateById(entity);
        }
    }

    private void applySaveRequest(PcPermissionCondition entity, ConditionSaveReq req, LocalDateTime now, boolean creating) {
        String conditionSource = normalizeConditionSource(req.getConditionSource(), entity.getConditionSource());
        String persistedExpression = resolvePersistedExpression(req, entity, conditionSource);
        boolean definitionChanged = !creating && isDefinitionChanged(entity, req, persistedExpression);
        entity.setCode(req.getCode().trim());
        entity.setName(req.getName().trim());
        entity.setExpression(persistedExpression);
        entity.setDescription(toNullable(req.getDescription()));
        entity.setConditionSource(conditionSource);
        entity.setEnabled(resolveEnabled(req.getEnabled(), entity.getEnabled(), creating));
        String status = normalizeConditionStatus(req.getStatus(), entity.getStatus(), conditionSource, creating, definitionChanged);
        entity.setStatus(status);
        applyReviewMetadata(entity, req, now, creating, definitionChanged, conditionSource, status);
    }

    private String normalizeConditionSource(String requestValue, String existingValue) {
        String source = StrUtil.blankToDefault(requestValue, existingValue);
        if (StrUtil.isBlank(source)) {
            return PermissionConstants.CONDITION_SOURCE_CUSTOM;
        }
        String normalized = source.trim().toUpperCase();
        if (PermissionConstants.CONDITION_SOURCE_PRESET.equals(normalized)) {
            return PermissionConstants.CONDITION_SOURCE_PRESET;
        }
        return PermissionConstants.CONDITION_SOURCE_CUSTOM;
    }

    private String normalizeConditionStatus(String requestValue, String existingValue, String conditionSource,
                                            boolean creating, boolean definitionChanged) {
        if (PermissionConstants.CONDITION_SOURCE_PRESET.equals(conditionSource)) {
            return PermissionConstants.CONDITION_STATUS_APPROVED;
        }
        if (creating) {
            return PermissionConstants.CONDITION_STATUS_PENDING;
        }
        if (definitionChanged) {
            return PermissionConstants.CONDITION_STATUS_PENDING;
        }
        String status = StrUtil.blankToDefault(requestValue, existingValue);
        if (StrUtil.isBlank(status)) {
            return PermissionConstants.CONDITION_STATUS_PENDING;
        }
        String normalized = status.trim().toUpperCase();
        if (PermissionConstants.CONDITION_STATUS_APPROVED.equals(normalized)
            || PermissionConstants.CONDITION_STATUS_REJECTED.equals(normalized)) {
            return normalized;
        }
        return PermissionConstants.CONDITION_STATUS_PENDING;
    }

    private PermissionConditionVo toVo(PcPermissionCondition e) {
        PermissionConditionVo vo = new PermissionConditionVo();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }

    private void validateSaveRequest(ConditionSaveReq req, PcPermissionCondition existing) {
        if (StrUtil.isBlank(req.getCode()) || StrUtil.isBlank(req.getName())) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "condition code/name missing");
        }
        assertValidConditionSource(req.getConditionSource());
        assertValidConditionStatus(req.getStatus());
        String conditionSource = normalizeConditionSource(req.getConditionSource(), existing == null ? null : existing.getConditionSource());
        if (existing != null && StrUtil.isNotBlank(req.getConditionSource())
            && !conditionSource.equals(existing.getConditionSource())) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "conditionSource cannot change");
        }
        if (PermissionConstants.CONDITION_SOURCE_PRESET.equals(conditionSource)) {
            validatePresetCondition(req, existing);
            return;
        }
        validateCustomCondition(req, existing);
    }

    private void validatePresetCondition(ConditionSaveReq req, PcPermissionCondition existing) {
        if (StrUtil.isNotBlank(req.getStatus())
            && !PermissionConstants.CONDITION_STATUS_APPROVED.equals(req.getStatus().trim().toUpperCase())) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "preset condition must stay APPROVED");
        }
        String handlerCode = resolvePersistedExpression(req, existing, PermissionConstants.CONDITION_SOURCE_PRESET);
        if (StrUtil.isBlank(handlerCode) || presetHandlerRegistry.find(handlerCode).isEmpty()) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "preset handler not registered");
        }
    }

    private void validateCustomCondition(ConditionSaveReq req, PcPermissionCondition existing) {
        if (StrUtil.isBlank(req.getExpression())) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "custom expression is blank");
        }
        try {
            expressionEvaluator.validateExpression(req.getExpression());
        } catch (Exception ex) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "invalid custom expression");
        }
        boolean definitionChanged = existing != null
            && isDefinitionChanged(existing, req, resolvePersistedExpression(req, existing, PermissionConstants.CONDITION_SOURCE_CUSTOM));
        boolean reviewRequested = StrUtil.isNotBlank(req.getStatus());
        String status = normalizeConditionStatus(req.getStatus(), existing == null ? null : existing.getStatus(),
            PermissionConstants.CONDITION_SOURCE_CUSTOM, existing == null, definitionChanged);
        if (existing == null && reviewRequested
            && !PermissionConstants.CONDITION_STATUS_PENDING.equals(status)) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "custom condition must start as PENDING");
        }
        if (!definitionChanged && reviewRequested && (PermissionConstants.CONDITION_STATUS_APPROVED.equals(status)
            || PermissionConstants.CONDITION_STATUS_REJECTED.equals(status))
            && req.getReviewedBy() == null) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "reviewedBy is required");
        }
    }

    private boolean isDefinitionChanged(PcPermissionCondition existing, ConditionSaveReq req, String persistedExpression) {
        return !StrUtil.equals(StrUtil.nullToEmpty(existing.getCode()), StrUtil.nullToEmpty(req.getCode()).trim())
            || !StrUtil.equals(StrUtil.nullToEmpty(existing.getName()), StrUtil.nullToEmpty(req.getName()).trim())
            || !StrUtil.equals(StrUtil.nullToEmpty(existing.getExpression()), StrUtil.nullToEmpty(persistedExpression))
            || !StrUtil.equals(StrUtil.nullToEmpty(existing.getDescription()), StrUtil.nullToEmpty(toNullable(req.getDescription())));
    }

    private String resolvePersistedExpression(ConditionSaveReq req, PcPermissionCondition existing, String conditionSource) {
        if (PermissionConstants.CONDITION_SOURCE_PRESET.equals(conditionSource)) {
            String handlerCode = req.getExpression();
            if (StrUtil.isBlank(handlerCode) && existing != null) {
                handlerCode = existing.getExpression();
            }
            return StrUtil.blankToDefault(handlerCode, req.getCode()).trim();
        }
        return req.getExpression() == null ? null : req.getExpression().trim();
    }

    private void applyReviewMetadata(PcPermissionCondition entity, ConditionSaveReq req, LocalDateTime now,
                                     boolean creating, boolean definitionChanged, String conditionSource, String status) {
        Long existingReviewedBy = entity.getReviewedBy();
        LocalDateTime existingReviewedAt = entity.getReviewedAt();
        if (PermissionConstants.CONDITION_SOURCE_PRESET.equals(conditionSource)) {
            entity.setReviewedBy(existingReviewedBy);
            entity.setReviewedAt(existingReviewedAt);
            return;
        }
        if (PermissionConstants.CONDITION_STATUS_PENDING.equals(status)) {
            entity.setReviewedBy(null);
            entity.setReviewedAt(null);
            return;
        }
        boolean reviewRequested = StrUtil.isNotBlank(req.getStatus());
        boolean terminalStatus = PermissionConstants.CONDITION_STATUS_APPROVED.equals(status)
            || PermissionConstants.CONDITION_STATUS_REJECTED.equals(status);
        boolean reviewAction = !creating && !definitionChanged && reviewRequested && terminalStatus;
        if (reviewAction) {
            entity.setReviewedBy(req.getReviewedBy());
            entity.setReviewedAt(now);
            return;
        }
        entity.setReviewedBy(existingReviewedBy);
        entity.setReviewedAt(existingReviewedAt);
    }

    private PcPermissionCondition loadActiveCondition(Long tenantId, Long conditionId) {
        PcPermissionCondition entity = mapper.selectOne(new LambdaQueryWrapper<PcPermissionCondition>()
            .eq(PcPermissionCondition::getTenantId, tenantId)
            .eq(PcPermissionCondition::getId, conditionId)
            .eq(PcPermissionCondition::getDeleteFlag, PermissionConstants.NOT_DELETED));
        if (entity == null) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "condition not found");
        }
        return entity;
    }

    private ConditionSaveReq mergeUpdateRequest(Long conditionId, ConditionUpdateReq req, PcPermissionCondition existing) {
        ConditionSaveReq merged = new ConditionSaveReq();
        merged.setId(conditionId);
        merged.setTenantId(req.getTenantId());
        merged.setCode(resolveUpdatedRequiredValue(req.getCode(), existing.getCode(), "code"));
        merged.setName(resolveUpdatedRequiredValue(req.getName(), existing.getName(), "name"));
        merged.setConditionSource(resolveUpdatedOptionalValue(req.getConditionSource(), existing.getConditionSource(), "conditionSource"));
        merged.setExpression(req.getExpression() != null ? req.getExpression() : existing.getExpression());
        merged.setStatus(req.getStatus());
        merged.setEnabled(req.getEnabled());
        merged.setDescription(req.getDescription() != null ? req.getDescription() : existing.getDescription());
        merged.setReviewedBy(req.getReviewedBy());
        return merged;
    }

    private String resolveUpdatedRequiredValue(String requestValue, String existingValue, String fieldName) {
        if (requestValue == null) {
            return existingValue;
        }
        if (requestValue.isBlank()) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, fieldName + " is blank");
        }
        return requestValue;
    }

    private String resolveUpdatedOptionalValue(String requestValue, String existingValue, String fieldName) {
        if (requestValue == null) {
            return existingValue;
        }
        if (requestValue.isBlank()) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, fieldName + " is blank");
        }
        return requestValue;
    }

    private String toNullable(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private Boolean resolveEnabled(Boolean requestedEnabled, Boolean existingEnabled, boolean creating) {
        if (requestedEnabled != null) {
            return requestedEnabled;
        }
        if (!creating && existingEnabled != null) {
            return existingEnabled;
        }
        return PermissionConstants.CONDITION_ENABLED;
    }

    private void assertValidConditionSource(String conditionSource) {
        if (StrUtil.isBlank(conditionSource)) {
            return;
        }
        String normalized = conditionSource.trim().toUpperCase();
        if (!PermissionConstants.CONDITION_SOURCE_PRESET.equals(normalized)
            && !PermissionConstants.CONDITION_SOURCE_CUSTOM.equals(normalized)) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "invalid conditionSource");
        }
    }

    private void assertValidConditionStatus(String status) {
        if (StrUtil.isBlank(status)) {
            return;
        }
        String normalized = status.trim().toUpperCase();
        if (!PermissionConstants.CONDITION_STATUS_APPROVED.equals(normalized)
            && !PermissionConstants.CONDITION_STATUS_PENDING.equals(normalized)
            && !PermissionConstants.CONDITION_STATUS_REJECTED.equals(normalized)) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "invalid condition status");
        }
    }

    private void assertValidTenantRequest(Long tenantId) {
        if (tenantId == null) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "tenantId missing");
        }
    }
}
