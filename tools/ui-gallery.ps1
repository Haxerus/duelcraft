param(
    [Parameter(Mandatory=$true)][string]$ReportDir,
    [Parameter(Mandatory=$true)][string]$OutputDir,
    [string]$SourceRevision = 'unknown',
    [ValidateSet('true','false','unknown')][string]$SourceDirty = 'unknown',
    [string]$DependencySha256 = 'unknown'
)
$ErrorActionPreference = 'Stop'
function Html($value) {
    if ($null -eq $value -or "$value" -eq '') { return 'unknown' }
    [System.Net.WebUtility]::HtmlEncode("$value")
}
$source = (Resolve-Path -LiteralPath $ReportDir).Path
$destination = [IO.Path]::GetFullPath($OutputDir)
if (Test-Path -LiteralPath $destination) { throw "Archive already exists: $destination" }
$reportPath = Join-Path $source 'report.json'
$report = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
if ($report.schema -ne 1) { throw 'Expected report schema 1' }
New-Item -ItemType Directory -Path $destination | Out-Null
Copy-Item -LiteralPath $reportPath -Destination (Join-Path $destination 'report.json')
$dependency = @($report.environment.mods | Where-Object { $_ -like 'ldlib2@*' }) -join ', '
if (!$dependency) { $dependency = 'unknown' }
@{sourceRevision=$SourceRevision; sourceDirty=$SourceDirty; dependencyVersion=$dependency; dependencySha256=$DependencySha256; environment=$report.environment} | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 (Join-Path $destination 'provenance.json')
$body = [Text.StringBuilder]::new()
[void]$body.Append("<h1>Retained UI evidence</h1><p>Harness verdict: <strong>$(Html $report.status)</strong>. Screenshots require visual review.</p><p><a href=`"report.json`">Original report</a> | <a href=`"provenance.json`">Provenance</a></p><dl><dt>Source revision / dirty</dt><dd>$(Html $SourceRevision) / $(Html $SourceDirty)</dd><dt>LDLib2 version / SHA256</dt><dd>$(Html $dependency) / $(Html $DependencySha256)</dd><dt>Runtime environment</dt><dd>$(Html ($report.environment | ConvertTo-Json -Compress -Depth 8))</dd></dl>")
foreach ($scenario in $report.scenarios) {
    [void]$body.Append("<section><h2>$(Html $scenario.name) - $(Html $scenario.status)</h2><p>Viewport / design metadata: unknown unless attached below.</p>")
    foreach ($step in $scenario.steps) {
        [void]$body.Append("<article><h3>$(Html $step.name) - $(Html $step.status)</h3>")
        if ($step.attachments) {
            foreach ($property in $step.attachments.PSObject.Properties) {
                [void]$body.Append("<p><b>$(Html $property.Name)</b>: $(Html $property.Value)</p>")
            }
        }
        if ($step.error) { [void]$body.Append("<p class=`"diagnostic`">$(Html $step.error)</p>") }
        foreach ($capture in $step.captures) {
            $path = [string]$capture.path
            $candidate = [IO.Path]::GetFullPath((Join-Path $source $path))
            $safe = $path -and ![IO.Path]::IsPathRooted($path) -and $candidate.StartsWith($source.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)
            if ($safe -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
                # Reject junctions/symlinks anywhere along the capture path as well.
                $item = Get-Item -LiteralPath $candidate
                while ($item.FullName -ne $source) {
                    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { $safe = $false; break }
                    $item = $item.Parent
                    if (!$item) { $item = (Get-Item -LiteralPath $candidate).Directory }
                }
            }
            if (!$safe) { [void]$body.Append("<p class=`"diagnostic`">Unsafe capture: $(Html $path)</p>"); continue }
            if (!(Test-Path -LiteralPath $candidate -PathType Leaf)) { [void]$body.Append("<p class=`"diagnostic`">Missing capture: $(Html $path)</p>"); continue }
            $extension = [IO.Path]::GetExtension($candidate).ToLowerInvariant()
            if ($extension -notin @('.png','.jpg','.jpeg','.gif','.webp')) { [void]$body.Append("<p class=`"diagnostic`">Unsupported capture: $(Html $path)</p>"); continue }
            $relative = $candidate.Substring($source.Length + 1)
            $link = (($relative -split '[\\/]') | ForEach-Object { [Uri]::EscapeDataString($_) }) -join '/'
            $target = Join-Path $destination $relative
            New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($target)) -Force | Out-Null
            Copy-Item -LiteralPath $candidate -Destination $target
            [void]$body.Append("<figure><a href=`"$link`"><img src=`"$link`" alt=`"$(Html $step.name)`" loading=`"lazy`"></a><figcaption>$(Html $path) | $(Html $capture.kind)</figcaption></figure>")
        }
        [void]$body.Append('</article>')
    }
    [void]$body.Append('</section>')
}
@"
<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Retained UI evidence</title>
<style>body{margin:0 auto;padding:24px;max-width:1100px;background:#211c2a;color:#eee5d4;font:16px/1.5 system-ui}a{color:#efd18e}h1,h2{color:#c6ab77}article{background:#34273e;padding:16px;margin:12px 0;border:1px solid #8c7495}p,dd,h3,figcaption{overflow-wrap:anywhere}dd{margin:0 0 12px}figure{margin:12px 0}img{display:block;max-width:100%;height:auto}.diagnostic{color:#ffb5b5;font-weight:bold}@media(max-width:500px){body{padding:12px}article{padding:8px}}</style>
<body>$body</body></html>
"@ | Set-Content -Encoding UTF8 (Join-Path $destination 'index.html')
Write-Output (Join-Path $destination 'index.html')


