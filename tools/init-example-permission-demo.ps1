[CmdletBinding()]
param(
    [Parameter(Mandatory)][uri]$BaseUrl,
    [switch]$AllowInsecure,
    [ValidatePattern('^[A-Z][A-Z0-9_]*$')][string]$FieldType='DEMO_REPORT_FIELD',
    [ValidatePattern('^[A-Z][A-Z0-9_]*$')][string]$ScopeType='DEMO_DATA_SCOPE',
    [string]$ReportCode='sales-report'
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if ($BaseUrl.Scheme -ne 'https' -and -not ($AllowInsecure -and $BaseUrl.Scheme -eq 'http')) {
    throw 'Use HTTPS, or explicitly select -AllowInsecure for an isolated trusted network.'
}
foreach ($key in @('ADMIN_TOKEN','EXAMPLE_CREDENTIAL_ID','EXAMPLE_CREDENTIAL_SECRET')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($key))) { throw "Missing environment variable $key" }
}
$reserved=@('DEMO_REPORT','DEMO_POLICY','DEMO_DATASET','DEMO_TASK','EXAMPLE')
if ($FieldType -eq $ScopeType -or $FieldType -in $reserved -or $ScopeType -in $reserved) { throw 'Field and scope types must have separate purposes.' }
$adminHeaders=@{Authorization="Bearer $env:ADMIN_TOKEN"}
$serviceHeaders=@{'X-Credential-Id'=$env:EXAMPLE_CREDENTIAL_ID;'X-Credential-Secret'=$env:EXAMPLE_CREDENTIAL_SECRET}
$root=Split-Path -Parent $PSScriptRoot
$manifest=Get-Content -LiteralPath (Join-Path $root 'example-service/examples/permission-demo-interfaces.json') -Raw | ConvertFrom-Json -AsHashtable

