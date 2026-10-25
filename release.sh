#!/usr/bin/env bash
# 构建 release 包。签名信息全部在运行时输入，不写入任何文件：
#   - 密码用 read -s，不进 shell 历史
#   - 通过 ORG_GRADLE_PROJECT_* 环境变量传给 gradle
#   - keystore 路径可预先 export LAP_KEYSTORE，免得每次手输
set -euo pipefail
cd "$(dirname "$0")"

KS="${LAP_KEYSTORE:-}"
if [[ -z "$KS" ]]; then
    read -rp "keystore 路径: " KS
fi

if [[ -z "$KS" || ! -f "$KS" ]]; then
    echo "keystore 无效：${KS:-<未提供>}" >&2
    exit 1
fi

read -rp "key alias: " ALIAS
if [[ -z "$ALIAS" ]]; then
    echo "alias 不能为空" >&2
    exit 1
fi

read -rsp "store 密码: " SP; echo
read -rsp "key 密码（直接回车表示与 store 相同）: " KP; echo
KP="${KP:-$SP}"

export ORG_GRADLE_PROJECT_storeFile="$KS"
export ORG_GRADLE_PROJECT_storePassword="$SP"
export ORG_GRADLE_PROJECT_keyAlias="$ALIAS"
export ORG_GRADLE_PROJECT_keyPassword="$KP"

./gradlew :app:assembleRelease "$@"

echo
echo "=== 产物 ==="
ls -la app/build/outputs/apk/release/*.apk 2>/dev/null || echo "未找到 apk，检查上面的构建输出"
