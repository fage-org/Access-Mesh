package cn.ac.fage.accessmesh.access.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 索引与公开错误码枚举、章节锚点对账；不复制一份容易漏改的码值清单。 */
class ErrorCodeIndexTest {
    @Test
    void everyDefinedCodeHasAnIndexEntryAndEverySectionLinkResolves() throws Exception {
        Path root = Path.of("..");
        var codes = new LinkedHashSet<String>();
        var definition = Pattern.compile("(?m)^\\s*[A-Z][A-Z_0-9]*\\((\\d+),");
        for (String file : List.of(
                "common/src/main/java/cn/ac/fage/accessmesh/common/enums/GlobalErrorCode.java",
                "access-service/src/main/java/cn/ac/fage/accessmesh/access/infrastructure/enums/AccessErrorCode.java",
                "example-service/src/main/java/cn/ac/fage/accessmesh/example/enums/ExampleErrorCode.java",
                "perm-sdk/perm-client-spring-boot-starter/src/main/java/cn/ac/fage/accessmesh/perm/client/enums/PermClientErrorCode.java")) {
            var matcher = definition.matcher(Files.readString(root.resolve(file)));
            int count = 0;
            while (matcher.find()) {
                count++;
                assertThat(codes.add(matcher.group(1))).as("码值只能有一个定义：%s", matcher.group(1)).isTrue();
            }
            assertThat(count).as("定义源可解析：%s", file).isPositive();
        }
        String doc = Files.readString(root.resolve("docs/design/access-service-api-contract.md"));
        var block = Pattern.compile("(?s)<!-- error-code-index:start -->(.*?)<!-- error-code-index:end -->").matcher(doc);
        String index = block.find() ? block.group(1) : "";
        var indexed = new LinkedHashSet<String>();
        var rows = Pattern.compile("(?m)^\\| `(\\d+)` \\|").matcher(index);
        while (rows.find()) assertThat(indexed.add(rows.group(1))).as("索引不重复").isTrue();
        assertThat(indexed).containsExactlyInAnyOrderElementsOf(codes);
        var links = Pattern.compile("\\]\\(#(contract-section-[\\d-]+)\\)").matcher(index);
        int linkCount = 0;
        while (links.find()) {
            linkCount++;
            assertThat(doc).as("索引章节锚点 %s", links.group(1))
                .contains("<a id=\"" + links.group(1) + "\"></a>");
        }
        assertThat(linkCount).isGreaterThanOrEqualTo(codes.size());
    }
}
