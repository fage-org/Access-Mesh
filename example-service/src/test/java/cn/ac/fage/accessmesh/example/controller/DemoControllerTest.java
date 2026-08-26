package cn.ac.fage.accessmesh.example.controller;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.dto.DemoHelloReq;
import cn.ac.fage.accessmesh.example.dto.DemoHelloResp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 演示接口单元测试：参数校验（3xxxx 错误码）、身份头缺失防御与身份回显。
 */
class DemoControllerTest {

    private final DemoController controller = new DemoController();

    @Test
    @DisplayName("合法请求：返回问候语并回显 Gateway 注入的身份头")
    void hello_echoesGatewayIdentityHeaders() {
        DemoHelloResp resp = controller.hello(new DemoHelloReq("AccessMesh"), "42", "1").getData();

        assertThat(resp.greeting()).isEqualTo("hello, AccessMesh");
        assertThat(resp.userId()).isEqualTo("42");
        assertThat(resp.tenantId()).isEqualTo("1");
    }

    @Test
    @DisplayName("name 为空白：拒绝并返回 30001（example 业务域错误码段）")
    void blankName_rejectedWith30001() {
        assertThatThrownBy(() -> controller.hello(new DemoHelloReq("  "), "42", "1"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(30001));

        assertThatThrownBy(() -> controller.hello(new DemoHelloReq(null), "42", "1"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(30001));

        assertThatThrownBy(() -> controller.hello(null, "42", "1"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(30001));
    }

    @Test
    @DisplayName("身份头缺失（未走 Gateway 链路）：拒绝并返回 30002")
    void missingIdentityHeaders_rejectedWith30002() {
        assertThatThrownBy(() -> controller.hello(new DemoHelloReq("AccessMesh"), null, "1"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(30002));

        assertThatThrownBy(() -> controller.hello(new DemoHelloReq("AccessMesh"), "42", null))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(30002));
    }
}
