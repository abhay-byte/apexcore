#!/system/bin/sh
#
# FactualStats FPS daemon — SAME logic as scripts/adb-app-fps-ondevice.sh
#
# Adreno (kgsl):
#   1) Discover app draw-ctx via dma_fence kgsl-timeline (pid prefix)
#   2) Sample adreno_cmdbatch_submitted for INTERVAL seconds (ring buffer, NOT trace_pipe)
#   3) Primary ctx only (most events among discovered)
#   4) Hybrid FPS = avg(inflight-drop, gap@2ms)  — validated vs 3DMark HUD ~±5%
#   5) Broadcast com.ivarna.apexcore.FPS_DATA
#
# Non-Adreno: fall back to dma_fence + fps_count.awk if present.
# Never filter cmdbatch by main PID — submits often run on child tids.

umask 000
TRACE=/sys/kernel/tracing
DMA_EV=$TRACE/events/dma_fence/dma_fence_signaled/enable
SUB_EV=$TRACE/events/kgsl/adreno_cmdbatch_submitted/enable
COUNT_AWK=/data/local/tmp/apexcore_fps_count.awk
INTERVAL=1
GAP_MS=2.0
FG_FILE=/data/local/tmp/fg_app

# Kill leftover ftrace consumers (only one trace_pipe reader allowed system-wide)
ps -A -o PID= 2>/dev/null | while read p; do
    cmd=$(tr '\0' ' ' < /proc/$p/cmdline 2>/dev/null) || continue
    case "$cmd" in
        *trace_pipe*|*kernel/tracing/trace*|*apexcore_fps*|*adb_app_fps*)
            [ "$p" != "$$" ] && kill -9 $p 2>/dev/null
            ;;
    esac
done
sleep 0.2

echo 16384 > $TRACE/buffer_size_kb 2>/dev/null
echo 0 > $TRACE/tracing_on 2>/dev/null
echo 0 > $DMA_EV 2>/dev/null
[ -f "$SUB_EV" ] && echo 0 > $SUB_EV 2>/dev/null

if [ -d "$TRACE/events/kgsl" ] && [ -f "$SUB_EV" ]; then
    MODE=adreno
    METHOD=cmdbatch-hybrid
else
    MODE=dma
    METHOD=dma-fence
fi

echo "daemon start v4 mode=$MODE method=$METHOD interval=$INTERVAL" >&2

# Foreground poller → fg_app: <pid> <refresh> <package> <pattern>
(
    while true; do
        focus=$(dumpsys window 2>/dev/null | grep mCurrentFocus | head -n 1)
        pkg=$(echo "$focus" | sed -n 's/.*u0 \([^/]*\).*/\1/p' | head -n 1)
        if [ -z "$pkg" ]; then
            pkg=$(echo "$focus" | sed -n 's/.* \([^/ ]*\)\/.*/\1/p' | awk '{print $NF}')
        fi
        pid="0"
        if [ -n "$pkg" ] && [ "$pkg" != "unknown" ]; then
            pid=$(pidof "$pkg" 2>/dev/null | awk '{print $1}')
            [ -z "$pid" ] && pid="0"
        fi
        # Skip our own overlay as "foreground" for FPS (never measure ourselves)
        case "$pkg" in
            *apexcore*) pid="0"; pkg=unknown ;;
        esac
        pat=$(echo "$pkg" | sed 's/.*\.//')
        rr=$(dumpsys display 2>/dev/null | grep -oE 'mActiveRenderFrameRate=[0-9.]+' | head -n 1 | cut -d= -f2)
        if [ -z "$rr" ]; then
            rr=$(dumpsys display 2>/dev/null | grep -oE 'renderFrameRate [0-9.]+' | head -n 1 | awk '{print $2}')
        fi
        echo "${pid:-0} ${rr:-60.0} ${pkg:-unknown} ${pat:-unknown}" > "$FG_FILE"
        sleep 0.5
    done
) &
POLLER_PID=$!

