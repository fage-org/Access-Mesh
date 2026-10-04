package cn.ac.fage.accessmesh.access.contract;

import cn.ac.fage.accessmesh.access.org.dto.req.OrgCreateReq;
import cn.ac.fage.accessmesh.access.org.dto.req.OrgUpdateReq;
import cn.ac.fage.accessmesh.access.platform.dto.req.NoticeCreateReq;
import cn.ac.fage.accessmesh.access.platform.dto.req.NoticeUpdateReq;
import cn.ac.fage.accessmesh.access.user.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.access.user.dto.req.UserUpdateReq;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessFieldValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    record FieldLimit(Class<?> dto, String field, int limit) {}

    static Stream<FieldLimit> fieldLimits() {
        return Stream.of(
            new FieldLimit(UserCreateReq.class, "username", 64),
            new FieldLimit(UserCreateReq.class, "name", 128),
            new FieldLimit(UserCreateReq.class, "phone", 32),
            new FieldLimit(UserCreateReq.class, "email", 128),
            new FieldLimit(UserUpdateReq.class, "name", 128),
            new FieldLimit(UserUpdateReq.class, "phone", 32),
            new FieldLimit(UserUpdateReq.class, "email", 128),
            new FieldLimit(OrgCreateReq.class, "orgName", 128),
            new FieldLimit(OrgCreateReq.class, "code", 64),
            new FieldLimit(OrgUpdateReq.class, "orgName", 128),
            new FieldLimit(OrgUpdateReq.class, "code", 64),
            new FieldLimit(NoticeCreateReq.class, "title", 256),
            new FieldLimit(NoticeUpdateReq.class, "title", 256)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fieldLimits")
    void acceptsColumnLimitAndRejectsOverflow(FieldLimit field) {
        var validator = FACTORY.getValidator();
        assertThat(validator.validateValue(field.dto(), field.field(), "a".repeat(field.limit()))).isEmpty();
        assertThat(validator.validateValue(field.dto(), field.field(), "a".repeat(field.limit() + 1)))
            .singleElement().satisfies(v -> assertThat(v.getPropertyPath().toString()).isEqualTo(field.field()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", "\u3000"})
    void blankOrganizationUpdateIsRejected(String name) {
        assertThat(FACTORY.getValidator().validate(new OrgUpdateReq(1L, name, null, null, null, null)))
            .singleElement().satisfies(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("orgName"));
    }

    @Test
    void omittedOrganizationNameStillMeansNoUpdate() {
        assertThat(FACTORY.getValidator().validate(new OrgUpdateReq(1L, null, null, null, null, null))).isEmpty();
    }
}
