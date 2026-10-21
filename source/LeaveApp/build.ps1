$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$bt = Join-Path $sdk 'build-tools\36.0.0'
$androidJar = Join-Path $sdk 'platforms\android-36.1\android.jar'
$build = Join-Path $root 'build'
$classes = Join-Path $build 'classes'
$gen = Join-Path $build 'gen'
$dex = Join-Path $build 'dex'
$compiled = Join-Path $build 'compiled'

Remove-Item $classes, $gen, $dex, $compiled -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $classes, $gen, $dex, $compiled | Out-Null

& (Join-Path $bt 'aapt2.exe') compile --dir (Join-Path $root 'res') -o (Join-Path $compiled 'res.zip')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

& (Join-Path $bt 'aapt2.exe') link -o (Join-Path $build 'base.apk') -I $androidJar `
    --manifest (Join-Path $root 'AndroidManifest.xml') --java $gen `
    --min-sdk-version 23 --target-sdk-version 36 --version-code 1022 --version-name LV.999.1 `
    --auto-add-overlay (Join-Path $compiled 'res.zip')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

$sources = @(Get-ChildItem (Join-Path $root 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources += Join-Path $gen 'com\huzhou\leaveapp\R.java'
& javac --release 8 -encoding UTF-8 -classpath $androidJar -d $classes $sources 2>&1 |
    Where-Object { $_ -notmatch 'source value 8 is obsolete|target value 8 is obsolete|To suppress warnings|warning' }
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

$classFiles = @(Get-ChildItem $classes -Recurse -Filter '*.class' | ForEach-Object FullName)
& (Join-Path $bt 'd8.bat') --lib $androidJar --min-api 23 --output $dex $classFiles
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Copy-Item (Join-Path $build 'base.apk') (Join-Path $build 'unsigned.apk') -Force
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open(
    (Join-Path $build 'unsigned.apk'),
    [System.IO.Compression.ZipArchiveMode]::Update)
$old = $zip.GetEntry('classes.dex')
if ($old) { $old.Delete() }
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
    $zip, (Join-Path $dex 'classes.dex'), 'classes.dex',
    [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
$zip.Dispose()

& (Join-Path $bt 'zipalign.exe') -f 4 (Join-Path $build 'unsigned.apk') (Join-Path $build 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

$keystore = Join-Path $build 'leave.keystore'
if (-not (Test-Path $keystore)) {
    & keytool -genkeypair -v -keystore $keystore -storepass leaveapp2026 `
        -keypass leaveapp2026 -alias leave -keyalg RSA -keysize 2048 -validity 10000 `
        -dname 'CN=Leave Program, OU=Local, O=Huzhou, L=Huzhou, ST=Zhejiang, C=CN'
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}

$outApk = Join-Path (Split-Path $root -Parent) 'LeaveProgram.apk'
& (Join-Path $bt 'apksigner.bat') sign --ks $keystore --ks-pass pass:leaveapp2026 `
    --key-pass pass:leaveapp2026 --ks-key-alias leave --v1-signing-enabled true `
    --v2-signing-enabled true --v3-signing-enabled true --min-sdk-version 23 `
    --out $outApk (Join-Path $build 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

& (Join-Path $bt 'apksigner.bat') verify --verbose $outApk
Write-Output "Built: $outApk"
