param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backend = Join-Path $root 'erp'
$frontend = Join-Path $root 'erp-vue'
$dist = Join-Path $frontend 'dist'
$release = Join-Path $root '.local/releases'

function Invoke-Checked([string]$name, [scriptblock]$action) {
    Write-Host "== $name =="
    & $action
    if ($LASTEXITCODE -ne 0) { throw "$name failed (exit $LASTEXITCODE)" }
}

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    throw 'Set JAVA_HOME to an installed Java 17 JDK before building.'
}
$env:PATH = "$(Join-Path $env:JAVA_HOME 'bin');$env:PATH"
$javaVersion = (& java -version 2>&1 | Select-Object -First 1).ToString()
if ($javaVersion -notmatch 'version "17\.') { throw "Java 17 is required; found $javaVersion" }
Write-Host $javaVersion
foreach ($command in @('node', 'npm')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { throw "$command is missing" }
}
Invoke-Checked 'Node version' { node --version }
Invoke-Checked 'npm version' { npm --version }
$nodeVersion = (node --version).TrimStart('v')
if ([version]$nodeVersion -lt [version]'22.18.0') { throw "Node.js 22.18+ is required; found $nodeVersion" }
if (-not (Test-Path (Join-Path $backend 'mvnw.cmd'))) { throw 'Maven Wrapper is missing' }
if (-not (Test-Path (Join-Path $frontend 'package-lock.json'))) { throw 'Front-end lock file is missing' }

# The ignored local file is a convenience for development. Explicit process variables win.
$localEnv = Join-Path $root '.env'
if (Test-Path $localEnv) {
    foreach ($line in Get-Content $localEnv) {
        if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$' -and -not [Environment]::GetEnvironmentVariable($matches[1], 'Process')) {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
        }
    }
}
if ($env:DB_URL -notmatch '^jdbc:mysql://192\.168\.1\.247:3307/erp(?:\?|$)') {
    throw 'DB_URL must target the designated development database for this regression build.'
}
foreach ($key in @('DB_USERNAME', 'DB_PASSWORD', 'MINIO_ENDPOINT', 'MINIO_BUCKET', 'MINIO_ACCESS_KEY', 'MINIO_SECRET_KEY')) {
    if (-not [Environment]::GetEnvironmentVariable($key, 'Process')) { throw "$key is not configured" }
}
if ($env:VITE_API_BASE_URL) { throw 'Unset VITE_API_BASE_URL: the release must use same-origin /api requests.' }
$env:VITE_API_BASE_URL = ''
$env:ERP_MYSQL_TEST = 'true'
$env:ERP_MINIO_TEST = 'true'

Push-Location $frontend
try {
    Invoke-Checked 'npm ci' { npm ci }
    Invoke-Checked 'Vue production build' { npm run build }
} finally { Pop-Location }
if (-not (Test-Path (Join-Path $dist 'index.html'))) { throw 'Vue dist/index.html is missing' }
if (-not (Get-ChildItem (Join-Path $dist 'assets') -Filter '*.js' -ErrorAction SilentlyContinue)) { throw 'Vue JS asset is missing' }
if (-not (Get-ChildItem (Join-Path $dist 'assets') -Filter '*.css' -ErrorAction SilentlyContinue)) { throw 'Vue CSS asset is missing' }
if (Get-ChildItem (Join-Path $dist 'assets') -Filter '*.js' | Select-String -Pattern 'http://localhost:8080|http://127\.0\.0\.1:8080' -Quiet) {
    throw 'Production JS contains a fixed local API address'
}
$builtJs = Get-ChildItem (Join-Path $dist 'assets') -Filter '*.js'
foreach ($key in @('DB_PASSWORD', 'MINIO_SECRET_KEY')) {
    $secret = [Environment]::GetEnvironmentVariable($key, 'Process')
    if ($secret.Length -ge 6) {
        foreach ($file in $builtJs) {
            if ([System.IO.File]::ReadAllText($file.FullName).Contains($secret)) {
                throw "Production JS unexpectedly contains $key"
            }
        }
    }
}

Push-Location $backend
try {
    Invoke-Checked 'Flyway validate' { .\mvnw.cmd -q flyway:validate }
    Invoke-Checked 'Complete Maven test' { .\mvnw.cmd test }
    Invoke-Checked 'Clean Maven package with tests' { .\mvnw.cmd clean package }
} finally { Pop-Location }

$jars = @(Get-ChildItem (Join-Path $backend 'target/erp-*.jar') -File)
if ($jars.Count -ne 1 -or $jars[0].Length -lt 1MB) { throw 'Expected one nonempty executable application JAR' }
$jar = $jars[0]
$entries = & (Join-Path $env:JAVA_HOME 'bin/jar.exe') tf $jar.FullName
if ($LASTEXITCODE -ne 0) { throw 'JAR cannot be read' }
foreach ($entry in @('BOOT-INF/classes/static/index.html', 'BOOT-INF/classes/com/haucheng/erp/ErpApplication.class')) {
    if ($entries -notcontains $entry) { throw "JAR is missing $entry" }
}
if ((Test-Path (Join-Path $dist 'favicon.ico')) -and $entries -notcontains 'BOOT-INF/classes/static/favicon.ico') {
    throw 'JAR is missing favicon.ico'
}
foreach ($pattern in @('^BOOT-INF/classes/static/assets/.+\.js$', '^BOOT-INF/classes/static/assets/.+\.css$', '^BOOT-INF/lib/.+\.jar$')) {
    if (-not ($entries | Where-Object { $_ -match $pattern } | Select-Object -First 1)) { throw "JAR is missing $pattern" }
}
$archive = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
try {
    $manifestEntry = $archive.GetEntry('META-INF/MANIFEST.MF')
    if (-not $manifestEntry) { throw 'JAR manifest is missing' }
    $reader = [System.IO.StreamReader]::new($manifestEntry.Open())
    try { $manifestText = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($manifestText -notmatch 'Main-Class: org\.springframework\.boot\.loader\.launch\.JarLauncher') {
        throw 'JAR does not have the Spring Boot executable entry point'
    }
} finally { $archive.Dispose() }
$commit = (git -C $root rev-parse --short=12 HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Git commit is unavailable' }
New-Item -ItemType Directory -Force -Path $release | Out-Null
$output = Join-Path $release "$($jar.BaseName)-$commit.jar"
Copy-Item -LiteralPath $jar.FullName -Destination $output -Force
$published = Get-Item -LiteralPath $output
$sha = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash
Write-Host "Release JAR: $($published.FullName)"
Write-Host "Size: $($published.Length) bytes"
Write-Host "Git: $commit"
Write-Host "SHA-256: $sha"
