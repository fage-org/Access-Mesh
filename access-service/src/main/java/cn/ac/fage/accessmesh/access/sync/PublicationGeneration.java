package cn.ac.fage.accessmesh.access.sync;

/** 发布源正整数代次；严格十进制文本，禁止本机时钟或隐式数值截断。 */
public final class PublicationGeneration {
    private PublicationGeneration() {}
    public static long parse(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("PUBLICATION_GENERATION_INVALID");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("PUBLICATION_GENERATION_INVALID", e);
        }
    }
}
