param([string]$Python = "python")
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$engine = Join-Path $project 'engine'
$archive = Join-Path ([IO.Path]::GetTempPath()) ("void-python-" + [guid]::NewGuid() + ".zip")
try {
    Invoke-WebRequest 'https://www.python.org/ftp/python/3.13.12/python-3.13.12-embed-amd64.zip' -OutFile $archive
    if ((Get-FileHash $archive).Hash -ne '76F238F606250C87C6BEAC75DCCD35EE99070A13490555936ABB6CB64ECCE3D0') {throw 'Python archive checksum mismatch'}
    Expand-Archive $archive $engine -Force
} finally {if (Test-Path $archive) {Remove-Item -LiteralPath $archive}}
& $Python -m pip install --upgrade --no-compile --only-binary=:all: --platform win_amd64 --python-version 3.13 --implementation cp --abi cp313 --target "$engine/packages" minecraft-launcher-lib==8.0 requests==2.34.2 charset-normalizer==3.5.2 idna==3.20 urllib3==2.8.0 certifi==2026.10.23
if ($LASTEXITCODE) {throw 'Could not install launcher dependencies'}
Add-Content "$engine/python313._pth" 'packages'
Copy-Item "$PSScriptRoot/launch.py" $engine -Force
& "$env:WINDIR/Microsoft.NET/Framework64/v4.0.30319/csc.exe" /nologo /target:winexe /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll /r:System.Security.dll /r:System.Web.Extensions.dll "/out:$project\Void Setup.exe" "$PSScriptRoot\Desktop.cs"
if ($LASTEXITCODE) {throw 'Could not compile the desktop launcher'}
Copy-Item "$project/Void Setup.exe" "$project/Void Launcher.exe" -Force

