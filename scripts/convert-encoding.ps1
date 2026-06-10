<#
Convert-encoding.ps1

Converte recursivamente arquivos .properties e .html em src/main/resources para UTF-8.
Cria backups com extensão .bak antes de sobrescrever.

Uso:
  # Dry run: lista arquivos que seriam convertidos
  .\scripts\convert-encoding.ps1 -DryRun

  # Converter de fato
  .\scripts\convert-encoding.ps1

Observação: este script usa a codificação Default do PowerShell ao ler o arquivo original.
Se seus arquivos estiverem em uma codificação específica (ex: Windows-1252), execute via Git Bash
ou WSL com iconv, ou modifique o script para usar -Encoding "OEM"/"Default" conforme necessário.
#>

param(
    [switch]$DryRun
)

$root = Join-Path -Path $PSScriptRoot -ChildPath ".."
$resources = Join-Path -Path $root -ChildPath "src\main\resources"

if (-not (Test-Path $resources)) {
    Write-Error "Diretório não encontrado: $resources"
    exit 1
}

$patterns = @('*.properties','*.html')

Get-ChildItem -Path $resources -Recurse -Include $patterns -File | ForEach-Object {
    $file = $_.FullName
    if ($DryRun) {
        Write-Host "[DRY] Would convert: $file"
        return
    }

    $bak = "$file.bak"
    try {
        Copy-Item -Path $file -Destination $bak -Force
        Write-Host "Backup criado: $bak"

        # Ler com codificação Default (plataforma) e escrever em UTF8
        $content = Get-Content -Raw -Encoding Default -Path $file
        Set-Content -Path $file -Value $content -Encoding UTF8
        Write-Host "Convertido para UTF-8: $file"
    }
    catch {
        Write-Warning "Falha ao converter $file: $_"
    }
}

Write-Host "Concluído. Verifique backups (.bak) antes de commitar."