cleanup() {
    kill $POLLER_PID 2>/dev/null
    echo 0 > $TRACE/tracing_on 2>/dev/null
    echo 0 > $DMA_EV 2>/dev/null
    [ -f "$SUB_EV" ] && echo 0 > $SUB_EV 2>/dev/null
    exit
}
trap cleanup EXIT INT TERM

discover_ctxs() {
    pfx="$1"
    echo 0 > $TRACE/tracing_on
    echo > $TRACE/trace
    echo 1 > $DMA_EV
    echo 1 > $TRACE/tracing_on
    sleep 0.5
    echo 0 > $TRACE/tracing_on
    echo 0 > $DMA_EV
    grep "driver=kgsl-timeline" $TRACE/trace 2>/dev/null \
        | grep "(${pfx}" \
        | grep -oE 'kgsl-3d0_[0-9]+' \
        | sed 's/kgsl-3d0_//' \
        | sort -nu | tr '\n' ' '
}

# Output: span events ifps gfps primary_ctx
sample_hybrid() {
    ctxs="$1"
    [ -z "$ctxs" ] && { echo "0 0 0 0 0"; return; }

    echo 0 > $TRACE/tracing_on
    echo 0 > $SUB_EV
    echo > $TRACE/trace
    echo 1 > $SUB_EV
    echo 1 > $TRACE/tracing_on
    t0=$(awk '{print $1}' /proc/uptime)
    sleep "$INTERVAL"
    echo 0 > $TRACE/tracing_on
    echo 0 > $SUB_EV
    t1=$(awk '{print $1}' /proc/uptime)
    span=$(awk -v a="$t0" -v b="$t1" 'BEGIN{printf "%.3f", b-a}')

    # Primary = max events among discovered app ctxs
    best_n=0
    primary=""
    for c in $ctxs; do
        n=$(grep -cE "ctx=${c}[^0-9]" $TRACE/trace 2>/dev/null || echo 0)
        case "$n" in ''|*[!0-9]*) n=0 ;; esac
        if [ "$n" -gt "$best_n" ]; then
            best_n=$n
            primary=$c
        fi
    done
    [ -z "$primary" ] || [ "$primary" = "0" ] && { echo "$span 0 0 0 0"; return; }

    gre="ctx=${primary}[^0-9]"
    awk -v gre="$gre" -v span="$span" -v gapms="$GAP_MS" -v pctx="$primary" '
    $0 ~ gre {
        t=$4; sub(/:$/,"",t); t=t+0
        if(t<=0) next
        inf=""
        for(i=1;i<=NF;i++) if($i ~ /^inflight=/){inf=$i; sub(/inflight=/,"",inf)}
        if(inf=="") next
        n++; ts[n]=t; iv[n]=inf+0
    }
    END {
        if(n<3){ printf "%s 0 0 0 %s\n", span, pctx; exit }
        f=1; prev=iv[1]
        for(i=2;i<=n;i++){ if(iv[i]<prev) f++; prev=iv[i] }
        g=1
        for(i=2;i<=n;i++) if((ts[i]-ts[i-1])*1000 >= gapms) g++
        tspan=ts[n]-ts[1]
        if(tspan<0.2) tspan=span+0
        if(tspan<0.2) tspan=1
        printf "%.3f %d %.1f %.1f %s\n", tspan, n, f/tspan, g/tspan, pctx
    }
    ' $TRACE/trace 2>/dev/null
}

sample_dma_awk() {
    # Legacy path for non-Adreno
    if [ ! -f "$COUNT_AWK" ]; then
        echo 0
        return
    fi
    echo 0 > $TRACE/tracing_on
    echo > $TRACE/trace
    echo 1 > $DMA_EV
    echo 1 > $TRACE/tracing_on
    # Prefer ring buffer over exclusive trace_pipe
    sleep "$INTERVAL"
    echo 0 > $TRACE/tracing_on
    echo 0 > $DMA_EV
    cat $TRACE/trace 2>/dev/null \
        | awk -v gpu=mali -v fgp="$FG_PID" -v pat="$FG_PAT" -v pkg="$FG_PKG" \
              -v interval="$INTERVAL" -f "$COUNT_AWK" 2>/dev/null | tail -n 1
}

