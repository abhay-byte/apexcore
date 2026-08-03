# Batch FPS for one sample window (stdin fences, stdout one integer).
# Vars: gpu, fgp, pat, pkg, interval
#
# Adreno (fixed after 60s verify undercount):
#   - Match all kgsl-timeline events for fg app
#   - Per process: fps_med (median frame gap) + fps_cnt (gap-burst / interval)
#   - Keep estimates inside event-rate band [raw/4, raw/1.15]
#   - Median of in-band candidates (rejects multi-fence high + sparse low)
# Mali: events / interval

function adreno_process(line,   pos, rest, under, rest2, paren) {
    pos = index(line, "timeline=")
    if (pos == 0) return ""
    rest = substr(line, pos + 9)
    under = index(rest, "_")
    if (under == 0) return ""
    rest2 = substr(rest, under + 1)
    paren = index(rest2, "(")
    if (paren == 0) return rest2
    return substr(rest2, 1, paren - 1)
}

function paren_pid(line,   pos, rest, i, c, d) {
    pos = index(line, "timeline=")
    if (pos == 0) return ""
    rest = substr(line, pos)
    pos = index(rest, "(")
    if (pos == 0) return ""
    d = ""
    for (i = pos + 1; i <= length(rest); i++) {
        c = substr(rest, i, 1)
        if (c < "0" || c > "9") break
        d = d c
    }
    return d
}

function strip_ctx(p,   d) {
    d = index(p, "-")
    if (d > 0 && d <= 4) return substr(p, d + 1)
    return p
}

function is_noise(p) {
    if (index(p, "apexcore") > 0) return 1
    if (index(p, "systemui") > 0) return 1
    if (index(p, "ndroid.systemui") > 0) return 1
    return 0
}

function matches(line, process,   bare, apid) {
    if (is_noise(process)) return 0
    apid = paren_pid(line)
    if (fgp != "" && fgp != "0" && apid != "") {
        if (apid == fgp) return 1
        if (length(apid) >= 3 && index(fgp, apid) == 1) return 1
    }
    bare = strip_ctx(process)
    if (pat != "" && (index(process, pat) > 0 || index(bare, pat) > 0)) return 1
    if (pkg != "" && pkg != "unknown" && (index(process, pkg) > 0 || index(bare, pkg) > 0)) return 1
    return 0
}

function ts_of(   t) {
    t = $4
    sub(/:$/, "", t)
    return t + 0
}

function sort_num(a, n,   i, j, t) {
    for (i = 2; i <= n; i++) {
        t = a[i]
        j = i - 1
        while (j >= 1 && a[j] > t) {
            a[j + 1] = a[j]
            j--
        }
        a[j + 1] = t
    }
}

function median_num(a, n) {
    if (n < 1) return 0
    sort_num(a, n)
    return a[int((n + 1) / 2)]
}

BEGIN { n = 0 }

/dma_fence_signaled/ {
    if (gpu == "adreno") {
        if (index($0, "driver=kgsl-timeline") == 0) next
        process = adreno_process($0)
        if (process == "" || !matches($0, process)) next
    } else {
        if (index($0, "driver=mali") == 0) next
        pos = index($0, "timeline=0-")
        if (pos == 0) next
        rest = substr($0, pos + 10)
        under = index(rest, "_")
        if (under <= 1) next
        mpid = substr(rest, 1, under - 1)
        if (mpid != fgp) next
        process = mpid
    }
    n++
    ts[n] = ts_of()
    proc[n] = process
}

END {
    if (n < 1) {
        print 0
        exit
    }

    win = interval + 0
    if (win < 0.2) win = 1

    if (gpu != "adreno") {
        print int(n / win + 0.5)
        exit
    }

    for (i = 1; i <= n; i++) {
        p = proc[i]
        c = ++pcnt[p]
        pts[p, c] = ts[i]
    }

    raw = n / win
    lo = raw / 4.0
    hi = raw / 1.15
    if (lo < 5) lo = 5
    if (hi > 240) hi = 240
    if (hi < lo + 1) hi = lo + 30

    ncand = 0
    best_fb = 0
    best_fb_err = 1e9
    target = raw / 2.2
    if (target < 5) target = 30

    for (p in pcnt) {
        m = pcnt[p]
        if (m < 4) continue

        for (i = 1; i <= m; i++) arr[i] = pts[p, i]
        sort_num(arr, m)

        ng = 0
        for (i = 2; i <= m; i++) {
            dms = (arr[i] - arr[i - 1]) * 1000
            if (dms > 0 && dms < 200) {
                ng++
                gaps[ng] = dms
            }
        }
        if (ng < 3) {
            for (i = 1; i <= m; i++) delete arr[i]
            continue
        }

        for (i = 1; i <= ng; i++) tmp[i] = gaps[i]
        med_all = median_num(tmp, ng)

        thr = med_all * 0.55
        if (thr < 6) thr = 6
        if (thr > 18) thr = 18

        nf = 0
        frames = 1
        for (i = 1; i <= ng; i++) {
            if (gaps[i] >= thr) {
                frames++
                if (gaps[i] <= 120) {
                    nf++
                    fgaps[nf] = gaps[i]
                }
            }
        }

        fps_cnt = frames / win
        fps_med = 0
        if (nf >= 3) {
            for (i = 1; i <= nf; i++) tmp[i] = fgaps[i]
            med = median_num(tmp, nf)
            if (med >= 4) fps_med = 1000.0 / med
        }

        if (fps_med > 0 && fps_cnt > 0) {
            if (fps_med > fps_cnt * 1.4) fps = fps_cnt
            else if (fps_cnt > fps_med * 1.4) fps = fps_med
            else fps = (fps_med + fps_cnt) / 2
        } else if (fps_med > 0) fps = fps_med
        else fps = fps_cnt

        if (fps >= lo && fps <= hi) {
            ncand++
            cand[ncand] = fps
        } else {
            err = fps - target
            if (err < 0) err = -err
            if (err < best_fb_err) {
                best_fb_err = err
                best_fb = fps
            }
        }

        for (i = 1; i <= m; i++) delete arr[i]
        for (i = 1; i <= ng; i++) { delete gaps[i]; delete tmp[i] }
        for (i = 1; i <= nf; i++) delete fgaps[i]
    }

    if (ncand >= 1) {
        for (i = 1; i <= ncand; i++) arr[i] = cand[i]
        sort_num(arr, ncand)
        best = arr[int((ncand + 1) / 2)]
    } else if (best_fb > 0) {
        best = best_fb
    } else {
        # last resort: raw / 2.2 (typical multi-fence factor)
        best = raw / 2.2
    }

    if (best < 0) best = 0
    if (best > 240) best = 240
    print int(best + 0.5)
}
