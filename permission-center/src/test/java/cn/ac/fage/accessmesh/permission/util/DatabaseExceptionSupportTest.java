package cn.ac.fage.accessmesh.permission.util;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseExceptionSupportTest {

    @Test
    void shouldRecognizePostgresUniqueViolationBySqlState() {
        SQLException cause = new SQLException("driver-specific text", "23505");

        assertTrue(DatabaseExceptionSupport.isUniqueViolation(
            new IllegalStateException("wrapped", cause), "renamed_constraint"));
    }

    @Test
    void shouldFallbackToConstraintNameWhenSqlExceptionIsUnavailable() {
        IllegalStateException cause = new IllegalStateException(
            "duplicate key violates constraint uk_role_resource_permission_manual_direct");

        assertTrue(DatabaseExceptionSupport.isUniqueViolation(
            cause, "uk_role_resource_permission_manual_direct"));
    }

    @Test
    void shouldNotTreatOtherSqlStatesAsUniqueViolation() {
        SQLException cause = new SQLException("foreign key violation", "23503");

        assertFalse(DatabaseExceptionSupport.isUniqueViolation(
            cause, "uk_role_resource_permission_manual_direct"));
    }
}
