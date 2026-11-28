#!/bin/sh
set -eu

load_protected_env() {
    file=$1
    if [ ! -f "$file" ]; then
        return
    fi
    mode=$(ls -l "$file" | awk '{print $1}')
    case "$mode" in
        -rw-------) ;;
        *)
            echo ".env 必须仅所有者可读写，拒绝加载" >&2
            exit 1
            ;;
    esac
    while IFS= read -r line || [ -n "$line" ]; do
        case "$line" in
            ''|\#*) continue ;;
        esac
        key=${line%%=*}
        value=${line#*=}
        case "$key" in
            MTX_RDP_DEV_MYSQL_USER|MTX_RDP_DEV_MYSQL_PASSWORD|MTX_RDP_DEV_DORIS_USER|MTX_RDP_DEV_DORIS_PASSWORD)
                export "$key=$value"
                ;;
        esac
    done < "$file"
}

require() {
    eval "value=\${$1-}"
    if [ -z "$value" ]; then
        echo "缺少环境变量: $1" >&2
        exit 1
    fi
}

reject_root() {
    eval "value=\${$1-}"
    lowered=$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')
    if [ "$lowered" = "root" ]; then
        echo "$1 不能使用 root" >&2
        exit 1
    fi
}

APP=${1:-}
case "$APP" in
    admin) MODULE=app-admin ;;
    worker) MODULE=app-worker ;;
    *)
        echo "用法: scripts/start-local.sh admin|worker" >&2
        exit 1
        ;;
esac

ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$ROOT"
load_protected_env "$ROOT/.env"

require MTX_RDP_DEV_MYSQL_USER
require MTX_RDP_DEV_MYSQL_PASSWORD
require MTX_RDP_DEV_DORIS_USER
require MTX_RDP_DEV_DORIS_PASSWORD
reject_root MTX_RDP_DEV_MYSQL_USER
reject_root MTX_RDP_DEV_DORIS_USER

echo "开发环境: LOCAL"
echo "应用: $APP"
echo "敏感变量已设置，不显示取值"
echo "不执行 DDL，不清理数据"

export SPRING_PROFILES_ACTIVE=local
exec ./mvnw -pl "$MODULE" spring-boot:run
