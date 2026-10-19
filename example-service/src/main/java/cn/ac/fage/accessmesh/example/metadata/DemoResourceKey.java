package cn.ac.fage.accessmesh.example.metadata;

/** Structural resource identity; codeType is normalized before constructing the key. */
public record DemoResourceKey(String type,String code,String codeType) {}
