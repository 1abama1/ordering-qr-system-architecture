param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$EnvFile = (Join-Path $PSScriptRoot '../.env')
)
$ErrorActionPreference = 'Stop'
$settings = @{}
foreach ($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $EnvFile))) {
    if ($line -match '^([^#=]+)=(.*)$') { $settings[$Matches[1].Trim()] = $Matches[2] }
}
function Assert-Check([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
function Request([string]$Method, [string]$Path, $Body = $null, $Headers = @{}) {
    $options = @{ Uri = $BaseUrl + $Path; Method = $Method; Headers = $Headers; SkipHttpErrorCheck = $true }
    if ($null -ne $Body) { $options.ContentType = 'application/json'; $options.Body = ConvertTo-Json -InputObject $Body -Depth 12 -Compress }
    $response = Invoke-WebRequest @options
    $text = if ($response.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($response.Content) } else { [string]$response.Content }
    $data = if ($text -and $response.Headers.'Content-Type' -like '*json*') { $text | ConvertFrom-Json } else { $null }
    return @{ Status = [int]$response.StatusCode; Data = $data }
}
function Send-Stomp($Socket, [string]$Frame) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($Frame + [char]0)
    $null = $Socket.SendAsync([ArraySegment[byte]]::new($bytes), [Net.WebSockets.WebSocketMessageType]::Text, $true, [Threading.CancellationToken]::None).GetAwaiter().GetResult()
}
function Receive-Stomp($Socket) {
    $buffer = [byte[]]::new(65536)
    $text = [Text.StringBuilder]::new()
    $timeout = [Threading.CancellationTokenSource]::new(7000)
    try {
        while ($true) {
            $result = $Socket.ReceiveAsync([ArraySegment[byte]]::new($buffer),$timeout.Token).GetAwaiter().GetResult()
            if ($result.MessageType -eq [Net.WebSockets.WebSocketMessageType]::Close) { throw 'WebSocket closed unexpectedly' }
            [void]$text.Append([Text.Encoding]::UTF8.GetString($buffer,0,$result.Count))
            if ($text.ToString().Contains([string][char]0)) { return $text.ToString().TrimStart([char]10).TrimEnd([char]0) }
        }
    } finally { $timeout.Dispose() }
}
function Connect-Stomp([string]$Jwt, [string]$Destination) {
    $socket = [Net.WebSockets.ClientWebSocket]::new()
    $socket.Options.SetRequestHeader('Origin', $settings['ALLOWED_ORIGINS'].Split(',')[0])
    $url = [UriBuilder]::new($BaseUrl + '/ws')
    $url.Scheme = if ($url.Scheme -eq 'https') { 'wss' } else { 'ws' }
    $null = $socket.ConnectAsync($url.Uri,[Threading.CancellationToken]::None).GetAwaiter().GetResult()
    $authorization = if ($Jwt) { "Authorization:Bearer $Jwt`n" } else { '' }
    Send-Stomp $socket ("CONNECT`naccept-version:1.2`nhost:localhost`nheart-beat:10000,10000`n" + $authorization + "`n")
    Assert-Check ((Receive-Stomp $socket).StartsWith('CONNECTED')) 'Missing STOMP CONNECTED'
    Send-Stomp $socket ("SUBSCRIBE`nid:smoke`ndestination:$Destination`nreceipt:ready`n`n")
    Assert-Check ((Receive-Stomp $socket).StartsWith('RECEIPT')) 'Missing subscription receipt'
    return $socket
}
function Event($Socket) {
    $frame = Receive-Stomp $Socket
    Assert-Check ($frame.StartsWith('MESSAGE')) 'Expected STOMP MESSAGE'
    return ($frame.Substring($frame.IndexOf("`n`n") + 2) | ConvertFrom-Json)
}
$health = Request GET '/actuator/health'
Assert-Check ($health.Status -eq 200 -and $health.Data.status -eq 'UP') 'Health check failed'
$login = Request POST '/api/staff/auth/login' @{username=$settings['BOOTSTRAP_ADMIN_USERNAME'];password=$settings['BOOTSTRAP_ADMIN_PASSWORD']}
Assert-Check ($login.Status -eq 200) 'Admin login failed'
$staffHeaders = @{Authorization='Bearer ' + $login.Data.accessToken}
$menu = (Request GET '/api/menu').Data
$lagman = $menu | Where-Object name -EQ 'Лагман' | Select-Object -First 1
$tea = $menu | Where-Object name -EQ 'Чай' | Select-Object -First 1
Assert-Check ($null -ne $lagman -and $null -ne $tea) 'Run smoke on dev demo menu with available Лагман and Чай'
$tables = (Request GET '/api/staff/tables' $null $staffHeaders).Data
$table = $tables | Where-Object number -EQ 3 | Select-Object -First 1
$metadata = (Request GET ("/api/staff/tables/" + $table.id + '/qr') $null $staffHeaders).Data
Assert-Check ((Request GET ("/api/tables/resolve?tableNumber=3&tableToken=" + $metadata.tableToken)).Status -eq 200) 'QR resolve failed'
$outputDir = Join-Path $PSScriptRoot '../.local'
[void][IO.Directory]::CreateDirectory($outputDir)
Invoke-WebRequest -Uri ($BaseUrl + '/api/staff/tables/' + $table.id + '/qr?format=png') -Headers $staffHeaders -OutFile (Join-Path $outputDir 'table-3.png')
$kds = $null
$guest = $null
try {
    $kds = Connect-Stomp $login.Data.accessToken '/topic/kitchen/orders'
    $guest = Connect-Stomp '' '/topic/menu/updates'
    $key = [Guid]::NewGuid().ToString()
    $body = @{tableNumber=3;tableToken=$metadata.tableToken;items=@(@{menuItemId=$lagman.id;quantity=2},@{menuItemId=$tea.id;quantity=1})}
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $created = Request POST '/api/orders' $body @{'Idempotency-Key'=$key}
    Assert-Check ($created.Status -eq 201) 'Order creation failed'
    $event = Event $kds
    $watch.Stop()
    Assert-Check ($event.type -eq 'NEW_ORDER' -and $event.orderId -eq $created.Data.id) 'KDS NEW_ORDER mismatch'
    $expected = 2 * [decimal]$lagman.price + [decimal]$tea.price
    Assert-Check ([decimal]$created.Data.totalAmount -eq $expected) 'Server total mismatch'
    $repeat = Request POST '/api/orders' $body @{'Idempotency-Key'=$key}
    Assert-Check ($repeat.Status -eq 200 -and $repeat.Data.id -eq $created.Data.id) 'Idempotent retry failed'
    $orderId = $created.Data.id
    Assert-Check ($orderId -in @((Request GET '/api/orders/active' $null $staffHeaders).Data | ForEach-Object id)) 'Order missing from active snapshot'
    Assert-Check ((Request PATCH ("/api/orders/$orderId/status") @{status='COOKING'} $staffHeaders).Status -eq 200) 'COOKING failed'
    Assert-Check ((Event $kds).newStatus -eq 'COOKING') 'COOKING event missing'
    Assert-Check ((Request PATCH ("/api/orders/$orderId/complete") $null $staffHeaders).Status -eq 200) 'Complete failed'
    Assert-Check ((Event $kds).newStatus -eq 'COMPLETED') 'COMPLETED event missing'
    $active = (Request GET '/api/orders/active' $null $staffHeaders).Data
    Assert-Check ($orderId -notin @($active | ForEach-Object id)) 'Completed order still active'
    Assert-Check ((Request PATCH ("/api/menu/" + $lagman.id + '/availability') @{isAvailable=$false} $staffHeaders).Status -eq 200) 'Stop-list update failed'
    Assert-Check ((Event $guest).isAvailable -eq $false) 'Guest stop-list event missing'
    $blocked = Request POST '/api/orders' $body @{'Idempotency-Key'=[Guid]::NewGuid().ToString()}
    Assert-Check ($blocked.Status -eq 409 -and $blocked.Data.code -eq 'MENU_ITEM_UNAVAILABLE') 'Unavailable dish was accepted'
    Assert-Check ((Request GET '/api/orders/active').Status -eq 401) 'Anonymous KDS access was allowed'
    # Exercise real admin CRUD, including soft deletion and history-safe category protection.
    $category = Request POST '/api/categories' @{name=('Smoke ' + [Guid]::NewGuid().ToString());sortOrder=99} $staffHeaders
    Assert-Check ($category.Status -eq 201) 'Category creation failed'
    Assert-Check ((Request PUT ("/api/categories/" + $category.Data.id) @{name=('Smoke updated ' + [Guid]::NewGuid().ToString());sortOrder=98} $staffHeaders).Status -eq 200) 'Category update failed'
    $unused = Request POST '/api/categories' @{name=('Unused smoke ' + [Guid]::NewGuid().ToString());sortOrder=100} $staffHeaders
    Assert-Check ((Request DELETE ("/api/categories/" + $unused.Data.id) $null $staffHeaders).Status -eq 204) 'Unused category deletion failed'
    $tableNumber = Get-Random -Minimum 1000 -Maximum 10000
    $newTable = Request POST '/api/staff/tables' @{number=$tableNumber} $staffHeaders
    Assert-Check ($newTable.Status -eq 201) 'Table creation failed'
    Assert-Check ((Request PATCH ("/api/staff/tables/" + $newTable.Data.id) @{isActive=$false} $staffHeaders).Status -eq 200) 'Table update failed'
    $dish = @{name='Smoke dish';description='Docker CRUD smoke';price=10.05;imageUrl=$null;categoryId=$category.Data.id;isAvailable=$true;isActive=$true}
    $newItem = Request POST '/api/menu' $dish $staffHeaders
    Assert-Check ($newItem.Status -eq 201) 'Menu create failed'
    $dish.price = 11.05
    Assert-Check ((Request PUT ("/api/menu/" + $newItem.Data.id) $dish $staffHeaders).Status -eq 200) 'Menu update failed'
    Assert-Check ((Request DELETE ("/api/menu/" + $newItem.Data.id) $null $staffHeaders).Status -eq 204) 'Menu soft delete failed'
    Assert-Check ((Request DELETE ("/api/categories/" + $category.Data.id) $null $staffHeaders).Status -eq 409) 'Referenced category was removed'
    $summary = @{idempotencyKey=$key;orderId=$orderId;totalAmount=$created.Data.totalAmount;status='COMPLETED';httpToStompMs=$watch.ElapsedMilliseconds;qrPng='table-3.png'}
    $summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputDir 'smoke.json')
    Write-Output ('Smoke passed: orderId=' + $orderId + '; total=' + $created.Data.totalAmount + ' KGS; HTTP to STOMP=' + $watch.ElapsedMilliseconds + ' ms')
} finally {
    if ($lagman) { [void](Request PATCH ("/api/menu/" + $lagman.id + '/availability') @{isAvailable=$true} $staffHeaders) }
    foreach ($socket in @($kds,$guest)) { if ($null -ne $socket) { $socket.Abort(); $socket.Dispose() } }
}
