param([switch]$Run)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$cache = Join-Path $PSScriptRoot '.tools'
New-Item -ItemType Directory -Force $cache | Out-Null
$gradle = Join-Path $cache 'gradle-9.2.1/bin/gradle.bat'
if (!(Test-Path $gradle)) {
    $zip = Join-Path $cache 'gradle-9.2.1-bin.zip'
    $url = 'https://services.gradle.org/distributions/gradle-9.2.1-bin.zip'
    Invoke-WebRequest $url -OutFile $zip
    $sha = (Invoke-RestMethod ($url + '.sha256')).Trim()
    if ((Get-FileHash $zip).Hash -ne $sha) { throw 'Gradle checksum mismatch' }
    Expand-Archive $zip $cache -Force
}
if (!(Test-Path "$env:JAVA_HOME/bin/javac.exe") -or !((& "$env:JAVA_HOME/bin/javac.exe" -version 2>&1 | Out-String) -match 'javac 21[.]')) {
    $jdkDir = Join-Path $cache 'jdk21'
    $jdk = Get-ChildItem $jdkDir -Filter javac.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if (!$jdk) {
        $meta = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows'
        $pkg = $meta[0].binary.package
        $zip = Join-Path $cache 'jdk21.zip'
        Invoke-WebRequest $pkg.link -OutFile $zip
        if ((Get-FileHash $zip).Hash -ne $pkg.checksum) { throw 'JDK checksum mismatch' }
        Expand-Archive $zip $jdkDir -Force
        $jdk = Get-ChildItem $jdkDir -Filter javac.exe -Recurse | Select-Object -First 1
    }
    $env:JAVA_HOME = $jdk.Directory.Parent.FullName
}
if ($Run) { & $gradle ':runClient' } else { & $gradle build }
exit $LASTEXITCODE

