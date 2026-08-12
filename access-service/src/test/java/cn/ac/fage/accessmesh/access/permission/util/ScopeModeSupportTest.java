package cn.ac.fage.accessmesh.access.permission.util;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopeModeSupportTest {

    @Test
    void shouldMapInternalScopeAllToProtocolScopeMode() {
        assertEquals(ScopeMode.ALL, ScopeModeSupport.fromScopeAll(true));
        assertEquals(ScopeMode.INSTANCE, ScopeModeSupport.fromScopeAll(false));
        assertEquals(ScopeMode.INSTANCE, ScopeModeSupport.fromScopeAll(null));
    }

    @Test
    void shouldValidateGrantScopeMode() {
        assertFalse(ScopeModeSupport.toScopeAllForGrant(ScopeMode.INSTANCE, "res-1", "default"));
        assertTrue(ScopeModeSupport.toScopeAllForGrant(ScopeMode.ALL, null, null));

        assertThrows(BizException.class,
            () -> ScopeModeSupport.toScopeAllForGrant(ScopeMode.INSTANCE, null, null));
        assertThrows(BizException.class,
            () -> ScopeModeSupport.toScopeAllForGrant(ScopeMode.INSTANCE, "res-1", null));
        assertThrows(BizException.class,
            () -> ScopeModeSupport.toScopeAllForGrant(ScopeMode.ALL, "res-1", null));
        assertThrows(BizException.class,
            () -> ScopeModeSupport.toScopeAllForGrant(ScopeMode.DENIED, "res-1", "default"));
    }

    @Test
    void shouldReadScopeModeFromNewOrLegacySnapshot() {
        assertEquals(ScopeMode.ALL, ScopeModeSupport.fromSnapshot("ALL", false));
        assertEquals(ScopeMode.ALL, ScopeModeSupport.fromSnapshot(null, true));
        assertEquals(ScopeMode.INSTANCE, ScopeModeSupport.fromSnapshot(null, false));
    }
}
