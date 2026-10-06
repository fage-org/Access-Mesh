package cn.ac.fage.accessmesh.access.contract;

import cn.ac.fage.accessmesh.access.user.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.access.user.dto.resp.UserResp;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class UserOrgTypeWireTest {
    static Stream<Class<?>> userOrgRecords() {
        return Stream.of(UserPageItemResp.OrgBrief.class, UserResp.OrgBrief.class);
    }

    @ParameterizedTest
    @MethodSource("userOrgRecords")
    void orgTypeUsesJsonNumberInBothUserResponses(Class<?> type) throws Exception {
        var fields = type.getRecordComponents();
        // 旧线格式仍可构造，必须由序列化断言暴露 string -> number 的实际差异。
        Object orgType = fields[2].getType() == String.class ? "2" : Integer.valueOf(2);
        var value = type.getDeclaredConstructor(Arrays.stream(fields)
            .map(RecordComponent::getType).toArray(Class<?>[]::new))
            .newInstance(1L, "岗位", orgType, false);
        var json = new ObjectMapper().valueToTree(value);
        assertThat(json.path("orgType").isIntegralNumber()).isTrue();
        assertThat(json.path("orgType").intValue()).isEqualTo(2);
    }
}
