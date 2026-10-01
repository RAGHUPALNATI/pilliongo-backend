<#
  PillionGo API failure checks
  ----------------------------
  Calls the RUNNING backend the way a buggy client or an attacker would,
  and checks the app answers each one with the right HTTP status code and
  a readable error, never a 500 and never a crash.

  How to run (backend must be running on port 8080):
      cd D:\Downloads\pilliongo\pilliongo
      powershell -ExecutionPolicy Bypass -File tests\api-checks.ps1

  Part A needs no account.
  Part B asks for one RIDER and one DRIVER login you've already verified.
  Press Enter at the prompt to skip Part B. Part B creates a test ride
  offer and booking and cancels both again at the end.

  Status codes, in one line each:
    200 OK | 400 bad input / rule broken | 401 not logged in | 403 not allowed
    404 not found | 409 conflict | 500 = a bug in OUR code
#>

param([string]$BaseUrl = "http://localhost:8080/api")

$script:pass = 0; $script:fail = 0

# Send one HTTP request and return @{ Status = 401; Body = <parsed JSON or text> }.
# Works on Windows PowerShell 5.1 and PowerShell 7.
function Call($Method, $Path, $Body = $null, $Token = $null, [switch]$RawBody) {
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $params = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; UseBasicParsing = $true; ContentType = "application/json" }
    if ($null -ne $Body) { $params["Body"] = $(if ($RawBody) { $Body } else { $Body | ConvertTo-Json -Depth 5 }) }
    try {
        $r = Invoke-WebRequest @params
        $status = [int]$r.StatusCode; $text = $r.Content
    } catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { throw "Cannot reach $BaseUrl - is the backend running?" }
        $status = [int]$resp.StatusCode
        $text = $_.ErrorDetails.Message
        if (-not $text) { try { $text = (New-Object IO.StreamReader($resp.GetResponseStream())).ReadToEnd() } catch { $text = "" } }
    }
    $parsed = $text
    try { if ($text) { $parsed = $text | ConvertFrom-Json } } catch {}
    return @{ Status = $status; Body = $parsed }
}

function Check($Name, $Result, [int[]]$Expected, [scriptblock]$Extra = $null) {
    $ok = $Expected -contains $Result.Status
    if ($ok -and $Extra) { $ok = [bool](& $Extra $Result.Body) }
    $msg = if ($Result.Body.message) { $Result.Body.message } else { "" }
    if ($ok) { $script:pass++; Write-Host ("  PASS  {0}  [{1}]" -f $Name, $Result.Status) -ForegroundColor Green }
    else {
        $script:fail++
        Write-Host ("  FAIL  {0}  expected {1}, got {2}  {3}" -f $Name, ($Expected -join "/"), $Result.Status, $msg) -ForegroundColor Red
    }
}

Write-Host "`nPillionGo API checks against $BaseUrl`n"

# ------------------------------------------------------------------ Part A
Write-Host "Part A - no account needed" -ForegroundColor Cyan
Check "Public planned-rides board loads"           (Call GET "/rides/planned") @(200)
Check "History with NO token is rejected"           (Call GET "/rides/history") @(401)
Check "History with a FAKE token is rejected"       (Call GET "/rides/history" -Token "abc.def.ghi") @(401)
Check "Register with empty body lists field errors" (Call POST "/auth/register" @{}) @(400) { param($b) $b.errors.email -and $b.errors.password }
Check "Register with bad email + phone rejected"    (Call POST "/auth/register" @{ fullName="T"; email="not-an-email"; phone="123"; password="secret1"; role="RIDER" }) @(400)
Check "Register as ADMIN is blocked"                (Call POST "/auth/register" @{ fullName="T"; email="admin-try@test.com"; phone="9999999999"; password="secret1"; role="ADMIN" }) @(400)
Check "Broken JSON gives 400, not 500"              (Call POST "/auth/login" "{ this is not json" -RawBody) @(400)
Check "Login with unknown account rejected"         (Call POST "/auth/login" @{ email="nobody-$(Get-Random)@test.com"; password="wrong123" }) @(400, 401)
Check "Forgot-password for unknown email = 404"     (Call POST "/auth/forgot-password" @{ email="nobody-$(Get-Random)@test.com" }) @(404)

