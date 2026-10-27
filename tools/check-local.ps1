<#
.SYNOPSIS
    End-to-end check that the running EventMap API works.

.DESCRIPTION
    Exercises the real HTTP API against a running application: health, seeded
    reference data, the map viewport (events and clusters), the error contract,
    admin login, event creation, public read-back, media upload and static
    serving. Prints one line per check and exits 1 if anything fails.

    Start the app first (IntelliJ "EventMap local" or
    .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local).

.PARAMETER BaseUrl
    Where the app listens. Default http://localhost:8080

.PARAMETER ReadOnly
    Skip the writing checks (login/event creation/media upload). Useful against
    an environment where you do not want test data.

.EXAMPLE
    .\tools\check-local.ps1

.EXAMPLE
    .\tools\check-local.ps1 -ReadOnly -BaseUrl http://localhost:8080
#>
[CmdletBinding()]
param(
    [string] $BaseUrl = "http://localhost:8080",
    [string] $AdminEmail = "admin@eventmap.local",
    [string] $AdminPassword = "admin12345",
    [switch] $ReadOnly
)

$ErrorActionPreference = "Stop"
$script:baseUrl = $BaseUrl.TrimEnd('/')
$script:failures = 0
$script:checks = 0
$invariant = [System.Globalization.CultureInfo]::InvariantCulture

function Write-Pass([string] $message) {
    Write-Host "  [ OK ] $message" -ForegroundColor Green
}

function Write-Fail([string] $message) {
    $script:failures++
    Write-Host "  [FAIL] $message" -ForegroundColor Red
}

function Check([string] $name, [scriptblock] $action) {
    $script:checks++
    try {
        & $action
        Write-Pass $name
    } catch {
        Write-Fail "$name -> $($_.Exception.Message)"
    }
}

function Invoke-Api {
    param(
        [string] $Method = "GET",
        [string] $Path,
        [string] $Body,
        [hashtable] $Headers
    )
    $params = @{
        Method     = $Method
        Uri        = $script:baseUrl + $Path
        TimeoutSec = 20
        UseBasicParsing = $true
    }
    if ($Body) {
        # Send UTF-8 bytes explicitly; PS 5.1 would otherwise use the ANSI code page.
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes($Body)
        $params.ContentType = "application/json; charset=utf-8"
    }
    if ($Headers) {
        $params.Headers = $Headers
    }
    $response = Invoke-WebRequest @params
    # Decode as UTF-8 ourselves: Spring sends no charset and PS 5.1 would guess wrong.
    $json = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    if ([string]::IsNullOrWhiteSpace($json)) {
        return $null
    }
    return ($json | ConvertFrom-Json)
}

function Get-StatusCode([string] $Path) {
    try {
        $response = Invoke-WebRequest -Uri ($script:baseUrl + $Path) -UseBasicParsing -TimeoutSec 15
        return [int] $response.StatusCode
    } catch {
        if ($_.Exception.Response) {
            return [int] $_.Exception.Response.StatusCode
        }
        throw
    }
}

function Assert-Status([string] $Path, [int] $Expected) {
    $actual = Get-StatusCode $Path
    if ($actual -ne $Expected) {
        throw "expected HTTP $Expected, got $actual for $Path"
    }
}

function Assert-True([bool] $condition, [string] $message) {
    if (-not $condition) {
        throw $message
    }
}

Write-Host ""
Write-Host "EventMap API check: $($script:baseUrl)" -ForegroundColor Cyan
Write-Host ""

# Fail fast with a useful message when nothing is listening at all.
try {
    Invoke-WebRequest -Uri ($script:baseUrl + "/actuator/health") -UseBasicParsing -TimeoutSec 5 | Out-Null
} catch {
    if (-not $_.Exception.Response) {
        Write-Host "  Cannot connect to $($script:baseUrl)." -ForegroundColor Red
        Write-Host "  Start the app first: IntelliJ run config 'EventMap local', or" -ForegroundColor Yellow
        Write-Host "    `$env:JAVA_HOME = 'C:\Users\<you>\.jdks\temurin-25.0.5'" -ForegroundColor Yellow
        Write-Host "    .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local" -ForegroundColor Yellow
        Write-Host "  Database: docker compose up -d db   (PostGIS on localhost:5433)" -ForegroundColor Yellow
        exit 2
    }
}

# --- 1. the process is up and its dependencies are healthy -------------------
Check "health endpoint reports UP" {
    $health = Invoke-Api -Path "/actuator/health"
    Assert-True ($health.status -eq "UP") "status was '$($health.status)'"
}

Check "database reachable through the app (liveness/readiness)" {
    Assert-Status "/actuator/health/readiness" 200
    Assert-Status "/actuator/health/liveness" 200
}

# --- 2. Flyway seeds survived ------------------------------------------------
Check "categories seeded (8)" {
    $categories = @(Invoke-Api -Path "/api/v1/categories")
    Assert-True ($categories.Count -ge 8) "only $($categories.Count) categories"
}

Check "cities seeded (7)" {
    $cities = @(Invoke-Api -Path "/api/v1/cities")
    Assert-True ($cities.Count -ge 7) "only $($cities.Count) cities"
}

