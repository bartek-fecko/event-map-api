#Requires -Version 5.1
<#
.SYNOPSIS
    Regenerates src/main/resources/db/migration/V3__seed_cities.sql from OpenStreetMap.

.DESCRIPTION
    Fetches the city outline for each launch city from Nominatim (GeoJSON),
    simplifies it inside a throwaway PostGIS container with
    ST_SimplifyPreserveTopology, validates the result and rewrites the Flyway
    migration.

    Needs Docker and network access. Nominatim asks for at most one request per
    second, so the script sleeps between cities.

.PARAMETER OutputPath
    Target migration file. Defaults to src/main/resources/db/migration/V3__seed_cities.sql.

.PARAMETER SimplifyTolerance
    Simplification tolerance in degrees. 0.0008 is roughly 80 m.

.PARAMETER Image
    PostGIS image used for the geometry work.

.EXAMPLE
    .\tools\fetch-city-boundaries.ps1

.EXAMPLE
    .\tools\fetch-city-boundaries.ps1 -SimplifyTolerance 0.0005 -OutputPath D:\tmp\V3.sql
#>
[CmdletBinding()]
param(
    [string]$OutputPath,
    [double]$SimplifyTolerance = 0.0008,
    [string]$Image = "postgis/postgis:17-3.5"
)

$ErrorActionPreference = "Stop"

$utf8 = New-Object System.Text.UTF8Encoding($false)
$invariant = [System.Globalization.CultureInfo]::InvariantCulture
$toleranceText = $SimplifyTolerance.ToString($invariant)

# slug = stable API value, name = display value, query = what Nominatim searches for.
$cities = @(
    @{ Slug = "krakow";   Name = "Kraków";   Query = "Kraków, Poland" },
    @{ Slug = "warszawa"; Name = "Warszawa"; Query = "Warszawa, Poland" },
    @{ Slug = "wroclaw";  Name = "Wrocław";  Query = "Wrocław, Poland" },
    @{ Slug = "gdansk";   Name = "Gdańsk";   Query = "Gdańsk, Poland" },
    @{ Slug = "poznan";   Name = "Poznań";   Query = "Poznań, Poland" },
    @{ Slug = "katowice"; Name = "Katowice"; Query = "Katowice, Poland" },
    @{ Slug = "lodz";     Name = "Łódź";     Query = "Łódź, Poland" }
)

if (-not $OutputPath) {
    $repoRoot = Split-Path -Parent $PSScriptRoot
    $OutputPath = Join-Path $repoRoot "src\main\resources\db\migration\V3__seed_cities.sql"
}
$OutputPath = [System.IO.Path]::GetFullPath($OutputPath)

function Invoke-Docker {
    param([string[]]$Arguments)
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

# --- preconditions ----------------------------------------------------------
& docker info --format "{{.ServerVersion}}" | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Docker is not running. Start Docker Desktop (or the engine) and retry."
}

$suffix = [Guid]::NewGuid().ToString("N").Substring(0, 8)
$container = "eventmap-geo-$suffix"
$workDir = Join-Path $env:TEMP "eventmap-geo-$suffix"
$fetched = 0

