# Раскладывает выгрузку регистратора (recorder.js) по файлам: один снимок — один .txt в папке dom/.
# Вход: файл, в который Claude Code сохранил результат чтения localStorage['__combatsSnapshots']
# (JSON-массив [{type, text}], где text содержит JSON-строку со снимками).
# Пример: powershell -File docs\tools\split-snapshots.ps1 -Source <путь к файлу> -Destination dom
param(
    [Parameter(Mandatory = $true)][string]$Source,
    [string]$Destination = (Join-Path $PSScriptRoot '..\..\dom')
)

New-Item -ItemType Directory -Force $Destination | Out-Null
$wrapper = Get-Content $Source -Raw -Encoding UTF8 | ConvertFrom-Json
$text = ($wrapper | Where-Object { $_.type -eq 'text' } | Select-Object -First 1).text
$start = $text.IndexOf('"{')
$end = $text.LastIndexOf('}"')
$jsonString = ($text.Substring($start, $end - $start + 2) | ConvertFrom-Json)

$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText((Join-Path $Destination 'snapshots.json'), $jsonString, $utf8)

$all = $jsonString | ConvertFrom-Json
foreach ($p in $all.PSObject.Properties) {
    $v = $p.Value
    $file = ($p.Name -replace '^(frame|battle) /?', '$1_' -replace '[^\w=.-]+', '_').Trim('_') + '.txt'
    $lines = @("key: $($p.Name)", "url: $($v.url)", "title: $($v.title)",
               "seen: $($v.seen) times, first $($v.firstSeen), last $($v.time)")
    if ($v.message) { $lines += "battle message: $($v.message)" }
    $lines += "", "=== TEXT ===", $v.text, "", "=== ELEMENTS ($(@($v.elements).Count)) ==="
    $lines += $v.elements
    [IO.File]::WriteAllLines((Join-Path $Destination $file), [string[]]$lines, $utf8)
    "$file  ($(@($v.elements).Count) elements)"
}
