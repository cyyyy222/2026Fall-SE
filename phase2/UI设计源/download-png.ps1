param([string]$Manifest)
$ErrorActionPreference = 'Stop'
if (-not $Manifest) { $Manifest = Join-Path $PSScriptRoot '.export-links.temporary.json' }
$items = Get-Content -LiteralPath $Manifest -Raw -Encoding UTF8 | ConvertFrom-Json
$destination = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ('../UI' + [char]0x754C + [char]0x9762)))
foreach ($item in $items) {
    $path = Join-Path $destination ($item.name + '.png')
    Invoke-WebRequest -Uri $item.url -OutFile $path
    $bytes = [IO.File]::ReadAllBytes($path)
    if ([BitConverter]::ToString($bytes[0..7]) -ne '89-50-4E-47-0D-0A-1A-0A') { throw "Invalid PNG: $($item.name)" }
    Write-Output "Saved $($item.name).png"
}