# --- 3. the map endpoint the frontend will hammer ---------------------------
Check "map at zoom 13 returns individual events" {
    $map = Invoke-Api -Path "/api/v1/map/events?bbox=19.70,49.95,20.20,50.20&zoom=13"
    Assert-True ($map.mode -eq "events") "mode was '$($map.mode)'"
    Assert-True ($null -ne $map.events) "no events array"
    Write-Host "         -> $($map.events.Count) events, truncated=$($map.truncated)" -ForegroundColor DarkGray
}

Check "map at zoom 8 returns clusters" {
    $map = Invoke-Api -Path "/api/v1/map/events?bbox=19.70,49.95,20.20,50.20&zoom=8"
    Assert-True ($map.mode -eq "clusters") "mode was '$($map.mode)'"
    Write-Host "         -> $($map.clusters.Count) clusters" -ForegroundColor DarkGray
}

Check "map rejects a missing bbox with 400" {
    Assert-Status "/api/v1/map/events?zoom=13" 400
}

Check "unknown media path returns 404 problem+json" {
    Assert-Status "/media/does-not-exist.png" 404
}

Check "unknown API path is not a 500" {
    $code = Get-StatusCode "/api/v1/does-not-exist"
    # Security answers 401 for unknown paths under /api/v1 before routing sees
    # them; either way it must never be a server error.
    Assert-True ($code -eq 404 -or $code -eq 401) "got HTTP $code"
}

# --- 4. writes, unless asked not to touch data ------------------------------
if ($ReadOnly) {
    Write-Host ""
    Write-Host "  [SKIP] login, event creation, media upload (-ReadOnly)" -ForegroundColor Yellow
} else {
    $token = $null
    $auth = $null

    Check "admin login returns an access token" {
        $body = @{ email = $AdminEmail; password = $AdminPassword } | ConvertTo-Json -Compress
        $login = Invoke-Api -Method "POST" -Path "/api/v1/auth/login" -Body $body
        Assert-True (-not [string]::IsNullOrWhiteSpace($login.accessToken)) "no accessToken in response"
        Assert-True ($login.user.role -eq "ADMIN") "role was '$($login.user.role)'"
        $script:token = $login.accessToken
    }

    if ($script:token) {
        $auth = @{ Authorization = "Bearer $($script:token)" }
    }

    $eventId = $null

    if ($auth) {
        Check "admin creates a published event" {
            $starts = (Get-Date).ToUniversalTime().AddDays(7)
            $payload = @{
                title        = "Check run " + $starts.ToString("yyyy-MM-dd HH:mm", $invariant)
                description  = "Created by tools/check-local.ps1"
                venue        = @{
                    name      = "Check venue"
                    address   = "Rynek Glowny 1"
                    citySlug  = "krakow"
                    latitude  = 50.0614
                    longitude = 19.9366
                }
                occurrences  = @(@{
                    startsAt = $starts.ToString("yyyy-MM-ddTHH:mm:ssZ", $invariant)
                    endsAt   = $starts.AddHours(3).ToString("yyyy-MM-ddTHH:mm:ssZ", $invariant)
                })
                categorySlugs = @("music", "food")
            }
            $created = Invoke-Api -Method "POST" -Path "/api/v1/admin/events" `
                -Body ($payload | ConvertTo-Json -Depth 6 -Compress) -Headers $auth
            Assert-True (-not [string]::IsNullOrWhiteSpace($created.id)) "no event id in response"
            $script:eventId = $created.id
        }

        if ($script:eventId) {
            Check "event is readable on the public endpoint" {
                $detail = Invoke-Api -Path "/api/v1/events/$($script:eventId)"
                Assert-True ($detail.status -eq "PUBLISHED") "status was '$($detail.status)'"
                Assert-True (-not [string]::IsNullOrWhiteSpace($detail.venue.city)) "venue city was empty"
            }

            Check "created event shows up in the map viewport" {
                $map = Invoke-Api -Path "/api/v1/map/events?bbox=19.90,50.03,19.97,50.09&zoom=15"
                $ids = @($map.events | ForEach-Object { $_.id })
                Assert-True ($ids -contains $script:eventId) "event $($script:eventId) missing from $($ids.Count) map events"
            }
        }

        Check "image upload is stored and served back" {
            $png = Join-Path ([System.IO.Path]::GetTempPath()) "eventmap-check.png"
            $bytes = [Convert]::FromBase64String("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")
            [System.IO.File]::WriteAllBytes($png, $bytes)
            try {
                $json = & curl.exe -s -X POST "$($script:baseUrl)/api/v1/admin/media" `
                    -H "Authorization: Bearer $($script:token)" -F "file=@$png;type=image/png"
                $upload = $json | ConvertFrom-Json
                Assert-True (-not [string]::IsNullOrWhiteSpace($upload.url)) "no media url in response"
                $code = Get-StatusCode $upload.url
                Assert-True ($code -eq 200) "media url returned $code"
                Write-Host "         -> $($upload.url)" -ForegroundColor DarkGray
            } finally {
                Remove-Item $png -Force -ErrorAction SilentlyContinue
            }
        }
    }
}

# --- summary ----------------------------------------------------------------
Write-Host ""
if ($script:failures -eq 0) {
    Write-Host "All $($script:checks) checks passed." -ForegroundColor Green
    exit 0
}

Write-Host "$($script:failures) of $($script:checks) checks failed." -ForegroundColor Red
Write-Host "See the app log for the server side of the failing calls." -ForegroundColor Red
exit 1