try {
    New-Item -ItemType Directory -Force -Path $workDir | Out-Null

    Write-Host "Starting $Image as $container ..."
    Invoke-Docker @("run", "-d", "--rm", "--name", $container,
        "-e", "POSTGRES_PASSWORD=pg", $Image) | Out-Null

    $deadline = (Get-Date).AddMinutes(3)
    $ready = $false
    while (-not $ready -and (Get-Date) -lt $deadline) {
        & docker exec $container pg_isready -U postgres 1>$null 2>$null
        $ready = $LASTEXITCODE -eq 0
        if (-not $ready) { Start-Sleep -Seconds 2 }
    }
    if (-not $ready) { throw "PostGIS container did not become ready in time" }

    # --- download outlines ---------------------------------------------------
    $headers = @{ "User-Agent" = "eventmap-seed-script/1.0 (local development; contact: admin@eventmap.local)" }
    foreach ($city in $cities) {
        $uri = "https://nominatim.openstreetmap.org/search?q=" +
            [Uri]::EscapeDataString($city.Query) +
            "&format=json&polygon_geojson=1&limit=1&featuretype=city"
        Write-Host ("Fetching {0} ..." -f $city.Name)

        $result = Invoke-RestMethod -Uri $uri -Headers $headers -TimeoutSec 60
        if (-not $result -or -not $result[0].geojson) {
            throw "Nominatim returned no polygon for $($city.Name)"
        }

        $json = $result[0].geojson | ConvertTo-Json -Depth 100 -Compress
        [System.IO.File]::WriteAllText((Join-Path $workDir "$($city.Slug).geojson"), $json, $utf8)
        $fetched++
        Start-Sleep -Milliseconds 1200
    }

    # --- simplify + validate inside PostGIS ----------------------------------
    $ordinal = 0
    $valueRows = foreach ($city in $cities) {
        $ordinal++
        " ({0}, '{1}', '{2}', pg_read_file('/tmp/{1}.geojson')::jsonb)" -f $ordinal, $city.Slug, $city.Name
    }

    $sql = @"
CREATE TEMP TABLE raw (ord int, slug text, name text, gj jsonb);
INSERT INTO raw (ord, slug, name, gj) VALUES
$($valueRows -join ",`n");

-- Simplification per city, written as UTF-8 text to a file we copy out.
-- Ordered by ord so regeneration keeps the file's row order stable.
COPY (
    SELECT slug,
           name,
           ST_AsText(ST_Multi(ST_SimplifyPreserveTopology(
               ST_SetSRID(ST_GeomFromGeoJSON(gj), 4326), $toleranceText)))
      FROM raw
     ORDER BY ord
) TO '/tmp/cities.tsv' WITH (FORMAT text, DELIMITER E'\t');

-- Fail loudly instead of committing broken geometry.
WITH simplified AS (
    SELECT slug,
           ST_Multi(ST_SimplifyPreserveTopology(
               ST_SetSRID(ST_GeomFromGeoJSON(gj), 4326), $toleranceText)) AS area
      FROM raw
)
SELECT slug, ST_IsValid(area), ST_GeometryType(area), ST_NPoints(area)
  FROM simplified
 ORDER BY slug;
"@

    $sqlPath = Join-Path $workDir "load.sql"
    [System.IO.File]::WriteAllText($sqlPath, $sql, $utf8)

    Invoke-Docker @("cp", $sqlPath, "${container}:/tmp/load.sql") | Out-Null
    foreach ($city in $cities) {
        Invoke-Docker @("cp", (Join-Path $workDir "$($city.Slug).geojson"), "${container}:/tmp/$($city.Slug).geojson") | Out-Null
    }

    $report = & docker exec $container psql -U postgres -q -A -t -f /tmp/load.sql
    if ($LASTEXITCODE -ne 0) { throw "psql failed while simplifying the boundaries" }

    Write-Host "Validation (slug|valid|type|points):"
    foreach ($line in $report) {
        if (-not $line) { continue }
        Write-Host "  $line"
        $fields = $line -split "\|"
        if ($fields.Count -lt 4) { continue }
        if ($fields[1] -ne "t") { throw "Boundary for $($fields[0]) is not valid" }
        if ($fields[2] -ne "ST_MultiPolygon") { throw "Boundary for $($fields[0]) is $($fields[2]), expected ST_MultiPolygon" }
    }

    $tsvPath = Join-Path $workDir "cities.tsv"
    Invoke-Docker @("cp", "${container}:/tmp/cities.tsv", $tsvPath) | Out-Null

    # --- compose the migration ----------------------------------------------
    $rows = @([System.IO.File]::ReadAllLines($tsvPath, $utf8) | Where-Object { $_ -match "`t" })
    if ($rows.Count -ne $cities.Count) {
        throw "Expected $($cities.Count) city rows, got $($rows.Count)"
    }

    $stamp = [DateTime]::UtcNow.ToString("yyyy-MM", $invariant)
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append("-- City boundaries for the seven launch cities.`n")
    [void]$sb.Append("-- Source: OpenStreetMap via Nominatim (fetched $stamp), simplified with`n")
    [void]$sb.Append("-- ST_SimplifyPreserveTopology at $toleranceText deg (~80 m) so the file stays reviewable.`n")
    [void]$sb.Append("-- Regenerate with tools/fetch-city-boundaries.ps1.`n")
    [void]$sb.Append("INSERT INTO city (id, slug, name, boundary)`n")
    [void]$sb.Append("VALUES`n")

    for ($i = 0; $i -lt $rows.Count; $i++) {
        $fields = $rows[$i] -split "`t"
        if ($fields.Count -lt 3) {
            throw "Malformed row in cities.tsv: $($rows[$i].Substring(0, [Math]::Min(40, $rows[$i].Length)))..."
        }
        $slug = $fields[0]
        $name = $fields[1].Replace("'", "''")
        $wkt = $fields[2]
        $separator = if ($i -lt $rows.Count - 1) { "," } else { "" }
        [void]$sb.Append("    (gen_random_uuid(), '$slug', '$name', ST_GeomFromText('$wkt', 4326)::geography)$separator`n")
    }

    [void]$sb.Append("ON CONFLICT (slug) DO NOTHING;`n")

    $parent = Split-Path -Parent $OutputPath
    if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
    [System.IO.File]::WriteAllText($OutputPath, $sb.ToString(), $utf8)

    $sizeKb = [Math]::Round((Get-Item $OutputPath).Length / 1KB)
    Write-Host ""
    Write-Host "Wrote $OutputPath ($sizeKb KB, $($rows.Count) cities, $fetched fetched)."
    Write-Host "Existing databases keep the old boundaries: add a new migration or recreate the local volume."
}
finally {
    if ($container) {
        & docker rm -f $container 1>$null 2>$null
    }
    if (Test-Path $workDir) {
        Remove-Item $workDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}