function Invoke-DemoApi([string]$Path,$Body,$Headers=$adminHeaders,[int[]]$AllowedCodes=@(200)) {
    $admissionDeadline=[DateTime]::UtcNow.AddSeconds(30)
    try {
        do {
            $response=Invoke-RestMethod ($BaseUrl.AbsoluteUri.TrimEnd('/')+$Path) -Method Post -ContentType 'application/json; charset=utf-8' `
                -Headers $Headers -Body ($Body | ConvertTo-Json -Depth 30 -Compress) -SkipHttpErrorCheck -StatusCodeVariable status -TimeoutSec 30
            $retry=$Path.StartsWith('/api/example/') -and ($status -eq 403 -or ($status -eq 200 -and $response.code -eq 30004)) -and [DateTime]::UtcNow -lt $admissionDeadline
            if ($retry) { Start-Sleep -Milliseconds 250 }
        } while ($retry)
    } catch { throw "Request failed: $Path ($($_.Exception.GetType().Name))" }
    if ($status -ne 200 -or $response.code -notin $AllowedCodes) { throw "Request rejected: $Path HTTP=$status code=$($response.code)" }
    return $response
}

# Check the administrator's tenant view before allowing this credential to send a FULL declaration.
$service=@((Invoke-DemoApi '/api/access/service-config/list' @{}).data.items | Where-Object {$_.serviceCode -ceq 'example-service'})
if ($service.Count -ne 1 -or $service[0].status -ne 1) { throw 'Register and enable example-service before initializing the demonstration.' }
$tenantCredentials=@((Invoke-DemoApi '/api/access/service-credential/list' @{serviceCode='example-service'}).data.items)
$credential=@($tenantCredentials|Where-Object {$_.credentialId -ceq $env:EXAMPLE_CREDENTIAL_ID -and $_.serviceCode -ceq 'example-service' -and $_.status -eq 1})
if ($credential.Count -ne 1) { throw 'The provided service credential is not enabled for example-service in the administrator tenant.' }

# FULL preflight: keep the identities of supported existing routes and reject unknown or altered routes.
$current=(Invoke-DemoApi '/api/access/service-config/apis' @{serviceCode='example-service'}).data.items
foreach ($mapping in $current) {
    $api=@($manifest.groups[0].apis | Where-Object { ('/api/example'+$_.path) -ceq $mapping.pathPattern -and $_.httpMethod -ceq $mapping.httpMethod })
    if ($api.Count -ne 1 -or $mapping.maintainSource -ne 'SERVICE_SYNC' -or -not $mapping.enabled -or $mapping.matchOrder -ne 0) {
        throw 'Existing routes require an explicit complete manifest; no FULL request has been sent.'
    }
    if ($null -eq $mapping.requiredPermission -or
        $api[0].requiredPermission.resourceTypeCode -cne $mapping.requiredPermission.resourceTypeCode -or
        $api[0].requiredPermission.operationCode -cne $mapping.requiredPermission.operationCode) {
        throw 'Existing route permission differs from the demonstration contract.'
    }
    $api[0].resourceCode=$mapping.resourceCode
}

function Ensure-DemoType([string]$Code,[bool]$Sync) {
    $page=1; $found=@()
    do {
        $result=(Invoke-DemoApi '/api/access/type-definition/list' @{typeKey='resource_type';keyword=$Code;pageNum=$page;pageSize=200}).data
        $found+=@($result.items | Where-Object {$_.typeCode -ceq $Code}); $page++
    } while ($result.hasNext)
    if ($found.Count -gt 1) { throw "Ambiguous type $Code" }
    if ($found.Count -eq 0) {
        $extra=if ($Sync) {@{managedMode='SYNC';syncSourceService='example-service'}} else {@{managedMode='MANAGED'}}
        $null=Invoke-DemoApi '/api/access/type-definition/create' @{typeKey='resource_type';typeCode=$Code;name=$Code;extra=($extra|ConvertTo-Json -Compress)}
    } else {
        $extra=if ([string]::IsNullOrWhiteSpace($found[0].extra)) {@{}} else {$found[0].extra | ConvertFrom-Json -AsHashtable}
        $mode=if ($extra.ContainsKey('managedMode')) {$extra.managedMode} else {'MANAGED'}
        if (($Sync -and ($mode -ne 'SYNC' -or -not $extra.ContainsKey('syncSourceService') -or $extra.syncSourceService -ne 'example-service')) -or (-not $Sync -and $mode -ne 'MANAGED')) {
            throw "Existing type $Code has incompatible ownership."
        }
    }
}
function Ensure-DemoOperation([string]$Type,[string]$Code) {
    $ops=@((Invoke-DemoApi '/api/access/operation-permission/list' @{resourceTypeCode=$Type}).data.items)
    $existing=@($ops|Where-Object {$_.code -ceq $Code})
    if ($existing.Count -gt 0) {
        if ($Code -notin @('CREATE','VIEW','UPDATE','DELETE') -and $existing[0].inheritMask -ne 0) { throw "Unexpected inherited permission: $Type/$Code" }
        return
    }
    [long]$bit=1
    while ($bit -in @($ops|ForEach-Object {$_.binaryBit})) {
        if ($bit -ge [long]4611686018427387904) { throw "No operation bit available for $Type" }
        $bit=$bit -shl 1
    }
    $null=Invoke-DemoApi '/api/access/operation-permission/create' @{resourceTypeCode=$Type;code=$Code;name=$Code;binaryBit=$bit;inheritMask=0}
}

Ensure-DemoType 'EXAMPLE' $false
foreach ($op in @('EXPORT','SUB_VIEW')) { Ensure-DemoOperation 'EXAMPLE' $op }
foreach ($type in @('DEMO_REPORT',$FieldType,$ScopeType)) {
    Ensure-DemoType $type $true
    foreach ($op in @('DATA_READ','DATA_EDIT')) { Ensure-DemoOperation $type $op }
}
Ensure-DemoOperation 'DEMO_REPORT' 'EXPORT'
foreach ($type in @('DEMO_POLICY','DEMO_DATASET','DEMO_TASK')) { Ensure-DemoType $type $false }
Ensure-DemoOperation 'DEMO_POLICY' 'MANAGE'
Ensure-DemoOperation 'DEMO_DATASET' 'MANAGE'
foreach ($op in @('ENABLE','TRIGGER')) { Ensure-DemoOperation 'DEMO_TASK' $op }

$domain=(Invoke-DemoApi '/api/access/biz-domain/detail' @{domainCode='EXAMPLE_DEMO'}).data
if ($null -eq $domain) { $null=Invoke-DemoApi '/api/access/biz-domain/create' @{code='EXAMPLE_DEMO';name='权限演示';global=$false} }
$types=@('DEMO_REPORT',$FieldType,$ScopeType,'DEMO_POLICY','DEMO_DATASET','DEMO_TASK')
foreach ($kind in @('CLASSIFY','SUB_PERM')) {
    $config=(Invoke-DemoApi '/api/access/domain-config/detail' @{domainCode='EXAMPLE_DEMO';configType=$kind}).data
    $desired=if ($kind -eq 'CLASSIFY') {@{resourceTypeCodes=$types}} else {@{allowed=@(@{parent_type='DEMO_REPORT';child_types=@($FieldType,$ScopeType)})}}
    if ($null -ne $config) {
        $actual=$config.extra|ConvertFrom-Json -AsHashtable
        if ($kind -eq 'CLASSIFY') {
            $matches=(($actual.resourceTypeCodes|Sort-Object)-join ',') -ceq (($types|Sort-Object)-join ',')
        } else {
            $matches=$actual.allowed.Count -eq 1 -and $actual.allowed[0].parent_type -ceq 'DEMO_REPORT' -and
                (($actual.allowed[0].child_types|Sort-Object)-join ',') -ceq ((@($FieldType,$ScopeType)|Sort-Object)-join ',')
        }
        if (-not $matches) { throw "Existing EXAMPLE_DEMO/$kind configuration differs; it has not been overwritten." }
    } else {
        $null=Invoke-DemoApi '/api/access/domain-config/save' @{domainCode='EXAMPLE_DEMO';configType=$kind;extra=($desired|ConvertTo-Json -Depth 8 -Compress)}
    }
}
$null=Invoke-DemoApi '/api/access/service-config/sync-v2' $manifest $serviceHeaders

function Wait-DemoPublication([string]$Type,[string]$Code) {
    $until=[DateTime]::UtcNow.AddSeconds(90)
    do {
        $event=(Invoke-DemoApi '/api/example/demo-publication/get' @{resourceTypeCode=$Type;resourceCode=$Code;codeType='default'}).data
        if ($event.status -eq 'PUBLISHED') { return }
        if ($event.status -eq 'FAILED') { throw "Publication failed for $Type/$Code ($($event.reason)); correct configuration and use demo-publication/retry." }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $until)
    throw "Publication deadline exceeded for $Type/$Code"
}
$null=Invoke-DemoApi '/api/example/demo-report/create' @{reportCode=$ReportCode;name='销售记录权限演示';datasetCode='sales-records';fieldResourceTypeCode=$FieldType} $adminHeaders @(200,30103)
Wait-DemoPublication 'DEMO_REPORT' $ReportCode
$report=(Invoke-DemoApi '/api/example/demo-report/get' @{reportCode=$ReportCode}).data
if ($report.datasetCode -ne 'sales-records' -or $report.fieldResourceTypeCode -cne $FieldType) { throw 'Existing report identity differs from the requested configuration.' }
$fields=@((Invoke-DemoApi '/api/example/demo-field/list' @{reportCode=$ReportCode;pageNum=1;pageSize=200}).data.items)
$missing=@(@('recordCode','ownerUserId','teamCode','departmentCode','regionCode','saleDate','productName','amount','remark') | Where-Object {$_ -notin @($fields|ForEach-Object {$_.fieldKey})})
if ($missing.Count -gt 0) {
    $null=Invoke-DemoApi '/api/example/demo-field/register' @{reportCode=$ReportCode;expectedCatalogVersion=$report.catalogVersion;fieldKeys=$missing}
    $fields=@((Invoke-DemoApi '/api/example/demo-field/list' @{reportCode=$ReportCode;pageNum=1;pageSize=200}).data.items)
}
foreach ($field in $fields) { Wait-DemoPublication $field.resourceTypeCode $field.resourceCode }
Write-Output "Example metadata ready: report=$ReportCode; fieldType=$FieldType; scopeType=$ScopeType. No business records or reader grants were created."
