package cn.ac.fage.accessmesh.perm.client.feign;

/**
 * SDK client starter 真实接口的同 FQCN 测试桩（codex sol r2 P2-1 处置）。
 * <p>
 * 凭证拦截器的身份门按 {@code MethodMetadata.targetType()} 的<b>全限定类名</b>比对
 * （perm-common 无法类引用两个 SDK 接口——starter 依赖本模块，反向引用成环），
 * 故本模块的注入路径测试以同 FQCN 桩驱动：身份门视角下与真实接口等价。
 * 桩仅存在于测试树（test scope 不外溢），不会与 starter 的真实接口同处一个类路径。
 * 威胁模型备注：身份门防的是「同 contextId 子上下文里无关宿主客户端被意外注入」
 * （宿主自持配置的凭证，蓄意伪造同 FQCN 类的宿主本就能读自己的配置，不在防护面内）。
 * </p>
 */
public interface PermissionFeignClient {

    @feign.RequestLine("POST /stub")
    void stub();
}
