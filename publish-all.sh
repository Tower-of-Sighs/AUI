#!/usr/bin/env bash
# 一键发布：所有 target × CurseForge + Modrinth + sighs maven。
#
# 用法:
#   ./publish-all.sh                      # 用 .env 里的 PUBLISH_CHANGELOG 或默认 changelog
#   ./publish-all.sh "changelog 文本"      # 命令行直接给 changelog（优先）
#
# 凭据放在仓库根目录的 .env（已被 .gitignore 忽略），格式见 .env.example。
# 注意：同一版本号重发会被平台拒绝，发布前记得改根 gradle.properties 的 mod_version。
#
# Gradle JVM 按各 target 的 ci.properties 里 ci.java 选：优先取环境变量 JAVA_HOME_<版本>
# （如 JAVA_HOME_17 / JAVA_HOME_21 / JAVA_HOME_25，可以写进 .env），没设就退回当前
# JAVA_HOME。各 target 要求不同（fabric-1.20.1 要 17，1.21.1 系要 21，26.1 系要 25），
# 只用一个 JAVA_HOME 跑会在配置阶段就失败。
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

TARGETS=(forge-1.18.2 forge-1.19.2 forge-1.20.1 fabric-1.20.1 fabric-1.21.1 fabric-26.1 neoforge-1.21.1 neoforge-26.1)
FAILED=()

# 按 ci.properties 的 ci.java 挑 Gradle JVM；JAVA_HOME_<版本> 优先，否则用脚本启动时的 JAVA_HOME。
AMBIENT_JAVA_HOME="${JAVA_HOME:-}"
java_home_for() {
    local wanted override
    wanted="$(sed -n 's/^ci\.java=//p' "$ROOT/targets/$1/ci.properties" 2>/dev/null | head -1)"
    if [[ -n "$wanted" ]]; then
        override="JAVA_HOME_${wanted}"
        if [[ -n "${!override:-}" ]]; then
            printf '%s' "${!override}"
            return
        fi
    fi
    printf '%s' "$AMBIENT_JAVA_HOME"
}

# 串行跑：并行上传 sighs maven 出现过 Connection reset。
for target in "${TARGETS[@]}"; do
    jdk="$(java_home_for "$target")"
    echo
    echo "=== $target (JAVA_HOME=${jdk:-<未设置>}) ==="
    if (cd "$ROOT/targets/$target" && JAVA_HOME="$jdk" ./gradlew publishMods publishAllPublicationsToRemoteRepoRepository --console=plain); then
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
