param([switch]$Install)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
if (!(Test-Path -LiteralPath '.asr-venv/Scripts/python.exe')) {python -m venv .asr-venv}
if ($Install -or !(Test-Path -LiteralPath '.asr-venv/ready.txt')) {
 & '.asr-venv/Scripts/python.exe' -m pip install 'torch==2.11.0+cpu' 'torchaudio==2.11.0+cpu' --index-url https://download.pytorch.org/whl/cpu
 if ($LASTEXITCODE -ne 0) {throw 'CPU PyTorch installation failed'}
 & '.asr-venv/Scripts/python.exe' -m pip install -r asr/requirements.lock.txt --index-url https://mirrors.aliyun.com/pypi/simple
 if ($LASTEXITCODE -ne 0) {throw 'FunASR installation failed'}
 Set-Content -LiteralPath '.asr-venv/ready.txt' -Value 'ready'
}
& '.asr-venv/Scripts/python.exe' asr/server.py
