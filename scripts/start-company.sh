#!/bin/sh
set -eu

APP=${1:-}
case "$APP" in
    admin) MODULE=app-admin ;;
    worker) MODULE=app-worker ;;
    *)
        echo "用法: scripts/start-company.sh admin|worker" >&2
        exit 1
        ;;
esac

require() {
    eval "value=\${$1-}"
    if [ -z "$value" ]; then
        echo "缺少环境变量: $1" >&2
        exit 1
    fi
}

require MTX_RDP_DEV_MYSQL_USER
require MTX_RDP_DEV_MYSQL_PASSWORD
require MTX_RDP_DEV_DORIS_USER
require MTX_RDP_DEV_DORIS_PASSWORD

echo "开发环境: COMPANY"
echo "应用: $APP"
echo "敏感变量已设置，不显示取值"
echo "不执行 DDL，不清理数据"

export SPRING_PROFILES_ACTIVE=company
cd "$(dirname "$0")/.."
exec ./mvnw -pl "$MODULE" spring-boot:run
