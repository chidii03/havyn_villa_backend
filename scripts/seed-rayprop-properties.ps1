param(
    [string]$ApiBaseUrl = "http://localhost:8080",
    [Parameter(Mandatory = $true)]
    [string]$Email,
    [Parameter(Mandatory = $true)]
    [string]$Password
)

$ErrorActionPreference = "Stop"

$loginBody = @{
    email = $Email
    password = $Password
} | ConvertTo-Json

$login = Invoke-RestMethod `
    -Method Post `
    -Uri "$ApiBaseUrl/api/v1/auth/login" `
    -ContentType "application/json" `
    -Body $loginBody

$headers = @{
    Authorization = "Bearer $($login.accessToken)"
}

$result = Invoke-RestMethod `
    -Method Post `
    -Uri "$ApiBaseUrl/api/v1/admin/rayprop/sync" `
    -Headers $headers

$result | ConvertTo-Json -Depth 5
