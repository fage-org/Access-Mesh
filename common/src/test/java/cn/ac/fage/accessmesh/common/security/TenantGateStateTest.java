package cn.ac.fage.accessmesh.common.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TenantGateStateTest {

    @Test
    void shouldSeparateSessionGenerationFromServiceAvailability() {
        TenantGateState active = TenantGateState.fromWire("ENABLED|7");
        assertThat(active.permitsService()).isTrue();
        assertThat(active.permitsSession(7)).isTrue();
        assertThat(active.permitsSession(6)).isFalse();
        assertThat(active.permitsSession(8)).isFalse();

        TenantGateState disabled = TenantGateState.fromWire("DISABLED|8");
        assertThat(disabled.status()).isEqualTo(TenantGateState.Status.DISABLED);
        assertThat(disabled.permitsService()).isFalse();
        assertThat(disabled.permitsSession(8)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ENABLED", "ENABLED|0", "ENABLED|-1", "ENABLED|bad",
        "ENABLED|9223372036854775808", "ENABLED|1|extra", "enabled|1", "BLOCKED|1",
        "UNKNOWN|1", "UNAVAILABLE|0", "DISABLED|0"})
    void shouldDenyMalformedOrUnknownState(String wire) {
        TenantGateState state = TenantGateState.fromWire(wire);
        assertThat(state.status()).isEqualTo(TenantGateState.Status.UNAVAILABLE);
        assertThat(state.permitsService()).isFalse();
        assertThat(state.permitsSession(1)).isFalse();
    }

    @Test
    void shouldPreserveExactGenerationBeyondLuaIntegerPrecision() {
        TenantGateState state = TenantGateState.fromWire("ENABLED|9007199254740993");
        assertThat(state.permitsSession(9007199254740993L)).isTrue();
        assertThat(state.permitsSession(9007199254740992L)).isFalse();
    }
    @Test
    void sessionSnapshotKeepsActualProcessAlongsideTenantGeneration() {
        var snapshot=TenantGateSnapshot.fromWire("a".repeat(40)+"|ENABLED|7");
        assertThat(snapshot.redisProcessId()).isEqualTo("a".repeat(40));
        assertThat(snapshot.state().permitsSession(7)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings={"ENABLED|7","a|ENABLED|7","AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA|ENABLED|7"})
    void malformedProcessCannotAuthorizeNativeSession(String wire) {
        assertThat(TenantGateSnapshot.fromWire(wire).state().permitsService()).isFalse();
    }
}
