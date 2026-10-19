[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_.-]*$')]
    [string]$Container = 'accessmesh-postgresql'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($env:EXAMPLE_DB_PASSWORD)) {
    throw '请通过 EXAMPLE_DB_PASSWORD 环境变量提供 example_app 的独立密码。'
}
if ($env:EXAMPLE_DB_PASSWORD.Contains([char]10) -or $env:EXAMPLE_DB_PASSWORD.Contains([char]13)) {
    throw '此初始化工具使用 psql 的单行密码输入，不接受换行密码。'
}
$schemaPath = Join-Path $PSScriptRoot '../docs/design/schema/example-service.sql'
if (-not (Test-Path -LiteralPath $schemaPath -PathType Leaf)) {
    throw '找不到 example-service.sql。'
}

function Invoke-ExampleBootstrapSql([string]$Database, [string]$Sql, [switch]$PassThru) {
    # Credentials stay in stdin / the container environment, not command-line arguments or files.
    $command = 'PGPASSWORD="$POSTGRES_PASSWORD" exec psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1"'
    $inputSql = "\set VERBOSITY terse`n" + $Sql
    $captured = $inputSql | & docker exec -i $Container sh -c $command sh $Database 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL 初始化失败（数据库 $Database）。确认容器可用、管理账号有效且 example_db 为空；未输出 SQL 或密码。"
    }
    if ($PassThru) { return ($captured | Out-String).Trim() }
}

$roleExists = Invoke-ExampleBootstrapSql 'postgres' "SELECT EXISTS(SELECT FROM pg_roles WHERE rolname='example_app');" -PassThru
if ($roleExists -notin @('t', 'f')) { throw '无法确认 example_app 是否存在。' }
if ($roleExists -eq 'f') {
    # psql encrypts the prompted password client-side; plaintext never enters SQL/server error logs.
    $createRole = @(
        'BEGIN;'
        "SET LOCAL password_encryption='scram-sha-256';"
        'CREATE ROLE example_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;'
        '\password example_app'
        $env:EXAMPLE_DB_PASSWORD
        $env:EXAMPLE_DB_PASSWORD
        'COMMIT;'
    ) -join "`n"
    Invoke-ExampleBootstrapSql 'postgres' $createRole
    $createRole = $null
}

$provision = @'
DO $bootstrap$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'example_app'
        AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls OR NOT rolcanlogin)) THEN
        RAISE EXCEPTION 'existing example_app has unexpected privileges; refusing to reuse it';
    END IF;
    IF EXISTS (SELECT FROM pg_database d JOIN pg_roles r ON r.oid = d.datdba
        WHERE d.datname = 'example_db' AND r.rolname <> 'example_app') THEN
        RAISE EXCEPTION 'example_db has another owner; refusing to change ownership';
    END IF;
END $bootstrap$;
SELECT 'CREATE DATABASE example_db OWNER example_app'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'example_db')
\gexec
'@
Invoke-ExampleBootstrapSql 'postgres' $provision
$provision = $null

$emptyGuard = @'
DO $guard$
BEGIN
    IF EXISTS (SELECT FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','f','S')) THEN
        RAISE EXCEPTION 'example_db is not empty; this command does not migrate or reset data';
    END IF;
END $guard$;
BEGIN;
SET LOCAL ROLE example_app;
'@
$ddl = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $schemaPath).Path)
Invoke-ExampleBootstrapSql 'example_db' ($emptyGuard + "`n" + $ddl + "`nCOMMIT;")
Write-Host 'example_db 已初始化，业务账号为 example_app；未修改现有角色密码或其他数据库。'
