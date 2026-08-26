package cn.ac.fage.accessmesh.example.dto;

/**
 * 演示接口 hello 请求体
 *
 * @param name 问候目标名称，非空白
 */
public record DemoHelloReq(String name) {
}
