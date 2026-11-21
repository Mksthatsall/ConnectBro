$ErrorActionPreference = 'Stop'

$jarPath = Join-Path $PSScriptRoot 'target\ConnectionsService-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "Connection service JAR not found: $jarPath. Build ConnectionsService first."
}

$usernameInput = Read-Host 'Neo4j username [neo4j]'
$neo4jUsername = if ([string]::IsNullOrWhiteSpace($usernameInput)) { 'neo4j' } else { $usernameInput }
$securePassword = Read-Host 'Neo4j password' -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$previousUser = $env:neo4j
$previousPassword = $env:con!@#123

try {
    $env:NEO4J_USER = $neo4jUsername
    $env:NEO4J_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    & java -jar $jarPath
    exit $LASTEXITCODE
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    if ($null -eq $previousUser) { Remove-Item Env:NEO4J_USER -ErrorAction SilentlyContinue }
    else { $env:NEO4J_USER = $previousUser }
    if ($null -eq $previousPassword) { Remove-Item Env:NEO4J_PASSWORD -ErrorAction SilentlyContinue }
    else { $env:NEO4J_PASSWORD = $previousPassword }
}
