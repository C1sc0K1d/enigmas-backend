param([ValidateRange(1, 65535)][int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $projectRoot '.env.properties'
$tokenLine = Get-Content -LiteralPath $configPath | Where-Object { $_ -match '^master\.reset-token=' } | Select-Object -Last 1
if (-not $tokenLine) { throw 'Chave do mestre ausente em .env.properties (master.reset-token).' }
$masterToken = $tokenLine.Substring($tokenLine.IndexOf('=') + 1).Trim()
if (-not $masterToken) { throw 'Chave do mestre vazia.' }
Write-Host 'Isso reinicia a partida de TODOS os jogadores: historicos, modos, conexoes e desbloqueios.'
$confirmation = Read-Host 'Digite REINICIAR para confirmar'
if ($confirmation -cne 'REINICIAR') { Write-Host 'Reset cancelado.'; return }
try {
  $null = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$Port/api/master/reset" -Headers @{ 'X-Master-Token' = $masterToken }
  Write-Host 'Partida reiniciada. Os jogadores conectados receberao o novo estado por SSE.'
} catch {
  throw 'Nao foi possivel confirmar o reset. Confira o backend, a porta e as telas antes de tentar novamente.'
} finally {
  $masterToken = $null
}