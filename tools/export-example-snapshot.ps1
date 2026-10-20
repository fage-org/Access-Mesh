[CmdletBinding()]
param(
    [Parameter(Mandatory)][uri]$BaseUrl,
    [Parameter(Mandatory)][string]$SelectionPath,
    [Parameter(Mandatory)][string]$OutputPath,
    [guid]$TriggerKey=[guid]::NewGuid(),
    [switch]$AllowInsecure
)
$ErrorActionPreference='Stop'
if ($BaseUrl.Scheme -ne 'https' -and -not ($AllowInsecure -and $BaseUrl.Scheme -eq 'http')) { throw 'Use HTTPS or explicitly select an isolated trusted HTTP network.' }
if ([string]::IsNullOrWhiteSpace($env:EXAMPLE_OPERATOR_TOKEN)) { throw 'Set EXAMPLE_OPERATOR_TOKEN to the exporting user bearer token.' }
$outputFile=[System.IO.Path]::GetFullPath($OutputPath)
if (Test-Path -LiteralPath $outputFile) { throw 'OutputPath already exists; choose a new file.' }
if (-not (Test-Path -LiteralPath ([System.IO.Path]::GetDirectoryName($outputFile)) -PathType Container)) { throw 'Output directory must exist.' }
$selection=Get-Content -LiteralPath $SelectionPath -Raw | ConvertFrom-Json -AsHashtable
Write-Output "triggerKey=$TriggerKey"
$client=[System.Net.Http.HttpClient]::new()
$client.Timeout=[TimeSpan]::FromSeconds(30)
$client.DefaultRequestHeaders.Authorization=[System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$env:EXAMPLE_OPERATOR_TOKEN)
$root=$BaseUrl.AbsoluteUri.TrimEnd('/')
function Invoke-SnapshotJson([string]$Path,$Body) {
    $content=[System.Net.Http.StringContent]::new(($Body|ConvertTo-Json -Depth 30 -Compress),[System.Text.Encoding]::UTF8,'application/json')
    try {
        $response=$client.PostAsync($root+$Path,$content).GetAwaiter().GetResult()
        try {
            $envelope=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult()|ConvertFrom-Json
            if (-not $response.IsSuccessStatusCode -or $envelope.code -ne 200) { throw "Request rejected: $Path HTTP=$([int]$response.StatusCode) code=$($envelope.code)" }
            return $envelope.data
        } finally { $response.Dispose() }
    } finally { $content.Dispose() }
}
try {
    $execution=Invoke-SnapshotJson '/api/example/demo-snapshot/submit' @{triggerKey=$TriggerKey.ToString();selection=$selection}
    $key=$execution.executionKey
    Write-Output "executionKey=$key"
    $deadline=[DateTime]::UtcNow.AddMinutes(2)
    while ($execution.status -in @('PENDING','RUNNING')) {
        if ([DateTime]::UtcNow -ge $deadline) { throw "Polling deadline reached; query executionKey=$key or retry with the same triggerKey." }
        Start-Sleep -Milliseconds 250
        $execution=Invoke-SnapshotJson '/api/example/demo-execution/get' @{executionKey=$key}
    }
    if ($execution.status -ne 'SUCCESS') { throw "No artifact: status=$($execution.status) reason=$($execution.reason) executionKey=$key" }
    $content=[System.Net.Http.StringContent]::new((@{executionKey=$key}|ConvertTo-Json -Compress),[System.Text.Encoding]::UTF8,'application/json')
    try {
        $response=$client.PostAsync($root+'/api/example/demo-execution/download',$content).GetAwaiter().GetResult()
        try {
            if (-not $response.IsSuccessStatusCode -or $response.Content.Headers.ContentType.MediaType -ne 'text/csv') { throw "Download rejected: HTTP=$([int]$response.StatusCode). Query the execution state." }
            $bytes=$response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
            $file=[System.IO.File]::Open($outputFile,[System.IO.FileMode]::CreateNew,[System.IO.FileAccess]::Write)
            try { $file.Write($bytes,0,$bytes.Length) } finally { $file.Dispose() }
            Write-Output "status=SUCCESS recordCount=$($execution.recordCount) output=$outputFile"
        } finally { $response.Dispose() }
    } finally { $content.Dispose() }
} finally { $client.Dispose() }
