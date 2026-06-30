param(
    [int]$Port = 3000,
    [string]$Python = ""
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

if (-not $Python) {
    $pythonCommand = Get-Command python -ErrorAction SilentlyContinue
    if ($pythonCommand) {
        $Python = $pythonCommand.Source
    } else {
        $bundled = Join-Path $HOME ".cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
        if (Test-Path -LiteralPath $bundled) {
            $Python = $bundled
        } else {
            throw "Python 3을 찾지 못했습니다. -Python 옵션으로 python.exe 경로를 지정하세요."
        }
    }
}

$Database = Join-Path $Root "data\readersen.sqlite3"
if (-not (Test-Path -LiteralPath $Database)) {
    Write-Host "SQLite DB가 없어 원본 데이터를 변환합니다."
    & $Python (Join-Path $Root "convert_to_sqlite.py")
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "브라우저 주소: http://127.0.0.1:$Port"
& $Python (Join-Path $Root "server.py") --port $Port
