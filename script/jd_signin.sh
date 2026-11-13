#!/system/bin/sh
# ==============================================================================
# 京东签到调度脚本（v0.1 采集版）
#
# 分工：
#   本脚本   → 定时、拉起京东、等待模块产出、发通知
#   LSPosed 模块 → 在京东进程里把签到页跑起来、注入 JS、点签到、抓请求
#
# 为什么不直接在脚本里 curl：
#   signBeanAct 用纯 cookie 请求返回 402「活动现在挤不进去呀」，
#   这是风控门；whwswswws / h5st 不落盘，只能由 app 运行时生成。
#
# 日志：/data/adb/crond/jd_signin.log
# 数据：/data/user/0/com.jingdong.app.mall/files/jdhook/
# ==============================================================================

export PATH="/data/adb/ksu/bin:/data/adb/magisk:/system/bin:/system/xbin:/vendor/bin:$PATH"

PKG="com.jingdong.app.mall"
DIR="/data/user/0/${PKG}/files/jdhook"
LOG_DIR="/data/adb/crond"
LOG_FILE="${LOG_DIR}/jd_signin.log"

# 签到活动页（来自京东客户端内置配置，活动 ID 会随月份变化）
PAGE="https://pro.m.jd.com/mall/active/Md9FMi1pJXg2q7qc8CmE9FNYDS4/index.html?babelChannel=ttt91&jdhook=click"

mkdir -p "$LOG_DIR"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

rotate_log() {
    if [ -f "$LOG_FILE" ]; then
        lines=$(wc -l < "$LOG_FILE" 2>/dev/null | tr -d ' ')
        if [ -n "$lines" ] && [ "$lines" -gt 500 ]; then
            tail -n 300 "$LOG_FILE" > "${LOG_FILE}.tmp" && mv "${LOG_FILE}.tmp" "$LOG_FILE"
        fi
    fi
}

notify() {
    local title body
    title=$(echo "$1" | tr -d "'\"")
    body=$(echo "$2" | tr -d "'\"")
    /system/bin/su 2000 -c "/system/bin/cmd notification post -S bigtext -t '${title}' 'jd_signin_tag' '${body}'" >/dev/null 2>&1
}

# 百分号编码（busybox ash 没有 urlencode）
urlencode() {
    printf '%s' "$1" | while IFS= read -r -n1 c; do
        case "$c" in
            [a-zA-Z0-9.~_-]) printf '%s' "$c" ;;
            *) printf '%%%02X' "'$c" ;;
        esac
    done
}

# ==============================================================================
rotate_log
log "========== 京东签到任务启动 =========="

if [ "$(id -u)" -ne 0 ]; then
    log "[错误] 需要 Root 权限"
    exit 1
fi

if ! pm list packages 2>/dev/null | grep -q "$PKG"; then
    log "[错误] 未安装京东"
    notify "京东签到失败" "未检测到京东客户端"
    exit 1
fi

# 清掉上一轮产物（hook.log 不清，模块每个进程只写一次，清了就误判）
rm -f "$DIR/report.json" "$DIR/report2.json" "$DIR/click.json"
mkdir -p "$DIR"
chmod 777 "$DIR" 2>/dev/null
BASE=$(wc -l < "$DIR/hook.log" 2>/dev/null | tr -d ' ')
[ -n "$BASE" ] || BASE=0

PARAMS="{\"category\":\"jump\",\"des\":\"m\",\"url\":\"${PAGE}\"}"
DEEPLINK="openapp.jdmobile://virtual?params=$(urlencode "$PARAMS")"

log "拉起京东并打开签到页..."
am start -a android.intent.action.VIEW -d "$DEEPLINK" -p "$PKG" >/dev/null 2>&1
START=$(date +%s)

# 等模块产出（注入在 +6s / +13s）
# 注意：京东冷启动实测要 100s 以上，这里给 200s
i=0
while [ "$i" -lt 200 ]; do
    if [ -f "$DIR/report2.json" ] || [ -f "$DIR/report.json" ]; then
        sleep 3
        break
    fi
    sleep 2
    i=$((i + 2))
done

WAITED=$(( $(date +%s) - START ))

if [ ! -f "$DIR/hook.log" ]; then
    log "[失败] 模块没有任何输出（hook.log 不存在，京东进程没起来或模块没加载）"
    notify "京东签到失败" "京东进程未启动或模块未加载，详见 jd_signin.log"
    exit 1
fi

PAGES=$(tail -n +$((BASE + 1)) "$DIR/hook.log" 2>/dev/null | grep -c " page ")
log "本轮模块新记录 ${PAGES} 条页面加载"

if [ ! -f "$DIR/report.json" ] && [ ! -f "$DIR/report2.json" ]; then
    log "[失败] 模块已加载但签到页没有打开（${WAITED}s 内无页面加载）"
    notify "京东签到失败" "模块已加载，但签到页未打开（新页面 ${PAGES} 条）"
    exit 1
fi

RESULT="$DIR/report2.json"
[ -f "$RESULT" ] || RESULT="$DIR/report.json"

log "采集耗时 ${WAITED}s，结果文件 $(basename "$RESULT") ($(wc -c < "$RESULT" 2>/dev/null | tr -d ' ') 字节)"

# 粗略判定（v0.1）：从转义后的 JSON 里直接找特征
SIGN_SEEN=0
grep -q 'signBeanAct' "$RESULT" 2>/dev/null && SIGN_SEEN=1
SIGN_OK=0
grep -q '\\\\"code\\\\":\\\\"0\\\\"' "$RESULT" 2>/dev/null && SIGN_OK=1
ALREADY=0
grep -q '已签到' "$RESULT" 2>/dev/null && ALREADY=1
CLICKED=$(grep -o 'clicked' "$DIR/click.json" 2>/dev/null | head -n 1)
BEAN=$(grep -o -E '[0-9]{1,6}[ ]*京豆' "$RESULT" 2>/dev/null | head -n 1 | tr -d ' ')

if [ "$SIGN_SEEN" = "1" ] && [ "$SIGN_OK" = "1" ]; then
    log "[成功] 签到请求已发出且返回 code=0"
    notify "京东签到成功" "签到请求返回成功${BEAN:+，页面显示 $BEAN}"
elif [ "$ALREADY" = "1" ]; then
    log "[完成] 今日已签到"
    notify "京东今日已签到" "页面显示已签到${BEAN:+，当前 $BEAN}"
elif [ -n "$CLICKED" ]; then
    log "[执行] 已点击签到按钮，等待下次核对"
    notify "京东签到已执行" "已点击签到${BEAN:+，页面显示 $BEAN}，请稍后核对京豆"
else
    log "[失败] 未命中签到按钮，也没有 signBeanAct 请求"
    notify "京东签到失败" "未找到签到入口（${WAITED}s），详见 jd_signin.log"
    exit 1
fi

log "========== 京东签到任务结束 =========="