# ------------------------------------------------------------------ Part B
Write-Host "`nPart B - needs a verified rider and driver account" -ForegroundColor Cyan
$riderEmail = Read-Host "  Rider email (Enter to skip Part B)"
if ($riderEmail) {
    $riderPass   = Read-Host "  Rider password"
    $driverEmail = Read-Host "  Driver email"
    $driverPass  = Read-Host "  Driver password"

    $rl = Call POST "/auth/login" @{ email=$riderEmail; password=$riderPass }
    $dl = Call POST "/auth/login" @{ email=$driverEmail; password=$driverPass }
    Check "Rider can log in"  $rl @(200)
    Check "Driver can log in" $dl @(200)
    $rider = $rl.Body.token; $driver = $dl.Body.token

    if ($rider -and $driver) {
        Check "Rider can't open driver endpoints"      (Call GET "/driver/vehicles" -Token $rider) @(403)
        Check "Rider can't publish a driver offer"     (Call POST "/rides/offer" @{ pickupLocation="LPU University Main Gate"; destination="Jalandhar City"; rideType="PLANNED" } $rider) @(403)
        Check "Non-numeric ride id gives 400, not 500" (Call GET "/rides/abc" -Token $rider) @(400)
        Check "Unknown ride id is a clean error"       (Call GET "/rides/999999999" -Token $rider) @(400, 404)

        $vehicles = (Call GET "/driver/vehicles" -Token $driver).Body
        $car  = @($vehicles) | Where-Object { $_.vehicleType -eq "Car" }  | Select-Object -First 1
        $bike = @($vehicles) | Where-Object { $_.vehicleType -eq "Bike" } | Select-Object -First 1
        $date = (Get-Date).AddDays(2).ToString("yyyy-MM-dd")
        $offerBody = @{ pickupLocation="LPU University Main Gate"; destination="Jalandhar City"; rideType="PLANNED"; scheduledDate=$date; scheduledTime="10:00:00"; description="API check - safe to ignore" }

        Check "Offer with 9 seats is rejected (max 6)" (Call POST "/rides/offer" ($offerBody + @{ seats=9; vehicleId=$(if ($car) { $car.id } else { $bike.id }) }) $driver) @(400)

        if ($bike) {
            $b = Call POST "/rides/offer" ($offerBody + @{ seats=3; vehicleId=$bike.id }) $driver
            Check "Bike offer is capped at 1 seat" $b @(200) { param($x) $x.seatsTotal -eq 1 }
            if ($b.Body.id) { Call DELETE "/rides/$($b.Body.id)" -Token $driver | Out-Null }
        }

        if (-not $car) {
            Write-Host "  SKIP  multi-seat checks - add a Car vehicle to the driver account to run them" -ForegroundColor Yellow
        } else {
            $o = Call POST "/rides/offer" ($offerBody + @{ seats=3; vehicleId=$car.id }) $driver
            Check "Driver publishes a 3-seat car offer" $o @(200) { param($x) $x.seatsTotal -eq 3 -and $x.seatsAvailable -eq 3 }
            $offerId = $o.Body.id; $perSeat = [double]$o.Body.fare

            if ($offerId) {
                Check "Booking 5 seats on a 3-seat offer fails" (Call PUT "/rides/$offerId/book?seats=5" -Token $rider) @(400)
                Check "Driver can't book seats"                 (Call PUT "/rides/$offerId/book?seats=1" -Token $driver) @(400, 403)

                $bk = Call PUT "/rides/$offerId/book?seats=2" -Token $rider
                Check "Rider books 2 seats, fare doubles" $bk @(200) { param($x) $x.seatCount -eq 2 -and [math]::Abs([double]$x.fare - 2 * $perSeat) -lt 0.01 }
                $bookingId = $bk.Body.id

                $board = @((Call GET "/rides/planned").Body) | Where-Object { $_.id -eq $offerId }
                Check "Board now shows 1 seat left" @{ Status = 200; Body = $board } @(200) { param($x) $x.seatsAvailable -eq 1 }
                Check "Same rider can't book the offer twice"   (Call PUT "/rides/$offerId/book?seats=1" -Token $rider) @(400)

                if ($bookingId) {
                    Check "Can't complete a ride that hasn't started" (Call PUT "/rides/$bookingId/complete" -Token $rider) @(400)
                    Check "Rider cancels the booking"                 (Call DELETE "/rides/$bookingId" -Token $rider) @(200)
                    $board = @((Call GET "/rides/planned").Body) | Where-Object { $_.id -eq $offerId }
                    Check "Cancelled seats go back on the board" @{ Status = 200; Body = $board } @(200) { param($x) $x.seatsAvailable -eq 3 }
                    Check "Cancelled booking can't be cancelled again" (Call DELETE "/rides/$bookingId" -Token $rider) @(400)
                }
                Check "Driver cancels the offer (cleanup)" (Call DELETE "/rides/$offerId" -Token $driver) @(200)
            }
        }
    }
}

# ------------------------------------------------------------------ Summary
$color = if ($script:fail -eq 0) { "Green" } else { "Red" }
Write-Host ("`n{0} passed, {1} failed`n" -f $script:pass, $script:fail) -ForegroundColor $color
if ($script:fail -gt 0) { exit 1 }
