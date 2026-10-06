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

    /**
     * 架构锁：服务层分页偏移与 hasNext 禁止裸 int 内联运算，必须走 PageUtil
     * （long 中间值 + 超限 IllegalArgumentException→400；工程规范 §1.3）。
     * 2026-10-06 复评轮外评 P2：PageUtil 修复后 11 个服务类 25 处调用点仍内联 int 运算，
     * 构造 pageNum 即可溢出为负偏移（PG 500）或回绕返回错误页数据。
     */
    @Test void serviceLayerMustNotComputeIntPaginationInline() throws Exception {
        java.nio.file.Path root = java.nio.file.Path.of("..", "access-service", "src", "main", "java");
        java.util.regex.Pattern inlineOffset = java.util.regex.Pattern.compile(
            "\\(\\s*(?:\\w+\\.)*pageNum(?:\\(\\))?\\s*-\\s*1\\s*\\)\\s*\\*");
        java.util.regex.Pattern inlineHasNext = java.util.regex.Pattern.compile(
            "\\+\\s*\\w+\\.size\\(\\)\\s*<\\s*total\\b");
        int scanned = 0;
        try (var files = java.nio.file.Files.walk(root)) {
            for (var it = files.iterator(); it.hasNext(); ) {
                java.nio.file.Path p = it.next();
                if (!p.toString().endsWith(".java") || p.getFileName().toString().equals("PageUtil.java")) continue;
                String source = java.nio.file.Files.readString(p);
                scanned++;
                assertThat(inlineOffset.matcher(source).find())
                    .as("分页偏移必须走 PageUtil.offset（唯一允许的实现源是 PageUtil 自身）：%s", p).isFalse();
                assertThat(inlineHasNext.matcher(source).find())
                    .as("hasNext 必须走 PageUtil.hasNext（long 加法）：%s", p).isFalse();
            }
        }
        assertThat(scanned).as("扫描面为空即断言失效").isGreaterThan(500);
        // 阳性对照：被禁的两种历史形态都能命中（防止 pattern 写错静默全绿）
        assertThat(inlineOffset.matcher("(pageNum - 1) * pageSize").find()).isTrue();
        assertThat(inlineOffset.matcher("(pageReq.pageNum() - 1) * pageReq.pageSize()").find()).isTrue();
        assertThat(inlineHasNext.matcher("offset + items.size() < total").find()).isTrue();
    }
}
