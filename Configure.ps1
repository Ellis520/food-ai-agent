$ErrorActionPreference='Stop'
$backendFile=Join-Path $PSScriptRoot 'backend/.env'
$staticFile=Join-Path $PSScriptRoot 'static/.env'
if (!(Test-Path -LiteralPath $backendFile)) {Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'backend/.env.example') -Destination $backendFile}
if (!(Test-Path -LiteralPath $staticFile)) {Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'static/.env.example') -Destination $staticFile}
function Set-SecretValue([string]$file,[string]$key,[string]$label){
 Write-Host "$label (blank keeps the current value)"
 $secret=Read-Host -AsSecureString
 $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
 try {
  $value=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
  if (!$value) {return}
  if ($value -notmatch '^[A-Za-z0-9_\-]+$') {throw 'Unexpected key characters'}
  $lines=Get-Content -LiteralPath $file
  $found=$false
  $new=foreach($line in $lines){if($line.StartsWith("$key=")){$found=$true;"$key=$value"}else{$line}}
  if(!$found){$new+= "$key=$value"}
  Set-Content -LiteralPath $file -Value $new -Encoding utf8
 } finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)}
}
Set-SecretValue $backendFile 'DASHSCOPE_API_KEY' 'Qwen API Key'
Set-SecretValue $backendFile 'AMAP_WEB_SERVICE_KEY' 'Amap Web Service Key'
Set-SecretValue $staticFile 'VITE_AMAP_JS_KEY' 'Amap JavaScript API Key'
Set-SecretValue $backendFile 'AMAP_SECURITY_CODE' 'Amap JavaScript Security Code'
Write-Host 'Saved locally. Restart FoodMoment to use the updated configuration.'
