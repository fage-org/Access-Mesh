package cn.ac.fage.accessmesh.access.infrastructure;

import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.common.security.RedisTenantGateStore;
import cn.ac.fage.accessmesh.common.security.TenantGateProtocol;
import cn.ac.fage.accessmesh.common.security.TenantGateState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
class TenantGateRedisIT {
    private static final long TENANT_A = 70101L;
    private static final long TENANT_B = 70102L;
    private static LettuceConnectionFactory connections;
    private static StringRedisTemplate redis;
    private static RedisTenantGateStore gates;

    @BeforeAll
    static void connect() {
        ItInfra.prepare(TenantGateRedisIT.class, true);
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(ItInfra.redisHost(), ItInfra.redisPort());
        config.setPassword(ItInfra.REDIS_TEST_PASSWORD);
        config.setDatabase(ItInfra.redisDatabase(TenantGateRedisIT.class));
        connections = new LettuceConnectionFactory(config);
        connections.afterPropertiesSet();
        connections.start();
        redis = new StringRedisTemplate(connections);
        gates = new RedisTenantGateStore(redis);
    }

    @AfterAll
    static void disconnect() {
        if (connections != null) connections.destroy();
    }

    @BeforeEach
    void resetKeys() {
        redis.delete(List.of(TenantGateProtocol.key(TENANT_A), TenantGateProtocol.key(TENANT_B)));
    }

    @Test
    void nativeSnapshotUsesActualRedisProcessRatherThanPersistedRecordIdentity() {
        var publication=gates.reserve(TENANT_A);
        assertThat(gates.publish(publication,enabled(7))).isTrue();
        var snapshot=gates.readForSession(TENANT_A);
        assertThat(snapshot.redisProcessId()).isEqualTo(publication.processId());
        assertThat(snapshot.state().permitsSession(7)).isTrue();
        String oldProcess="0".repeat(40);
        assertThat(publication.processId()).isNotEqualTo(oldProcess);
        redis.opsForValue().set(TenantGateProtocol.key(TENANT_A),oldProcess+"|ENABLED|old-publication|7");
        var restored=gates.readForSession(TENANT_A);
        assertThat(restored.redisProcessId()).isEqualTo(publication.processId());
        assertThat(restored.state().permitsService()).isFalse();
    }

    @Test
    void shouldBlockUntilPublishedAndInvalidateOldSessionsAfterRestore() {
        assertThat(gates.read(TENANT_A).permitsService()).isFalse();
        var initial = gates.reserve(TENANT_A);
        assertThat(gates.read(TENANT_A).permitsService()).isFalse();
        assertThat(gates.publish(initial, enabled(7))).isTrue();
        assertThat(gates.read(TENANT_A).permitsSession(7)).isTrue();

        var suspend = gates.reserve(TENANT_A);
        assertThat(gates.read(TENANT_A).permitsSession(7)).isFalse();
        assertThat(gates.publish(suspend, new TenantGateState(TenantGateState.Status.DISABLED, 8))).isTrue();
        assertThat(gates.read(TENANT_A).status()).isEqualTo(TenantGateState.Status.DISABLED);
        var restore = gates.reserve(TENANT_A);
        assertThat(gates.publish(restore, enabled(8))).isTrue();
        assertThat(gates.read(TENANT_A).permitsService()).isTrue();
        assertThat(gates.read(TENANT_A).permitsSession(7)).isFalse();
        assertThat(gates.read(TENANT_A).permitsSession(8)).isTrue();
    }

    @Test
    void shouldNotLetOldPublisherOverwriteNewSuspension() {
        var oldRestore = gates.reserve(TENANT_A);
        var suspension = gates.reserve(TENANT_A);
        assertThat(gates.publish(suspension, new TenantGateState(TenantGateState.Status.DISABLED, 9))).isTrue();
        assertThat(gates.publish(oldRestore, enabled(8))).isFalse();
        assertThat(gates.read(TENANT_A).permitsService()).isFalse();
    }

