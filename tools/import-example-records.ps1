[CmdletBinding()]
param(
    [Parameter(Mandatory)][uri]$BaseUrl,
    [Parameter(Mandatory)][string]$CsvPath,
    [string]$ReportCode='sales-report',
    [guid]$ImportKey=[guid]::NewGuid(),
    [switch]$AllowInsecure
)
$ErrorActionPreference='Stop'
if ($BaseUrl.Scheme -ne 'https' -and -not ($AllowInsecure -and $BaseUrl.Scheme -eq 'http')) { throw 'Use HTTPS or explicitly select an isolated trusted HTTP network.' }
if ([string]::IsNullOrWhiteSpace($env:EXAMPLE_OPERATOR_TOKEN)) { throw 'Set EXAMPLE_OPERATOR_TOKEN to the importing user bearer token.' }
$csv=Get-Item -LiteralPath $CsvPath
if ($csv.PSIsContainer -or $csv.Length -gt 10MB) { throw 'CSV must be a file of at most 10 MiB.' }
# Keep this key for network retries; a new key means a new intentional import.
Write-Output "importKey=$ImportKey"
$client=[System.Net.Http.HttpClient]::new()
$form=[System.Net.Http.MultipartFormDataContent]::new()
try {
    $client.Timeout=[TimeSpan]::FromSeconds(60)
    $client.DefaultRequestHeaders.Authorization=[System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$env:EXAMPLE_OPERATOR_TOKEN)
    $request=@{reportCode=$ReportCode;importKey=$ImportKey.ToString()}|ConvertTo-Json -Compress
    $form.Add([System.Net.Http.StringContent]::new($request,[System.Text.Encoding]::UTF8,'application/json'),'request')
    $file=[System.Net.Http.StreamContent]::new([System.IO.File]::OpenRead($csv.FullName))
    $file.Headers.ContentType=[System.Net.Http.Headers.MediaTypeHeaderValue]::new('text/csv')
    $form.Add($file,'file','records.csv')
    $response=$client.PostAsync(($BaseUrl.AbsoluteUri.TrimEnd('/')+'/api/example/demo-data/import'),$form).GetAwaiter().GetResult()
    try {
        $envelope=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult()|ConvertFrom-Json
        if (-not $response.IsSuccessStatusCode -or $envelope.code -ne 200) { throw "Import rejected: HTTP=$([int]$response.StatusCode) code=$($envelope.code)" }
        Write-Output "status=$($envelope.data.status) recordCount=$($envelope.data.recordCount) importKey=$ImportKey"
    } finally { $response.Dispose() }
} finally { $form.Dispose(); $client.Dispose() }
