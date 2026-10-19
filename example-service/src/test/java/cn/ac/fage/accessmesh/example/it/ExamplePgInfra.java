package cn.ac.fage.accessmesh.example.it;

import cn.ac.fage.accessmesh.testsupport.PgTestInfra;
import java.nio.file.Path;

/** PG-only test facade; no IAM template, tenant seed or Redis dependency. */
public final class ExamplePgInfra {
    public static final Path DDL = Path.of("..", "docs", "design", "schema", "example-service.sql");
    public static final PgTestInfra PG = new PgTestInfra("example", DDL, null);
    private ExamplePgInfra() {}
}
