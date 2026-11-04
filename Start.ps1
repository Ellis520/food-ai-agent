param([switch]$Install)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
if (!(Test-Path -LiteralPath 'backend/.env')) {Copy-Item -LiteralPath 'backend/.env.example' -Destination 'backend/.env'}
if (!(Test-Path -LiteralPath 'static/.env')) {Copy-Item -LiteralPath 'static/.env.example' -Destination 'static/.env'}
if (!(Test-Path -LiteralPath '.venv/Scripts/python.exe')) {python -m venv .venv; if ($LASTEXITCODE -ne 0) {throw 'Python environment setup failed'}}
$requirementsHash = (Get-FileHash -LiteralPath 'backend/requirements.lock.txt').Hash
$installedHash = Get-Content -LiteralPath '.venv/ready.txt' -ErrorAction SilentlyContinue
if ($Install -or $installedHash -ne $requirementsHash) {
  & '.venv/Scripts/python.exe' -m pip install -r backend/requirements.lock.txt
  if ($LASTEXITCODE -ne 0) {throw 'Backend installation failed'}
  Set-Content -LiteralPath '.venv/ready.txt' -Value $requirementsHash
}
Push-Location static
try {
 if ($Install -or !(Test-Path -LiteralPath 'node_modules')) {npm ci; if ($LASTEXITCODE -ne 0) {throw 'Frontend installation failed'}}
 $env:VITE_DATA_MODE='api'
 npm run build
 if ($LASTEXITCODE -ne 0) {throw 'Frontend build failed'}
} finally {Pop-Location}
Write-Host 'FoodMoment: http://127.0.0.1:8017  |  Ctrl+C to stop'
& '.venv/Scripts/python.exe' -m uvicorn main:app --host 127.0.0.1 --port 8017 --no-access-log
