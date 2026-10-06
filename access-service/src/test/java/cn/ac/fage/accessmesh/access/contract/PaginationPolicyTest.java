package cn.ac.fage.accessmesh.access.contract;

import cn.ac.fage.accessmesh.access.infrastructure.util.PageUtil;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class PaginationPolicyTest {
    static Stream<Class<?>> dedicatedRequests() {
        return Stream.of(cn.ac.fage.accessmesh.common.model.PageReq.class,
            cn.ac.fage.accessmesh.access.user.dto.req.UserPageReq.class,
            cn.ac.fage.accessmesh.access.user.dto.req.MemberCandidatesReq.class,
            cn.ac.fage.accessmesh.access.org.dto.req.OrgPageReq.class,
            cn.ac.fage.accessmesh.access.platform.dto.req.FilePageReq.class,
            cn.ac.fage.accessmesh.access.platform.dto.req.JobLogPageReq.class,
            cn.ac.fage.accessmesh.access.auth.dto.Oauth2ClientPageReq.class);
    }

    @ParameterizedTest @MethodSource("dedicatedRequests")
    void allDedicatedDtosAccept200AndReject201(Class<?> type) throws Exception {
        RecordComponent[] fields = type.getRecordComponents();
        Object[] values = new Object[fields.length];
        int sizeIndex = -1;
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].getName().equals("pageNum")) values[i] = 1;
            if (fields[i].getName().equals("pageSize")) { sizeIndex = i; values[i] = 200; }
        }
        var constructor = type.getDeclaredConstructor(Arrays.stream(fields).map(RecordComponent::getType).toArray(Class<?>[]::new));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validateProperty(constructor.newInstance(values), "pageSize")).isEmpty();
            values[sizeIndex] = 201;
            assertThat(validator.validateProperty(constructor.newInstance(values), "pageSize")).isNotEmpty();
        }
    }

    @Test void offsetRejectsOverflowInsteadOfPassingNegativeSqlOffset() {
        assertThatThrownBy(() -> PageUtil.offset(Integer.MAX_VALUE, 200))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void utilityRejectsOversizeInsteadOfSilentlyTruncating() {
        assertThat(PageUtil.pageSize(200)).isEqualTo(200);
        assertThatThrownBy(() -> PageUtil.pageSize(201)).isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0,20,21,true", "0,20,20,false", "20,0,20,false", "0,0,0,false", "2147483640,20,2147483647,false"})
    void hasNextDistinguishesFullMiddleLastAndEmptyPages(int offset, int fetched, long total, boolean next) {
        assertThat(PageUtil.hasNext(offset, fetched, total)).isEqualTo(next);
    }
}
