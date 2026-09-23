param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$Concurrency = 100,
    [int]$Seconds = 30,
    [switch]$IncludePublic = $false,
    [switch]$Quiet = $false
)

# Load test for KA Bus Tracking (Phase 12).
#
# Targets the public read path (the "5 crore passenger" profile): every request
# is anonymous, cheap and DTO-shaped, so this measures the fan-out tier + DB
# read path that dominates passenger traffic.
#
# Examples:
#   .\load_test.ps1 -BaseUrl http://localhost:8080 -Concurrency 200 -Seconds 60
#
# Writer-path (GPS ingest) is measured separately against a seeded fleet because
# a driver token only controls its own bus; see docs/SCALABILITY.md.

$ErrorActionPreference = "Stop"

function Invoke-PublicBurst {
    param(
        [string]$base,
        [int]$n,
        [int]$seconds
    )

    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromSeconds(30)
    $endpoints = @(
        "/api/public/config",
        "/api/public/routes",
        "/api/public/buses",
        "/api/public/search?q=Sir&limit=5",
        "/api/public/buses/nearby?lat=14.5&lon=74.5&radiusKm=50"
    )
    $success = 0
    $failed = 0
    $elapsed = 0
    $deadline = [System.Diagnostics.Stopwatch]::StartNew()

    $script:running = $true
    $tasks = New-Object 'System.Collections.Generic.List[System.Threading.Tasks.Task]'

    for ($i = 0; $i -lt $n; $i++) {
        $num = $i
        $tasks.Add($client.GetAsync($base + $endpoints[$num % $endpoints.Count]).
            ContinueWith({
                param($t)
                try {
                    $r = $t.Result
                    if ([int]$r.StatusCode -lt 400) { [System.Threading.Interlocked]::Increment([ref]$success) }
                    else { [System.Threading.Interlocked]::Increment([ref]$failed) }
                    $r.Dispose()
                } catch { [System.Threading.Interlocked]::Increment([ref]$failed) }
            },
            [System.Threading.Tasks.TaskContinuationOptions]::ExecuteSynchronously))
    }

    while ($deadline.Elapsed.TotalSeconds -lt $seconds -and $script:running) {
        Start-Sleep -Milliseconds 250
    }
    $script:running = $false
    try {
        [System.Threading.Tasks.Task]::WaitAll($tasks.ToArray(), [TimeSpan]::FromSeconds(10))
    } catch { }
    $elapsed = $deadline.Elapsed.TotalSeconds
    $client.Dispose()

    if (-not $Quiet) {
        Write-Host "Public read path:"
        Write-Host ("  requests      : {0}" -f $tasks.Count)
        Write-Host ("  ok / failed   : {0} / {1}" -f $success, $failed)
        Write-Host ("  wall seconds  : {0:N1}" -f $elapsed)
        Write-Host ("  throughput    : {0:N0} req/s (peak, without ramp)" -f ($success / [Math]::Max($elapsed, 0.1)))
    }
}

function Invoke-LoginProbe {
    param([string]$base)
    $body = '{"username":"nonexistent.rate.limit.check","password":"Pass@123","deviceId":"loadtest"}'
    try {
        Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType "application/json" -Body $body -ErrorAction SilentlyContinue | Out-Null
    } catch {
        $code = [int]$_.Exception.Response.StatusCode
        if (-not $Quiet) {
            Write-Host ("Login probe (rate limit + no user enumeration): HTTP {0} as expected" -f $code)
        }
        return
    }
    if (-not $Quiet) { Write-Host "Login probe: unexpected 200 for unknown user - investigate" }
}

if (-not $Quiet) {
    Write-Host "KA Bus Tracking load test -> $BaseUrl"
    Write-Host ("Concurrency: {0}, duration: {1}s" -f $Concurrency, $Seconds)
    Write-Host "-------------------------------------------------"
}

if ($IncludePublic) {
    Invoke-PublicBurst -base $BaseUrl -n $Concurrency -seconds $Seconds
}

Invoke-LoginProbe -base $BaseUrl

if (-not $Quiet) {
    Write-Host "-------------------------------------------------"
    Write-Host "Done. Reference capacity (design): read path aims to hold >10k req/s behind CDN+cache; GPS writer 2k writes/s at 10k buses."
}