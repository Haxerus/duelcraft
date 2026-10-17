$ErrorActionPreference = 'Stop'
function Assert($condition, $message) { if (!$condition) { throw $message } }
$case = Join-Path $PSScriptRoot ('../build/gallery-proof/task3-test-' + [guid]::NewGuid().ToString('N'))
$inputDir = New-Item -ItemType Directory -Path (Join-Path $case 'input') -Force
[IO.File]::WriteAllBytes((Join-Path $inputDir 'capture.png'), [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aBZkAAAAASUVORK5CYII='))
@{schema=1;status='FAIL';environment=@{mods=@('ldlib2@test')};scenarios=@(@{name='<script>alert(1)</script>';status='FAIL';steps=@(@{name='pass';status='PASS';captures=@(@{path='capture.png'})},@{name='fail';status='FAIL';captures=@(@{path='capture.png'},@{path='missing.png'},@{path='../outside.png'});error='<img src=x onerror=alert(1)>'})})} | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $inputDir 'report.json')
$output = Join-Path $case 'archive'
& "$PSScriptRoot/ui-gallery.ps1" -ReportDir $inputDir -OutputDir $output
$html = Get-Content (Join-Path $output 'index.html') -Raw
Assert ($html.Contains('PASS') -and $html.Contains('FAIL')) 'Both verdicts visible'
Assert ($html.Contains('unknown')) 'Missing provenance/viewport unknown'
Assert ($html.Contains('Missing capture') -and $html.Contains('Unsafe capture')) 'Missing and traversal diagnostics visible'
Assert ($html.Contains('&lt;script&gt;') -and !$html.Contains('<script>')) 'HTML text escaped'
Assert ($html.Contains('href="report.json"') -and $html.Contains('src="capture.png"')) 'Relative usable links'
Assert (Test-Path (Join-Path $output 'capture.png')) 'Capture copied'
$refused = $false
try { & "$PSScriptRoot/ui-gallery.ps1" -ReportDir $inputDir -OutputDir $output } catch { $refused = $true }
Assert $refused 'Existing archive refused'
Assert ((Get-Content (Join-Path $output 'provenance.json') -Raw|ConvertFrom-Json).sourceRevision -eq 'unknown') 'Unknown provenance retained'
Write-Output "All gallery assertions passed. Incomplete archive: $output"