LAST_PID=""
CTXS=""
LAST_FPS=0
SMOOTH_FPS=0

while true; do
    if [ -f "$FG_FILE" ]; then
        read -r FG_PID FG_RR FG_PKG FG_PAT < "$FG_FILE" || true
    else
        FG_PID=0
        FG_RR=60
        FG_PKG=unknown
        FG_PAT=unknown
    fi

    if [ -z "$FG_PID" ] || [ "$FG_PID" = "0" ] || [ "$FG_PKG" = "unknown" ]; then
        sleep "$INTERVAL"
        continue
    fi

    RAW=0
    METHOD_OUT=$METHOD

    if [ "$MODE" = "adreno" ]; then
        pfx=$(echo "$FG_PID" | cut -c1-3)
        if [ "$FG_PID" != "$LAST_PID" ] || [ -z "$CTXS" ]; then
            CTXS=$(discover_ctxs "$pfx")
            LAST_PID=$FG_PID
        fi
        nctx=$(echo "$CTXS" | wc -w)
        if [ "$nctx" -gt 0 ]; then
            res=$(sample_hybrid "$CTXS")
            events=$(echo "$res" | awk '{print $2}')
            ifps=$(echo "$res" | awk '{print $3}')
            gfps=$(echo "$res" | awk '{print $4}')
            RAW=$(awk -v i="$ifps" -v g="$gfps" 'BEGIN{
                i=i+0; g=g+0
                if(i<2 && g<2){ print 0; exit }
                if(i>=2 && g>=2) print int((i+g)/2+0.5)
                else if(g>=2) print int(g+0.5)
                else print int(i+0.5)
            }')
            METHOD_OUT=$(awk -v i="$ifps" -v g="$gfps" 'BEGIN{
                i=i+0; g=g+0
                if(i<2 && g<2){ print "idle"; exit }
                if(i>=2 && g>=2) print "cmdbatch-hybrid"
                else if(g>=2) print "cmdbatch-gap"
                else print "cmdbatch-inflight"
            }')
            if [ "${events:-0}" -lt 3 ]; then
                CTXS=$(discover_ctxs "$pfx")
            fi
        fi
    else
        RAW=$(sample_dma_awk)
        METHOD_OUT=dma-fence
    fi

    case "$RAW" in
        ''|*[!0-9]*) RAW=0 ;;
    esac
    if [ "$RAW" -gt 240 ]; then
        RAW=240
    fi

    # Hold last good once if sample empty
    if [ "$RAW" -le 0 ] && [ "$LAST_FPS" -gt 0 ]; then
        RAW=$LAST_FPS
        LAST_FPS=0
    elif [ "$RAW" -gt 0 ]; then
        LAST_FPS=$RAW
    fi

    # Light EMA (α=0.55)
    if [ "$RAW" -gt 0 ]; then
        if [ "$SMOOTH_FPS" -le 0 ]; then
            SMOOTH_FPS=$RAW
        else
            SMOOTH_FPS=$(awk -v s="$SMOOTH_FPS" -v r="$RAW" \
                'BEGIN { printf "%d", int(s*0.45 + r*0.55 + 0.5) }')
        fi
        FPS=$SMOOTH_FPS
    else
        FPS=0
    fi

    if [ "$FPS" -gt 0 ]; then
        FT=$(awk -v f="$FPS" 'BEGIN { if (f>0) printf "%.2f", 1000/f; else print "0" }')
        am broadcast -p com.ivarna.apexcore \
            -a com.ivarna.apexcore.FPS_DATA \
            --es fps "$FPS" \
            --es timeline "$METHOD_OUT" \
            --es frametimes "${FT}," \
            > /dev/null 2>&1
        echo "FPS_DATA fps=$FPS raw=$RAW pid=$FG_PID method=$METHOD_OUT pkg=$FG_PKG" >&2
    fi
done