    @Test
    void shouldNotRecreateMissingGateWithOldPublication() {
        var publication = gates.reserve(TENANT_A);
        redis.delete(TenantGateProtocol.key(TENANT_A));
        assertThat(gates.publish(publication, enabled(1))).isFalse();
        assertThat(gates.read(TENANT_A).permitsService()).isFalse();
    }

    @Test
    void shouldRejectPersistedStateAndPublicationFromAnotherRedisProcess() {
        var current = gates.reserve(TENANT_A);
        var stale = new RedisTenantGateStore.Publication(TENANT_A, "0".repeat(40), current.token());
        redis.opsForValue().set(TenantGateProtocol.key(TENANT_A), stale.processId() + "|ENABLED|" + stale.token() + "|1");
        assertThat(gates.read(TENANT_A).permitsService()).isFalse();
        assertThat(gates.publish(stale, enabled(1))).isFalse();
        assertThat(gates.publish(gates.reserve(TENANT_A), enabled(2))).isTrue();
        assertThat(gates.read(TENANT_A).permitsSession(2)).isTrue();
    }

    @Test
    void shouldBindPublicationToItsTenantAndConsumeItOnce() {
        var a = gates.reserve(TENANT_A);
        var b = gates.reserve(TENANT_B);
        var wrongTenant = new RedisTenantGateStore.Publication(TENANT_B, a.processId(), a.token());
        assertThat(gates.publish(wrongTenant, enabled(1))).isFalse();
        assertThat(gates.read(TENANT_B).permitsService()).isFalse();
        assertThat(gates.publish(a, enabled(1))).isTrue();
        assertThat(gates.publish(b, enabled(2))).isTrue();
        assertThat(gates.publish(a, enabled(3))).isFalse();
        assertThat(gates.read(TENANT_A).permitsSession(1)).isTrue();
        assertThat(gates.read(TENANT_B).permitsSession(2)).isTrue();
    }

    @Test
    void shouldRetainLongGenerationWithoutLuaNumericRounding() {
        long epoch = 9007199254740993L;
        assertThat(gates.publish(gates.reserve(TENANT_A), enabled(epoch))).isTrue();
        assertThat(gates.read(TENANT_A).permitsSession(epoch)).isTrue();
        assertThat(gates.read(TENANT_A).permitsSession(epoch - 1)).isFalse();
    }

    @Test
    void shouldDenyMalformedRecordsEvenWhenTheirProcessMatches() {
        var publication = gates.reserve(TENANT_A);
        assertThat(gates.publish(publication, enabled(1))).isTrue();
        assertThat(gates.read(TENANT_A).permitsService()).isTrue();
        for (String suffix : List.of("ENABLED|token|0", "ENABLED|token|bad", "UNKNOWN|token|1",
            "ENABLED|token|1|extra", "ENABLED||1", "ENABLED|token|9223372036854775808")) {
            redis.opsForValue().set(TenantGateProtocol.key(TENANT_A), publication.processId() + "|" + suffix);
            assertThat(gates.read(TENANT_A).permitsService()).as(suffix).isFalse();
        }
    }

    private static TenantGateState enabled(long epoch) {
        return new TenantGateState(TenantGateState.Status.ENABLED, epoch);
    }

    @Test
    void shouldReadMixedTenantStatesInOneBatchAndDenyWrongRedisTypes() {
        assertThat(gates.publish(gates.reserve(TENANT_A),enabled(3))).isTrue();
        redis.opsForHash().put(TenantGateProtocol.key(TENANT_B),"invalid","record");
        var states=gates.readBatch(List.of(TENANT_A,TENANT_B,TENANT_A));
        assertThat(states).hasSize(2);
        assertThat(states.get(TENANT_A).permitsSession(3)).isTrue();
        assertThat(states.get(TENANT_B).status()).isEqualTo(TenantGateState.Status.UNAVAILABLE);
        assertThat(gates.read(TENANT_B).permitsService()).isFalse();
    }
}
