package org.dromara.permission.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.dromara.permission.domain.dto.ConflictDetectReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.dto.PermissionCheckReq;
import org.dromara.permission.domain.dto.RolePermissionAddReq;
import org.dromara.permission.domain.dto.SyncUsersReq;
import org.dromara.permission.domain.dto.UserRoleAssignReq;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Controller Request DTO Validation Tests")
@Tag("dev")
class ControllerValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    // ── PermissionCheckReq ──────────────────────────────────────────────

    @Test
    @DisplayName("PermissionCheckReq: missing tenantId → violation")
    void checkReq_missingTenantId_hasViolation() {
        PermissionCheckReq req = new PermissionCheckReq();
        req.setUserId(1L);
        req.setResourceEntityId(2L);
        req.setOperationPermissionId(3L);

        Set<ConstraintViolation<PermissionCheckReq>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("PermissionCheckReq: all required fields present → no violation")
    void checkReq_allFieldsPresent_noViolation() {
        PermissionCheckReq req = new PermissionCheckReq();
        req.setTenantId(1L);
        req.setUserId(2L);
        req.setResourceEntityId(3L);
        req.setOperationPermissionId(4L);

        Set<ConstraintViolation<PermissionCheckReq>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }

    // ── SyncUsersReq ────────────────────────────────────────────────────

    @Test
    @DisplayName("SyncUsersReq: null items → violation")
    void syncUsersReq_nullItems_hasViolation() {
        SyncUsersReq req = new SyncUsersReq();
        req.setTenantId(1L);
        req.setUserType(1);
        req.setItems(null);

        Set<ConstraintViolation<SyncUsersReq>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("SyncUsersReq: item with empty externalId → nested violation")
    void syncUsersReq_emptyItemExternalId_hasViolation() {
        SyncUsersReq.SyncUserItem item = new SyncUsersReq.SyncUserItem();
        item.setExternalId("");

        SyncUsersReq req = new SyncUsersReq();
        req.setTenantId(1L);
        req.setUserType(1);
        req.setItems(List.of(item));

        Set<ConstraintViolation<SyncUsersReq>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("SyncUsersReq: null tenantId → violation")
    void syncUsersReq_nullTenantId_hasViolation() {
        SyncUsersReq.SyncUserItem item = new SyncUsersReq.SyncUserItem();
        item.setExternalId("ext-1");

        SyncUsersReq req = new SyncUsersReq();
        req.setUserType(1);
        req.setItems(List.of(item));

        Set<ConstraintViolation<SyncUsersReq>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    // ── IdsReq ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("IdsReq: null tenantId → violation")
    void idsReq_nullTenantId_hasViolation() {
        IdsReq req = new IdsReq();
        req.setIds(List.of(1L));

        Set<ConstraintViolation<IdsReq>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("IdsReq: empty ids list → violation")
    void idsReq_emptyIds_hasViolation() {
        IdsReq req = new IdsReq();
        req.setTenantId(1L);
        req.setIds(Collections.emptyList());

        Set<ConstraintViolation<IdsReq>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("IdsReq: ids exceed 100 → violation")
    void idsReq_idsExceed100_hasViolation() {
        List<Long> oversized = new ArrayList<>(LongStream.rangeClosed(1, 101).boxed().toList());

        IdsReq req = new IdsReq();
        req.setTenantId(1L);
        req.setIds(oversized);

        Set<ConstraintViolation<IdsReq>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("IdsReq: valid request → no violation")
    void idsReq_valid_noViolation() {
        IdsReq req = new IdsReq();
        req.setTenantId(1L);
        req.setIds(List.of(10L, 20L, 30L));

        Set<ConstraintViolation<IdsReq>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }

    // ── UserRoleAssignReq ───────────────────────────────────────────────

    @Test
    @DisplayName("UserRoleAssignReq: null abstractUserId → violation")
    void assignReq_nullUserId_hasViolation() {
        UserRoleAssignReq req = new UserRoleAssignReq();
        req.setTenantId(1L);
        req.setRoleIds(List.of(1L));

        Set<ConstraintViolation<UserRoleAssignReq>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("UserRoleAssignReq: empty roleIds → violation")
    void assignReq_emptyRoleIds_hasViolation() {
        UserRoleAssignReq req = new UserRoleAssignReq();
        req.setTenantId(1L);
        req.setAbstractUserId(1L);
        req.setRoleIds(Collections.emptyList());

        Set<ConstraintViolation<UserRoleAssignReq>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    // ── RolePermissionAddReq ────────────────────────────────────────────

    @Test
    @DisplayName("RolePermissionAddReq: null abstractRoleId → violation")
    void addReq_nullRoleId_hasViolation() {
        RolePermissionAddReq.RolePermissionItem item = new RolePermissionAddReq.RolePermissionItem();
        item.setResourceEntityId(1L);
        item.setOperationPermissionId(2L);

        RolePermissionAddReq req = new RolePermissionAddReq();
        req.setTenantId(1L);
        req.setItems(List.of(item));

        Set<ConstraintViolation<RolePermissionAddReq>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("RolePermissionAddReq: empty items → violation")
    void addReq_emptyItems_hasViolation() {
        RolePermissionAddReq req = new RolePermissionAddReq();
        req.setTenantId(1L);
        req.setAbstractRoleId(1L);
        req.setItems(Collections.emptyList());

        Set<ConstraintViolation<RolePermissionAddReq>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("RolePermissionAddReq: item with null resourceEntityId → nested violation")
    void addReq_itemNullResourceId_hasViolation() {
        RolePermissionAddReq.RolePermissionItem item = new RolePermissionAddReq.RolePermissionItem();
        item.setOperationPermissionId(2L);

        RolePermissionAddReq req = new RolePermissionAddReq();
        req.setTenantId(1L);
        req.setAbstractRoleId(1L);
        req.setItems(List.of(item));

        Set<ConstraintViolation<RolePermissionAddReq>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    // ── ConflictDetectReq ───────────────────────────────────────────────

    @Test
    @DisplayName("ConflictDetectReq: null tenantId → violation")
    void detectReq_nullTenantId_hasViolation() {
        ConflictDetectReq req = new ConflictDetectReq();

        Set<ConstraintViolation<ConflictDetectReq>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("ConflictDetectReq: valid minimal (only tenantId) → no violation")
    void detectReq_validMinimal_noViolation() {
        ConflictDetectReq req = new ConflictDetectReq();
        req.setTenantId(1L);

        Set<ConstraintViolation<ConflictDetectReq>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }
}
