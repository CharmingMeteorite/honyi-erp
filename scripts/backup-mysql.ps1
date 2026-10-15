param([string]$BackupDirectory = '')

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $BackupDirectory) { $BackupDirectory = Join-Path $root '.local/backups' }
$BackupDirectory = [System.IO.Path]::GetFullPath($BackupDirectory)
if ($BackupDirectory.StartsWith($root + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -and
        -not $BackupDirectory.StartsWith((Join-Path $root '.local') + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Backups inside this Git workspace must be under ignored .local/'
}
$localEnv = Join-Path $root '.env'
if (Test-Path $localEnv) {
    foreach ($line in Get-Content $localEnv) {
        if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$' -and -not [Environment]::GetEnvironmentVariable($matches[1], 'Process')) {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
        }
    }
}
if ($env:DB_URL -notmatch '^jdbc:mysql://192\.168\.1\.247:3307/erp(?:\?|$)') { throw 'DB_URL does not match the designated database' }
if (-not $env:DB_USERNAME -or -not $env:DB_PASSWORD) { throw 'DB_USERNAME and DB_PASSWORD are required' }
$dump = Get-Command mysqldump.exe -ErrorAction SilentlyContinue
if (-not $dump) {
    $candidate = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe'
    if (-not (Test-Path $candidate)) { throw 'mysqldump.exe is not installed or not on PATH' }
    $dumpPath = $candidate
} else {
    $dumpPath = $dump.Source
}
& $dumpPath --version
if ($LASTEXITCODE -ne 0) { throw 'mysqldump version check failed' }
New-Item -ItemType Directory -Force -Path $BackupDirectory | Out-Null
$optionFile = Join-Path $BackupDirectory ('.mysql-client-' + [guid]::NewGuid().ToString('N') + '.cnf')
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$partial = Join-Path $BackupDirectory "erp-$stamp.sql.partial"
$complete = Join-Path $BackupDirectory "erp-$stamp.sql"
try {
    # The temporary client file keeps the password out of process arguments and command logs.
    $content = "[client]`r`nhost=192.168.1.247`r`nport=3307`r`nuser=$env:DB_USERNAME`r`npassword=$env:DB_PASSWORD`r`n"
    [System.IO.File]::WriteAllText($optionFile, $content, [System.Text.UTF8Encoding]::new($false))
    & $dumpPath "--defaults-extra-file=$optionFile" --single-transaction --quick --routines --triggers --events --hex-blob --default-character-set=utf8mb4 --set-gtid-purged=OFF --column-statistics=0 --databases erp "--result-file=$partial"
    if ($LASTEXITCODE -ne 0) { throw 'mysqldump failed; partial backup is not valid' }
    $file = Get-Item -LiteralPath $partial
    if ($file.Length -lt 10000) { throw 'Backup is unexpectedly small' }
    $sql = [System.IO.File]::ReadAllText($partial)
    if (-not $sql.Contains('flyway_schema_history') -or -not $sql.Contains('Dump completed on')) {
        throw 'Backup is missing Flyway history or completion marker'
    }
    Move-Item -LiteralPath $partial -Destination $complete
    $hash = (Get-FileHash -LiteralPath $complete -Algorithm SHA256).Hash
    Write-Host "Backup: $complete"
    Write-Host "Size: $((Get-Item -LiteralPath $complete).Length) bytes"
    Write-Host "SHA-256: $hash"
} finally {
    Remove-Item -LiteralPath $optionFile -ErrorAction SilentlyContinue
    if (Test-Path $partial) { Remove-Item -LiteralPath $partial }
}
