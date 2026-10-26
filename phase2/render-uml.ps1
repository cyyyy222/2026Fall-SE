[CmdletBinding()]
param(
    [string]$PlantUmlJar,
    [switch]$Download
)
$ErrorActionPreference = 'Stop'
if (-not $PlantUmlJar) { $PlantUmlJar = Join-Path $PSScriptRoot 'tools/plantuml-1.2025.10.jar' }
$version = '1.2025.10'
$java = Get-Command java -ErrorAction Stop
if (-not (Test-Path -LiteralPath $PlantUmlJar)) {
    if (-not $Download) {
        throw 'PlantUML JAR missing. Run this script with -Download once, or pass -PlantUmlJar.'
    }
    $parent = Split-Path -Parent $PlantUmlJar
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    $uri = "https://repo.maven.apache.org/maven2/net/sourceforge/plantuml/plantuml/$version/plantuml-$version.jar"
    Invoke-WebRequest -Uri $uri -OutFile "$PlantUmlJar.download"
    Move-Item -LiteralPath "$PlantUmlJar.download" -Destination $PlantUmlJar
}
$jar = (Resolve-Path -LiteralPath $PlantUmlJar).Path
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'UML') -Filter '*.puml' | Sort-Object Name)
if ($sources.Count -eq 0) { throw 'No .puml files found.' }
$result = @()
foreach ($source in $sources) {
    # Render to a temporary folder so syntax-error images cannot overwrite valid deliverables.
    $stage = Join-Path ([IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $stage | Out-Null
    & $java.Source '-Djava.awt.headless=true' '-DPLANTUML_LIMIT_SIZE=16000' '-jar' $jar '-charset' 'UTF-8' '-failfast2' '-tpng' '-o' $stage $source.FullName
    if ($LASTEXITCODE -ne 0) { throw "PlantUML failed: $($source.Name). Diagnostics: $stage" }
    $rendered = Join-Path $stage ($source.BaseName + '.png')
    if (-not (Test-Path -LiteralPath $rendered)) { throw "PNG missing: $rendered" }
    $bytes = [IO.File]::ReadAllBytes($rendered)
    if ([BitConverter]::ToString($bytes[0..7]) -ne '89-50-4E-47-0D-0A-1A-0A') { throw "Invalid PNG: $rendered" }
    $width = $bytes[16] * 16777216 + $bytes[17] * 65536 + $bytes[18] * 256 + $bytes[19]
    $height = $bytes[20] * 16777216 + $bytes[21] * 65536 + $bytes[22] * 256 + $bytes[23]
    $target = Join-Path $source.DirectoryName ($source.BaseName + '.png')
    Copy-Item -LiteralPath $rendered -Destination $target -Force
    Remove-Item -LiteralPath $rendered
    Remove-Item -LiteralPath $stage
    $result += [pscustomobject]@{ Source = $source.Name; PNG = [IO.Path]::GetFileName($target); Width = $width; Height = $height; SourceSHA256 = (Get-FileHash -LiteralPath $source.FullName -Algorithm SHA256).Hash }
    Write-Host "OK $($source.Name) -> ${width}x${height}"
}
[pscustomobject]@{
    GeneratedAt = [DateTimeOffset]::UtcNow.ToString('o')
    PlantUmlJarSHA256 = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    Files = $result
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'uml-build.json') -Encoding UTF8
Write-Host "Rendered $($sources.Count) diagrams."

