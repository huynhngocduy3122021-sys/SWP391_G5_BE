[System.Reflection.Assembly]::LoadWithPartialName("Microsoft.SqlServer.SMO") | Out-Null
$server = New-Object Microsoft.SqlServer.Management.Smo.Server("localhost,1433")
$server.ConnectionContext.LoginSecure = $false
$server.ConnectionContext.Login = "sa"
$server.ConnectionContext.Password = "12345"
$server.ConnectionContext.Connect()

$db = $server.Databases["parking_system"]
if ($db -eq $null) {
    Write-Host "Database parking_system not found."
    exit 1
}

$scripter = New-Object Microsoft.SqlServer.Management.Smo.Scripter($server)
$scripter.Options.ScriptSchema = $true
$scripter.Options.ScriptData = $false
$scripter.Options.Indexes = $true
$scripter.Options.DriAll = $true
$scripter.Options.Triggers = $true
$scripter.Options.IncludeHeaders = $false

$tables = $db.Tables | Where-Object { $_.IsSystemObject -eq $false }
$script = $scripter.Script($tables)

$outFile = "d:\Project\SWP391_G5_BE\parking_system_schema.sql"
$script | Out-File -FilePath $outFile -Encoding UTF8
Write-Host "Schema exported to $outFile"
