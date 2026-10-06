param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string] $Project
)

$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$projectRoot = [IO.Path]::GetFullPath((Join-Path $workspaceRoot $Project))
if (-not $projectRoot.StartsWith($workspaceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    -not (Test-Path -LiteralPath (Join-Path $projectRoot 'pom.xml') -PathType Leaf)) {
    throw 'Project 必须是仓库内包含 pom.xml 的模块相对路径，例如 access-service。'
}

# 上游一起 install；不把 compile 当成已刷新 SNAPSHOT，也不复用孤立模块的旧依赖。
Push-Location $workspaceRoot
try {
    & mvn install -pl $Project -am -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Maven 构建失败（exit=$LASTEXITCODE）" }
} finally {
    Pop-Location
}
