#!/usr/bin/env bash
# 一键发布：三个 target × CurseForge + Modrinth + sighs maven。
#
# 用法:
#   ./publish-all.sh                      # 用 .env 里的 PUBLISH_CHANGELOG 或默认 changelog
#   ./publish-all.sh "changelog 文本"      # 命令行直接给 changelog（优先）
#
# 凭据放在仓库根目录的 .env（已被 .gitignore 忽略），格式见 .env.example。
# 注意：同一版本号重发会被平台拒绝，发布前记得改根 gradle.properties 的 mod_version。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ ! -f "$ROOT/.env" ]]; then
    echo "缺少 $ROOT/.env，参照 .env.example 创建一个并填入 token" >&2
    exit 1
fi
set -a
# shellcheck disable=SC1091
source "$ROOT/.env"
set +a

MISSING=()
[[ -z "${CURSEFORGE_TOKEN:-}" ]] && MISSING+=("CURSEFORGE_TOKEN")
[[ -z "${MODRINTH_TOKEN:-}" ]] && MISSING+=("MODRINTH_TOKEN")
[[ -z "${SIGHS_PUBLISH_USER:-}" ]] && MISSING+=("SIGHS_PUBLISH_USER")
[[ -z "${SIGHS_PUBLISH_PASSWORD:-}" ]] && MISSING+=("SIGHS_PUBLISH_PASSWORD")
if ((${#MISSING[@]})); then
    echo ".env 里缺少: ${MISSING[*]}" >&2
    exit 1
fi

export PUBLISH_CHANGELOG="${1:-${PUBLISH_CHANGELOG:-See the project changelog for details.}}"
echo "changelog: $PUBLISH_CHANGELOG"

TARGETS=(forge-1.20.1 neoforge-1.21.1 neoforge-26.1)
FAILED=()

# 串行跑：并行上传 sighs maven 出现过 Connection reset。
for target in "${TARGETS[@]}"; do
    echo
    echo "=== $target ==="
    if (cd "$ROOT/targets/$target" && ./gradlew publishMods publishAllPublicationsToRemoteRepoRepository --console=plain); then
        echo "=== $target 完成 ==="
    else
        echo "=== $target 失败 ===" >&2
        FAILED+=("$target")
    fi
done

echo
if ((${#FAILED[@]})); then
    echo "发布失败: ${FAILED[*]}（已成功的平台不受影响；修复后重跑脚本即可）" >&2
    exit 1
fi
echo "全部发布完成"
