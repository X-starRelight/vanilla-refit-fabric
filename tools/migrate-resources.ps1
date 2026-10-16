<#
.SYNOPSIS
    原版精修 NeoForge(main 分支) → Fabric 资源迁移脚本。

.DESCRIPTION
    对指定资源根目录执行三次幂等变换：
      1. 文本内容重命名：btsdhz_original → vanilla_refit_fabric（只处理 *.json / *.mcmeta，
         不碰 PNG 等二进制；内容里没有旧命名空间时是无操作）
      2. 路径重命名：目录名/文件名含 btsdhz_original 的改名
      3. 物品模型格式迁移（-Items）：assets/<ns>/models/item/<name>.json
         → assets/<ns>/items/<name>.json（26.2 客户端物品定义格式）。
         只有 parent 的直接指向父模型；带额外内容的保留为普通模型并指向它自己。

    用完即重复运行也安全（已迁移的部分不会被二次处理）。

.EXAMPLE
    # 先从 main 分支取出资源，再执行迁移：
    git checkout main -- src/main/resources src/generated/resources
    powershell -File tools/migrate-resources.ps1 -Items
#>
param(
    # 资源根目录（相对仓库根）
    [string[]]$Roots = @("src/main/resources", "src/generated/resources"),
    # 是否执行 models/item → items 迁移
    [switch]$Items,
    # 旧/新命名空间
    [string]$OldNs = "btsdhz_original",
    [string]$NewNs = "vanilla_refit_fabric"
)

$ErrorActionPreference = "Stop"

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$textExts = @(".json", ".mcmeta", ".lang", ".txt", ".md")

function Read-Text([string]$path) {
    # 自动识别 BOM；统一以无 BOM UTF-8 写回
    return [System.IO.File]::ReadAllText($path)
}
function Write-Text([string]$path, [string]$text) {
    [System.IO.File]::WriteAllText($path, $text, $utf8NoBom)
}

$stats = @{ ContentRenamed = 0; FilesRenamed = 0; DirsRenamed = 0; ItemModelsConverted = 0; ItemModelsKept = 0 }

foreach ($root in $Roots) {
    if (-not (Test-Path $root)) { continue }
    $rootFull = (Resolve-Path $root).Path

    # ---- 1. 文本内容重命名 ----
    Get-ChildItem -Path $rootFull -Recurse -File | Where-Object { $textExts -contains $_.Extension.ToLower() } | ForEach-Object {
        $text = Read-Text $_.FullName
        if ($text.Contains($OldNs)) {
            Write-Text $_.FullName ($text.Replace($OldNs, $NewNs))
            $stats.ContentRenamed++
        }
    }

    # ---- 2. 路径重命名（先深后浅，避免父目录改名后找不到子路径）----
    $nsDirs = Get-ChildItem -Path $rootFull -Recurse -Directory | Where-Object { $_.Name -like "*$OldNs*" } | Sort-Object { $_.FullName.Length } -Descending
    foreach ($d in $nsDirs) {
        $newName = $d.Name.Replace($OldNs, $NewNs)
        Rename-Item -Path $d.FullName -NewName $newName
        $stats.DirsRenamed++
    }
    $nsFiles = Get-ChildItem -Path $rootFull -Recurse -File | Where-Object { $_.Name -like "*$OldNs*" }
    foreach ($f in $nsFiles) {
        Rename-Item -Path $f.FullName -NewName ($f.Name.Replace($OldNs, $NewNs))
        $stats.FilesRenamed++
    }

    # ---- 3. models/item → items 迁移 ----
    if ($Items) {
        # 需要从最深层往上处理，且每次转换后目录可能变空
        $itemModelDirs = Get-ChildItem -Path $rootFull -Recurse -Directory -Filter "item" |
            Where-Object { $_.Parent.Name -eq "models" }
        foreach ($dir in $itemModelDirs) {
            # 该 models 目录的命名空间：assets/<ns>/models/item
            $assetsOrData = $dir.Parent.Parent
            $ns = $assetsOrData.Name
            $itemsDir = Join-Path $assetsOrData.FullName "items"
            if (-not (Test-Path $itemsDir)) { New-Item -ItemType Directory -Path $itemsDir | Out-Null }

            Get-ChildItem -Path $dir.FullName -Filter "*.json" -File | ForEach-Object {
                $name = $_.BaseName
                $json = (Read-Text $_.FullName) | ConvertFrom-Json

                # 判定：对象只有 parent 键 → 直接指向父模型；否则保留原模型文件
                $props = @($json.PSObject.Properties.Name)
                $onlyParent = ($props.Count -eq 1 -and $props -contains "parent")

                $itemDef = if ($onlyParent) {
                    [ordered]@{ model = [ordered]@{ type = "minecraft:model"; model = $json.parent } }
                } else {
                    [ordered]@{ model = [ordered]@{ type = "minecraft:model"; model = "${ns}:item/${name}" } }
                }
                $outPath = Join-Path $itemsDir "$name.json"
                Write-Text $outPath (($itemDef | ConvertTo-Json -Depth 10) + "`n")

                if ($onlyParent) {
                    Remove-Item -Path $_.FullName  # 只有 parent 的旧文件不再需要
                } else {
                    $stats.ItemModelsKept++         # 保留为普通模型，items 定义指向它
                }
                $stats.ItemModelsConverted++
            }

            # 清掉空的 models/item 目录
            if (-not (Get-ChildItem -Path $dir.FullName -File -ErrorAction SilentlyContinue)) {
                Remove-Item -Path $dir.FullName -Recurse -Force
            }
        }
    }
}

"迁移完成：内容重命名 $($stats.ContentRenamed) 个文件；目录改名 $($stats.DirsRenamed) 个；文件改名 $($stats.FilesRenamed) 个；" +
"物品模型转换 $($stats.ItemModelsConverted) 个（其中保留原模型 $($stats.ItemModelsKept) 个）"
