package ru.garickbox.gor4akovnet

object BackupRestoreScripts {

    /**
     * Общий shell-блок диагностики, самотестирования и автодоустановки GNU tar + gzip.
     * Выставляет переменные:
     *   TAR            - путь к лучшему рабочему бинарю tar
     *   _tar_is_gnu    - 1 если GNU tar, 0 если BusyBox/иной
     *   _tar_can_create- 1 если реально создаёт .tar.gz
     *   _tar_can_extract-1 если реально распаковывает .tar.gz
     *   _tar_can_x     - 1 если реально поддерживает -X FILE
     *   _tar_can_strip - 1 если поддерживает --strip-components=1
     *   _gzip_ok       - 1 если gzip -c/-t/-dc работает в PATH
     *   _tar_diag      - строка отчёта о проверенных бинарях
     */
    val SH_ARCHIVER_HELPERS: String = """
        export PATH="/opt/bin:/opt/sbin:/opt/usr/bin:/opt/usr/sbin:/bin:/sbin:/usr/bin:/usr/sbin"
        if [ -d /opt/tmp ] && [ -w /opt/tmp ]; then
            export TMPDIR=/opt/tmp
        else
            export TMPDIR=/tmp
        fi

        _gbox_test_gzip() {
            _gz_bin=""
            for _g in /opt/bin/gzip /bin/gzip /usr/bin/gzip "${'$'}(command -v gzip 2>/dev/null || true)"; do
                [ -n "${'$'}_g" ] && [ -x "${'$'}_g" ] || continue
                _gz_p="${'$'}TMPDIR/.gbox_gz_${'$'}${'$'}.gz"
                if printf 'gbox_crc_ok\n' | "${'$'}_g" -c > "${'$'}_gz_p" 2>/dev/null \
                   && "${'$'}_g" -t "${'$'}_gz_p" >/dev/null 2>&1 \
                   && [ "${'$'}("${'$'}_g" -dc "${'$'}_gz_p" 2>/dev/null)" = "gbox_crc_ok" ]; then
                    _gz_bin="${'$'}_g"
                    rm -f "${'$'}_gz_p"
                    return 0
                fi
                rm -f "${'$'}_gz_p"
            done
            return 1
        }

        _gbox_probe_tar_one() {
            _cand="${'$'}1"
            [ -n "${'$'}_cand" ] && [ -x "${'$'}_cand" ] || return 1
            _pd="${'$'}TMPDIR/.gbox_tp_${'$'}${'$'}"
            rm -rf "${'$'}_pd"
            mkdir -p "${'$'}_pd/src/sub" "${'$'}_pd/out" 2>/dev/null || return 1
            printf 'ok123\n' > "${'$'}_pd/src/keep.txt"
            printf 'oksub\n' > "${'$'}_pd/src/sub/nested.txt"
            ln -s keep.txt "${'$'}_pd/src/link_keep" 2>/dev/null || true
            printf 'skip\n' > "${'$'}_pd/src/skip.log"
            printf '*.log\n' > "${'$'}_pd/excl"

            _c_gnu=0
            _c_create=0
            _c_x=0
            _c_list=0
            _c_extract=0
            _c_strip=0

            if "${'$'}_cand" --version 2>/dev/null | grep -qi "GNU tar"; then
                _c_gnu=1
            fi

            if (cd "${'$'}_pd/src" && "${'$'}_cand" -czf "${'$'}_pd/a.tar.gz" -X "${'$'}_pd/excl" . >/dev/null 2>&1) \
               && [ -s "${'$'}_pd/a.tar.gz" ]; then
                _c_create=1
                _c_x=1
            else
                rm -f "${'$'}_pd/a.tar.gz"
                if (cd "${'$'}_pd/src" && "${'$'}_cand" -czf "${'$'}_pd/a.tar.gz" . >/dev/null 2>&1) \
                   && [ -s "${'$'}_pd/a.tar.gz" ]; then
                    _c_create=1
                    _c_x=0
                fi
            fi

            if [ "${'$'}_c_create" = "1" ]; then
                if "${'$'}_cand" -tzf "${'$'}_pd/a.tar.gz" > "${'$'}_pd/lst.txt" 2>/dev/null; then
                    _c_list=1
                    if [ "${'$'}_c_x" = "1" ] && grep -q 'skip\.log' "${'$'}_pd/lst.txt" 2>/dev/null; then
                        _c_x=0
                    fi
                fi
                if (cd "${'$'}_pd/out" && "${'$'}_cand" -xzf "${'$'}_pd/a.tar.gz" >/dev/null 2>&1) \
                   && [ "${'$'}(cat "${'$'}_pd/out/keep.txt" 2>/dev/null)" = "ok123" ] \
                   && [ "${'$'}(cat "${'$'}_pd/out/sub/nested.txt" 2>/dev/null)" = "oksub" ] \
                   && { [ ! -L "${'$'}_pd/src/link_keep" ] || [ -L "${'$'}_pd/out/link_keep" ]; }; then
                    _c_extract=1
                fi
                rm -rf "${'$'}_pd/out" && mkdir -p "${'$'}_pd/out"
                if (cd "${'$'}_pd/out" && "${'$'}_cand" -xzf "${'$'}_pd/a.tar.gz" --strip-components=1 >/dev/null 2>&1) \
                   && [ -f "${'$'}_pd/out/keep.txt" ]; then
                    _c_strip=1
                fi
            else
                if "${'$'}_cand" --help 2>&1 | grep -qE -- '(^|[[:space:]])-x|Extract'; then
                    _c_extract=1
                fi
                if "${'$'}_cand" --help 2>&1 | grep -qE -- '(^|[[:space:]])-t|List'; then
                    _c_list=1
                fi
            fi

            rm -rf "${'$'}_pd"

            _score=0
            if [ "${'$'}_c_gnu" = "1" ] && [ "${'$'}_c_create" = "1" ] && [ "${'$'}_c_extract" = "1" ] && [ "${'$'}_c_x" = "1" ]; then
                _score=100
            elif [ "${'$'}_c_create" = "1" ] && [ "${'$'}_c_extract" = "1" ] && [ "${'$'}_c_x" = "1" ]; then
                _score=70
            elif [ "${'$'}_c_create" = "1" ] && [ "${'$'}_c_extract" = "1" ]; then
                _score=50
            elif [ "${'$'}_c_create" = "1" ]; then
                _score=20
            elif [ "${'$'}_c_extract" = "1" ]; then
                _score=10
            fi

            _tar_diag="${'$'}{_tar_diag}${'$'}{_tar_diag:+; }${'$'}_cand(gnu=${'$'}_c_gnu,c=${'$'}_c_create,x=${'$'}_c_x,e=${'$'}_c_extract,l=${'$'}_c_list,s=${'$'}_c_strip)"
            if [ "${'$'}_score" -gt "${'$'}_best_score" ]; then
                _best_score="${'$'}_score"
                TAR="${'$'}_cand"
                _tar_is_gnu="${'$'}_c_gnu"
                _tar_can_create="${'$'}_c_create"
                _tar_can_x="${'$'}_c_x"
                _tar_can_extract="${'$'}_c_extract"
                _tar_can_list="${'$'}_c_list"
                _tar_can_strip="${'$'}_c_strip"
            fi
            return 0
        }

        _gbox_scan_archivers() {
            TAR=""
            _best_score=0
            _tar_is_gnu=0
            _tar_can_create=0
            _tar_can_x=0
            _tar_can_extract=0
            _tar_can_list=0
            _tar_can_strip=0
            _tar_diag=""
            _gzip_ok=0
            _gbox_test_gzip && _gzip_ok=1

            _seen_tars="|"
            _cmd_tar="${'$'}(command -v tar 2>/dev/null || true)"
            for _t in /opt/bin/tar /opt/usr/bin/tar /usr/bin/tar /bin/tar "${'$'}_cmd_tar"; do
                [ -n "${'$'}_t" ] && [ -x "${'$'}_t" ] || continue
                case "${'$'}_seen_tars" in *"|${'$'}_t|"*) continue ;; esac
                _seen_tars="${'$'}_seen_tars${'$'}_t|"
                _gbox_probe_tar_one "${'$'}_t"
            done
            if [ "${'$'}_best_score" -eq 0 ] && [ -x /bin/busybox ] && /bin/busybox tar --help >/dev/null 2>&1; then
                _bb_shim="${'$'}TMPDIR/.gbox_bb_tar_${'$'}${'$'}"
                printf '#!/bin/sh\nexec /bin/busybox tar "${'$'}@"\n' > "${'$'}_bb_shim" 2>/dev/null && chmod +x "${'$'}_bb_shim" 2>/dev/null
                [ -x "${'$'}_bb_shim" ] && _gbox_probe_tar_one "${'$'}_bb_shim"
            fi
        }

        ensure_archiver() {
            _need_create="${'$'}{1:-1}"
            _gbox_scan_archivers
            if [ "${'$'}_tar_is_gnu" -ne 1 ] || [ "${'$'}_gzip_ok" -ne 1 ] || [ "${'$'}_best_score" -lt 70 ]; then
                if [ -x /opt/bin/opkg ] && [ -s /opt/etc/opkg.conf ]; then
                    echo "[INFO] Доустановка GNU tar и gzip через opkg (текущий: ${'$'}{_tar_diag:-none}, gzip=${'$'}_gzip_ok)..." >&2
                    if [ -s /opt/etc/opkg.conf.local ] && ls /opt/gor4akov-offline/ipks/*.ipk >/dev/null 2>&1; then
                        /opt/bin/opkg -f /opt/etc/opkg.conf.local update >/dev/null 2>&1 || true
                        /opt/bin/opkg -f /opt/etc/opkg.conf.local install tar gzip >/dev/null 2>&1 || true
                    fi
                    if [ ! -x /opt/bin/tar ] || ! /opt/bin/tar --version 2>/dev/null | grep -qi "GNU tar" || [ ! -x /opt/bin/gzip ]; then
                        if ! ls /opt/var/opkg-lists/* >/dev/null 2>&1; then
                            /opt/bin/opkg update >/dev/null 2>&1 || true
                        fi
                        /opt/bin/opkg install tar gzip >/dev/null 2>&1 || true
                        if [ ! -x /opt/bin/tar ] || ! /opt/bin/tar --version 2>/dev/null | grep -qi "GNU tar"; then
                            /opt/bin/opkg update >/dev/null 2>&1 || true
                            /opt/bin/opkg install --force-reinstall tar gzip >/dev/null 2>&1 || true
                        fi
                    fi
                    _gbox_scan_archivers
                fi
            fi

            if [ -z "${'$'}TAR" ] || [ "${'$'}_best_score" -eq 0 ]; then
                echo "FATAL: на роутере не найден рабочий tar (${'$'}{_tar_diag:-none}). Выполните: opkg update && opkg install tar gzip" >&2
                exit 1
            fi
            if [ "${'$'}_need_create" = "1" ] && [ "${'$'}_tar_can_create" -ne 1 ]; then
                echo "FATAL: tar (${'$'}TAR) умеет только читать, но НЕ умеет создавать архивы (${'$'}_tar_diag). Выполните: opkg update && opkg install tar gzip" >&2
                exit 1
            fi
            if [ "${'$'}_need_create" = "0" ] && [ "${'$'}_tar_can_extract" -ne 1 ]; then
                echo "FATAL: tar (${'$'}TAR) НЕ умеет распаковывать архивы (${'$'}_tar_diag). Выполните: opkg update && opkg install tar gzip" >&2
                exit 1
            fi
        }
    """.trimIndent()

    /**
     * Обнаружение всех реальных (не-tmpfs/не-squashfs) накопителей и точного пути привязки /opt.
     */
    private val SH_MOUNT_DISCOVERY: String = """
        _gbox_opt_bind_sub() {
            _sub=""
            if [ -r /proc/self/mountinfo ]; then
                _sub=${'$'}(awk '${'$'}5=="/opt"{print ${'$'}4; exit}' /proc/self/mountinfo 2>/dev/null)
            fi
            [ -z "${'$'}_sub" ] && _sub="/"
            printf '%s' "${'$'}_sub"
        }

        _gbox_opt_fs_info() {
            _opt_dev=""
            _opt_fs=""
            if [ -r /proc/mounts ]; then
                _line=${'$'}(awk '${'$'}2=="/opt"{d=${'$'}1; t=${'$'}3} END{if(d!="") print d"|"t}' /proc/mounts 2>/dev/null)
                _opt_dev=${'$'}(printf '%s' "${'$'}_line" | cut -d'|' -f1)
                _opt_fs=${'$'}(printf '%s' "${'$'}_line" | cut -d'|' -f2)
            fi
            if [ -z "${'$'}_opt_fs" ]; then
                _opt_fs=${'$'}(df -PT /opt 2>/dev/null | awk 'NR==2{print ${'$'}2}')
                _opt_dev=${'$'}(df -Pm /opt 2>/dev/null | awk 'NR==2{print ${'$'}1}')
            fi
            _opt_mounted=1
            case "${'$'}_opt_fs" in
                tmpfs|ramfs|squashfs|"") _opt_mounted=0 ;;
            esac
            [ -z "${'$'}_opt_dev" ] && _opt_mounted=0
        }

        _gbox_check_opkg_health() {
            _opkg_status="ok"
            if [ ! -e /opt/bin/opkg ]; then
                _opkg_status="missing_bin"
            elif [ ! -x /opt/bin/opkg ]; then
                _opkg_status="not_exec"
            elif ! /opt/bin/opkg --version >/dev/null 2>&1; then
                _opkg_status="broken_exec"
            elif [ ! -s /opt/etc/opkg.conf ]; then
                _opkg_status="missing_conf"
            fi
        }

        _gbox_collect_mounts() {
            _out_list="${'$'}1"
            : > "${'$'}_out_list"
            if [ -r /proc/mounts ]; then
                awk '{
                    gsub(/\\040/," ",${'$'}2)
                    if (${'$'}2 ~ /^\/(tmp\/mnt|media|mnt|storage)(\/|$)/ && ${'$'}2 != "/tmp/mnt" && ${'$'}2 != "/media" && ${'$'}2 != "/mnt") {
                        if (${'$'}3 !~ /^(tmpfs|devtmpfs|proc|sysfs|devpts|debugfs|cgroup|pstore|squashfs|overlay|ramfs)$/) print ${'$'}2
                    }
                }' /proc/mounts 2>/dev/null | awk '!seen[${'$'}0]++' > "${'$'}_out_list"
            fi
            if [ ! -s "${'$'}_out_list" ]; then
                for _m in /tmp/mnt/* /media/* /mnt/* /storage /storage/*; do
                    [ -d "${'$'}_m" ] || continue
                    _mfs=${'$'}(df -PT "${'$'}_m" 2>/dev/null | awk 'NR==2{print ${'$'}2}')
                    case "${'$'}_mfs" in tmpfs|devtmpfs|squashfs|ramfs|"") continue ;; esac
                    printf '%s\n' "${'$'}_m" >> "${'$'}_out_list"
                done
            fi
        }
    """.trimIndent()

    val OPT_PREFLIGHT_CMD: String = """
        set +e
        export PATH="/opt/bin:/opt/sbin:/opt/usr/bin:/opt/usr/sbin:/bin:/sbin:/usr/bin:/usr/sbin"
$SH_MOUNT_DISCOVERY
        _gbox_opt_fs_info
        _gbox_check_opkg_health
        _free_kb=${'$'}(df -Pk /opt 2>/dev/null | awk 'NR==2{print ${'$'}4+0}')
        echo "PREFLIGHT|${'$'}_opt_mounted|${'$'}{_opt_fs:-none}|${'$'}{_free_kb:-0}|${'$'}_opkg_status|${'$'}{_opt_dev:-none}"
    """.trimIndent()

    val ESTIMATE_BACKUP_SIZE_CMD: String = """
        set +e
        export PATH="/opt/bin:/opt/sbin:/opt/usr/bin:/opt/usr/sbin:/bin:/sbin:/usr/bin:/usr/sbin"
$SH_MOUNT_DISCOVERY
        _gbox_opt_fs_info
        _gbox_check_opkg_health
        core_kb=${'$'}(du -sk /opt 2>/dev/null | awk '{print ${'$'}1+0}')
        for _ex in /opt/gor4akov-offline /opt/z2k-work /opt/z2k-rollback /opt/tmp /opt/var/log /opt/var/run; do
            [ -e "${'$'}_ex" ] && core_kb=${'$'}((core_kb - ${'$'}(du -sk "${'$'}_ex" 2>/dev/null | awk '{print ${'$'}1+0}')))
        done
        [ "${'$'}core_kb" -lt 0 ] && core_kb=0
        opt_root_files=${'$'}(find /opt -maxdepth 1 \( -type f -o -type l \) 2>/dev/null | wc -l | tr -dc '0-9')
        media_kb=0
        mounts=""
        opt_dev=${'$'}(awk '${'$'}2=="/opt"{print ${'$'}1; exit}' /proc/mounts 2>/dev/null)
        opt_ino=${'$'}(ls -id /opt 2>/dev/null | awk '{print ${'$'}1}')
        opt_bind=${'$'}(_gbox_opt_bind_sub)
        MNT_LIST="/tmp/.gbox_mnts_${'$'}${'$'}"
        _gbox_collect_mounts "${'$'}MNT_LIST"
        while IFS= read -r m; do
            [ -n "${'$'}m" ] && [ -d "${'$'}m" ] || continue
            df_info=${'$'}(df -Pm "${'$'}m" 2>/dev/null | awk 'NF==1{d=${'$'}0; getline; ${'$'}0=d" "${'$'}0} NR==2{print ${'$'}1"|"(${'$'}2+0)"|"(${'$'}3+0)"|"(${'$'}4+0)}')
            m_dev=${'$'}(printf '%s' "${'$'}df_info" | cut -d'|' -f1)
            m_tot=${'$'}(printf '%s' "${'$'}df_info" | cut -d'|' -f2)
            m_used=${'$'}(printf '%s' "${'$'}df_info" | cut -d'|' -f3)
            m_free=${'$'}(printf '%s' "${'$'}df_info" | cut -d'|' -f4)
            ms=${'$'}(du -sk "${'$'}m" 2>/dev/null | awk '{print ${'$'}1+0}')
            m_rfiles=${'$'}(find "${'$'}m" -maxdepth 1 \( -type f -o -type l \) 2>/dev/null | wc -l | tr -dc '0-9')
            m_allfiles=${'$'}(find "${'$'}m" -mindepth 1 ! -name 'lost+found' 2>/dev/null | head -n 5 | wc -l | tr -dc '0-9')
            opt_tag=""
            if [ -n "${'$'}opt_dev" ] && [ "${'$'}m_dev" = "${'$'}opt_dev" ]; then
                m_ino=${'$'}(ls -id "${'$'}m" 2>/dev/null | awk '{print ${'$'}1}')
                if [ -n "${'$'}opt_ino" ] && [ "${'$'}m_ino" = "${'$'}opt_ino" ]; then
                    opt_tag=", корень USB = /opt (${'$'}{opt_root_files:-0} файлов в корне)"
                    ms=0
                else
                    _cand_sub=""
                    if [ "${'$'}opt_bind" != "/" ] && [ -d "${'$'}m${'$'}opt_bind" ]; then
                        _cand_sub="${'$'}m${'$'}opt_bind"
                    else
                        for sub in "${'$'}m"/* "${'$'}m"/*/*; do
                            [ -d "${'$'}sub" ] || continue
                            sub_ino=${'$'}(ls -id "${'$'}sub" 2>/dev/null | awk '{print ${'$'}1}')
                            if [ -n "${'$'}opt_ino" ] && [ "${'$'}sub_ino" = "${'$'}opt_ino" ]; then
                                _cand_sub="${'$'}sub"
                                break
                            fi
                        done
                    fi
                    if [ -n "${'$'}_cand_sub" ]; then
                        opt_sub_kb=${'$'}(du -sk "${'$'}_cand_sub" 2>/dev/null | awk '{print ${'$'}1+0}')
                        ms=${'$'}((ms - opt_sub_kb))
                        [ "${'$'}ms" -lt 0 ] && ms=0
                        opt_tag=", Entware в ${'$'}{_cand_sub#${'$'}m}"
                    fi
                fi
            elif [ "${'$'}{m_allfiles:-0}" -eq 0 ]; then
                case "${'$'}m" in
                    /storage|/storage/*) opt_tag=", пусто — встроенная флеш-память роутера" ;;
                    *) opt_tag=", пусто (0 файлов)" ;;
                esac
                ms=0
            fi
            [ -z "${'$'}opt_tag" ] && [ "${'$'}{m_rfiles:-0}" -gt 0 ] && opt_tag=", файлов в корне: ${'$'}m_rfiles"
            media_kb=${'$'}((media_kb + ms))
            mounts="${'$'}{mounts}${'$'}{mounts:+; }${'$'}m (~${'$'}((ms/1024))MB доп., занято ${'$'}{m_used:-0}/${'$'}{m_tot:-0}MB, своб. ${'$'}{m_free:-0}MB${'$'}opt_tag)"
        done < "${'$'}MNT_LIST"
        rm -f "${'$'}MNT_LIST"
        echo "EST|${'$'}((core_kb/1024))|${'$'}((media_kb/1024))|${'$'}{mounts:-none}|${'$'}_opkg_status|${'$'}_opt_mounted"
    """.trimIndent()

    fun buildFullBackupCmd(includeUserMedia: Boolean): String = """__BACKUP__
        set +e
$SH_ARCHIVER_HELPERS
$SH_MOUNT_DISCOVERY

        _gbox_opt_fs_info
        _gbox_check_opkg_health
        if [ "${'$'}_opt_mounted" -ne 1 ] || [ ! -d /opt/etc ]; then
            echo "FATAL: /opt не смонтирован на USB/хранилище (fs=${'$'}{_opt_fs:-none}) или нет /opt/etc — полный бэкап невозможен." >&2
            exit 1
        fi
        if [ "${'$'}_opkg_status" != "ok" ]; then
            echo "[WARN] Проверка opkg: ${'$'}_opkg_status (tar/бинарники всё равно будут сохранены, если архиватор доступен)" >&2
        fi
        ensure_archiver 1

        rm -rf /opt/.gbox_backup_stage.* /opt/.gbox_meta /opt/tmp/.gbox_bkp_* 2>/dev/null || true
        TMPB="/opt/tmp/.gbox_bkp_${'$'}${'$'}"
        META_OPT="/opt/.gbox_meta"
        STAGE="/opt/.gbox_backup_stage.${'$'}${'$'}.tar.gz"
        BB_SHIM_CLEAN="${'$'}TMPDIR/.gbox_bb_tar_${'$'}${'$'}"
        trap 'rm -rf "${'$'}TMPB" "${'$'}META_OPT" "${'$'}STAGE" "${'$'}BB_SHIM_CLEAN"' EXIT INT TERM
        mkdir -p "${'$'}TMPB" "${'$'}META_OPT" /opt/tmp

        # Проверяем, достаточно ли свободного места на /opt (минимум 8 МБ, и предупреждаем если тесно)
        _opt_free_kb=${'$'}(df -Pk /opt 2>/dev/null | awk 'NR==2{print ${'$'}4+0}')
        if [ -n "${'$'}_opt_free_kb" ] && [ "${'$'}_opt_free_kb" -lt 8192 ]; then
            echo "FATAL: критически мало места на /opt (${'$'}_opt_free_kb КБ) для временного архива." >&2
            exit 1
        fi

        LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" > "${'$'}TMPB/running-config.txt" 2>/dev/null || true
        LD_LIBRARY_PATH= /bin/ndmc -c "show startup-config" > "${'$'}TMPB/startup-config.txt" 2>/dev/null || cp -f "${'$'}TMPB/running-config.txt" "${'$'}TMPB/startup-config.txt" 2>/dev/null || true
        {
            echo "=== DATE ===" && date
            echo "=== UNAME ===" && uname -a
            echo "=== VERSION ===" && LD_LIBRARY_PATH= /bin/ndmc -c "show version" 2>/dev/null
            echo "=== MEDIA ===" && LD_LIBRARY_PATH= /bin/ndmc -c "show media" 2>/dev/null
            echo "=== OPKG_CFG ===" && LD_LIBRARY_PATH= /bin/ndmc -c "show opkg" 2>/dev/null
            echo "=== DF ===" && df -h 2>/dev/null
            echo "=== MOUNTS ===" && cat /proc/mounts 2>/dev/null
            echo "=== MOUNTINFO ===" && cat /proc/self/mountinfo 2>/dev/null
            echo "=== OPKG_LIST ===" && /opt/bin/opkg list-installed 2>/dev/null
        } > "${'$'}TMPB/router-info.txt" 2>/dev/null || true

        opt_dev=${'$'}(awk '${'$'}2=="/opt"{print ${'$'}1; exit}' /proc/mounts 2>/dev/null)
        opt_ino=${'$'}(ls -id /opt 2>/dev/null | awk '{print ${'$'}1}')
        opt_bind=${'$'}(_gbox_opt_bind_sub)
        opt_uuid=""
        if [ -n "${'$'}opt_dev" ] && command -v blkid >/dev/null 2>&1; then
            opt_uuid=${'$'}(blkid "${'$'}opt_dev" 2>/dev/null | sed -n 's/.*UUID="\([^"]*\)".*/\1/p' | head -n1)
        fi

        MNT_LIST="${'$'}TMPB/.mnts"
        INCL_MNTS="${'$'}TMPB/.incl_mnts"
        EXCL_LIST="${'$'}TMPB/.opt_excls"
        MOUNTS_TSV="${'$'}TMPB/.gbox_mounts.tsv"
        : > "${'$'}MNT_LIST"
        : > "${'$'}INCL_MNTS"
        : > "${'$'}EXCL_LIST"
        : > "${'$'}MOUNTS_TSV"

        if [ "${if (includeUserMedia) 1 else 0}" = "1" ]; then
            _gbox_collect_mounts "${'$'}MNT_LIST"
            while IFS= read -r m; do
                [ -n "${'$'}m" ] && [ -d "${'$'}m" ] || continue
                m_dev=${'$'}(df -Pm "${'$'}m" 2>/dev/null | awk 'NF==1{d=${'$'}0; getline; ${'$'}0=d" "${'$'}0} NR==2{print ${'$'}1}')
                m_fs=${'$'}(df -PT "${'$'}m" 2>/dev/null | awk 'NF==1{d=${'$'}0; getline; ${'$'}0=d" "${'$'}0} NR==2{print ${'$'}2}')
                m_uuid=""
                m_lbl=""
                if [ -n "${'$'}m_dev" ] && command -v blkid >/dev/null 2>&1; then
                    m_blk=${'$'}(blkid "${'$'}m_dev" 2>/dev/null | head -n1)
                    m_uuid=${'$'}(printf '%s' "${'$'}m_blk" | sed -n 's/.*UUID="\([^"]*\)".*/\1/p')
                    m_lbl=${'$'}(printf '%s' "${'$'}m_blk" | sed -n 's/.*LABEL="\([^"]*\)".*/\1/p')
                fi
                skip_m=0
                sub_rel=""
                if [ -n "${'$'}opt_dev" ] && [ "${'$'}m_dev" = "${'$'}opt_dev" ]; then
                    m_ino=${'$'}(ls -id "${'$'}m" 2>/dev/null | awk '{print ${'$'}1}')
                    if [ -n "${'$'}opt_ino" ] && [ "${'$'}m_ino" = "${'$'}opt_ino" ]; then
                        # Корень этого раздела и есть /opt -> всё уже попадёт через opt/ (включая файлы в корне!)
                        skip_m=1
                    else
                        _found_sub=""
                        if [ "${'$'}opt_bind" != "/" ] && [ -d "${'$'}m${'$'}opt_bind" ]; then
                            _found_sub="${'$'}m${'$'}opt_bind"
                        else
                            for sub in "${'$'}m"/* "${'$'}m"/*/*; do
                                [ -d "${'$'}sub" ] || continue
                                sub_ino=${'$'}(ls -id "${'$'}sub" 2>/dev/null | awk '{print ${'$'}1}')
                                if [ -n "${'$'}opt_ino" ] && [ "${'$'}sub_ino" = "${'$'}opt_ino" ]; then
                                    _found_sub="${'$'}sub"
                                    break
                                fi
                            done
                        fi
                        if [ -n "${'$'}_found_sub" ]; then
                            sub_rel="${'$'}{_found_sub#${'$'}m/}"
                            printf '%s\n' "${'$'}{_found_sub#/}" >> "${'$'}EXCL_LIST"
                            printf '%s\n' "${'$'}{_found_sub#/}/*" >> "${'$'}EXCL_LIST"
                        fi
                    fi
                fi
                if [ "${'$'}skip_m" = "0" ]; then
                    _has_usr=${'$'}(find "${'$'}m" -mindepth 1 ! -name 'lost+found' 2>/dev/null | head -n 1 | wc -l | tr -dc '0-9')
                    if [ "${'$'}{_has_usr:-0}" -eq 0 ] && [ "${'$'}m" = "/storage" ]; then
                        echo "[INFO] /storage (встроенная память роутера) пуст — пропущен из архива" >&2
                    else
                        printf '%s\n' "${'$'}m" >> "${'$'}INCL_MNTS"
                        printf '%s\t%s\t%s\t%s\t%s\t%s\n' "${'$'}{m#/}" "${'$'}m" "${'$'}m_uuid" "${'$'}m_lbl" "${'$'}m_fs" "${'$'}sub_rel" >> "${'$'}MOUNTS_TSV"
                    fi
                fi
            done < "${'$'}MNT_LIST"
        fi

        _media_cnt=${'$'}(awk 'NF{n++} END{print n+0}' "${'$'}INCL_MNTS" 2>/dev/null)
        _media_list=${'$'}(tr '\n' ',' < "${'$'}INCL_MNTS" 2>/dev/null | sed 's/,$//')
        _tar_flavor="busybox"
        [ "${'$'}_tar_is_gnu" = "1" ] && _tar_flavor="gnu"
        printf '{"version":2,"arch":"%s","tar_flavor":"%s","include_media":%s,"media_mnts":%d,"media_list":"%s","opt_bind_sub":"%s","opt_uuid":"%s"}\n' \
            "${'$'}(uname -m)" "${'$'}_tar_flavor" "${if (includeUserMedia) "true" else "false"}" "${'$'}{_media_cnt:-0}" "${'$'}{_media_list:-}" "${'$'}opt_bind" "${'$'}opt_uuid" > "${'$'}TMPB/manifest.json"
        echo "[INFO] media_mnts=${'$'}{_media_cnt:-0} TAR=${'$'}TAR(${'$'}_tar_flavor,x=${'$'}_tar_can_x,gzip=${'$'}_gzip_ok)" >&2

        EXCLF="${'$'}TMPB/.excludes"
        {
          printf '%s\n' 'opt/.gbox_backup_stage*'
          printf '%s\n' 'opt/.gbox_prerestore.*'
          printf '%s\n' 'opt/.gbox_rst_*'
          printf '%s\n' 'opt/.gbox_old_*'
          printf '%s\n' 'opt/gor4akov-offline' 'opt/gor4akov-offline/*'
          printf '%s\n' 'opt/z2k-work' 'opt/z2k-work/*'
          printf '%s\n' 'opt/z2k-rollback' 'opt/z2k-rollback/*'
          printf '%s\n' 'opt/tmp' 'opt/tmp/*'
          printf '%s\n' 'opt/var/log' 'opt/var/log/*'
          printf '%s\n' 'opt/var/run' 'opt/var/run/*'
          printf '%s\n' '*.sock' '*.pid' '*.log' '*.swap' 'swapfile' '*.db-wal' '*.db-shm'
          cat "${'$'}EXCL_LIST" 2>/dev/null || true
        } > "${'$'}EXCLF"

        rm -f "${'$'}STAGE"
        tar_rc=1

        if [ "${'$'}_tar_is_gnu" = "1" ]; then
            # GNU tar корректно понимает несколько -C и --warning/--ignore-failed-read
            rm -rf "${'$'}META_OPT"
            set -- -C "${'$'}TMPB" running-config.txt startup-config.txt router-info.txt manifest.json .gbox_mounts.tsv
            [ -d /opt ] && set -- "${'$'}@" -C / opt
            while IFS= read -r m; do
                [ -n "${'$'}m" ] && [ -d "${'$'}m" ] || continue
                set -- "${'$'}@" -C / "${'$'}{m#/}"
            done < "${'$'}INCL_MNTS"
            "${'$'}TAR" --warning=no-file-changed --ignore-failed-read -X "${'$'}EXCLF" -czf "${'$'}STAGE" "${'$'}@"
            tar_rc=${'$'}?
        else
            # BusyBox tar НЕ поддерживает несколько -C в одной команде!
            # Кладём 5 служебных файлов в /opt/.gbox_meta и архивируем от единого корня `/` без единого -C.
            cp -f "${'$'}TMPB/running-config.txt" "${'$'}TMPB/startup-config.txt" "${'$'}TMPB/router-info.txt" "${'$'}TMPB/manifest.json" "${'$'}TMPB/.gbox_mounts.tsv" "${'$'}META_OPT/" 2>/dev/null || true
            set --
            if [ "${'$'}_tar_can_x" = "1" ]; then
                [ -d /opt ] && set -- opt
                while IFS= read -r m; do
                    [ -n "${'$'}m" ] && [ -d "${'$'}m" ] || continue
                    set -- "${'$'}@" "${'$'}{m#/}"
                done < "${'$'}INCL_MNTS"
                (cd / && "${'$'}TAR" -czf "${'$'}STAGE" -X "${'$'}EXCLF" "${'$'}@")
                tar_rc=${'$'}?
            else
                # Ультра-урезанный BusyBox без -X: перечисляем безопасные элементы /opt вручную
                for _item in /opt/* /opt/.[!.]* /opt/..?*; do
                    [ -e "${'$'}_item" ] || [ -L "${'$'}_item" ] || continue
                    _bn="${'$'}{_item##*/}"
                    case "${'$'}_bn" in
                        .gbox_backup_stage*|.gbox_prerestore.*|.gbox_rst_*|.gbox_old_*|gor4akov-offline|z2k-work|z2k-rollback|tmp|swapfile|*.swap) continue ;;
                    esac
                    set -- "${'$'}@" "opt/${'$'}_bn"
                done
                while IFS= read -r m; do
                    [ -n "${'$'}m" ] && [ -d "${'$'}m" ] || continue
                    set -- "${'$'}@" "${'$'}{m#/}"
                done < "${'$'}INCL_MNTS"
                (cd / && "${'$'}TAR" -czf "${'$'}STAGE" "${'$'}@")
                tar_rc=${'$'}?
            fi
            rm -rf "${'$'}META_OPT"
        fi

        rm -rf "${'$'}TMPB"

        _arch_ok=1
        if [ "${'$'}_tar_is_gnu" = "1" ] && [ "${'$'}tar_rc" -ge 2 ]; then
            _arch_ok=0
        elif [ "${'$'}_tar_is_gnu" != "1" ] && [ "${'$'}tar_rc" -gt 1 ]; then
            _arch_ok=0
        fi
        [ ! -s "${'$'}STAGE" ] && _arch_ok=0
        if [ "${'$'}_arch_ok" = "1" ] && command -v gzip >/dev/null 2>&1; then
            gzip -t "${'$'}STAGE" >/dev/null 2>&1 || _arch_ok=0
        fi
        if [ "${'$'}_arch_ok" = "1" ]; then
            "${'$'}TAR" -tzf "${'$'}STAGE" >/dev/null 2>&1 || _arch_ok=0
        fi

        if [ "${'$'}_arch_ok" != "1" ]; then
            _sz=${'$'}(wc -c < "${'$'}STAGE" 2>/dev/null || echo 0)
            rm -f "${'$'}STAGE"
            echo "FATAL: архив бэкапа не прошёл проверку (rc=${'$'}tar_rc, size=${'$'}{_sz:-0} B, TAR=${'$'}TAR, diag=${'$'}_tar_diag). Проверьте место: df -h /opt" >&2
            exit 1
        fi

        cat "${'$'}STAGE"
        cat_rc=${'$'}?
        rm -f "${'$'}STAGE"
        exit ${'$'}cat_rc
    """.trimIndent()

    val FULL_BACKUP_TAR_CMD: String get() = buildFullBackupCmd(includeUserMedia = false)

    fun restoreArchiveCmd(mode: String): String = """__LONG__
        set +e
$SH_ARCHIVER_HELPERS
$SH_MOUNT_DISCOVERY

        ensure_archiver 0

        ARCH="/opt/.gbox_restore.tar.gz"
        [ -s "${'$'}ARCH" ] || { echo "[FATAL] Архив не найден на /opt/.gbox_restore.tar.gz"; echo "PREFLIGHT_FAIL"; exit 1; }

        if command -v gzip >/dev/null 2>&1 && ! gzip -t "${'$'}ARCH" >/dev/null 2>&1; then
            echo "[FATAL] Архив повреждён (gzip CRC fail). Файл оставлен на /opt/.gbox_restore.tar.gz для повтора."
            echo "PREFLIGHT_FAIL"
            exit 1
        fi
        if ! "${'$'}TAR" -tzf "${'$'}ARCH" >/dev/null 2>&1; then
            echo "[FATAL] ${'$'}TAR не смог прочитать архив (${'$'}TAR -tzf fail, diag=${'$'}_tar_diag)."
            echo "PREFLIGHT_FAIL"
            exit 1
        fi

        _gbox_opt_fs_info
        _gbox_check_opkg_health
        if [ "${'$'}_opt_mounted" -ne 1 ]; then
            echo "[FATAL] Раздел /opt не смонтирован на реальном накопителе (fs=${'$'}{_opt_fs:-none}). Отказ во избежание переполнения ОЗУ."
            echo "PREFLIGHT_FAIL"
            rm -f "${'$'}ARCH"
            exit 1
        fi
        if [ "${'$'}_opkg_status" != "ok" ]; then
            echo "[INFO] Текущий opkg имеет статус '${'$'}_opkg_status' — откат восстановит рабочую папку /opt из архива."
        fi

        # Фиксируем системные утилиты прошивки ДО любых операций с /opt/lib
        _CP=/bin/cp; [ -x "${'$'}_CP" ] || _CP="${'$'}(command -v cp 2>/dev/null || echo cp)"
        _RM=/bin/rm; [ -x "${'$'}_RM" ] || _RM="${'$'}(command -v rm 2>/dev/null || echo rm)"
        _MV=/bin/mv; [ -x "${'$'}_MV" ] || _MV="${'$'}(command -v mv 2>/dev/null || echo mv)"
        _MKDIR=/bin/mkdir; [ -x "${'$'}_MKDIR" ] || _MKDIR="${'$'}(command -v mkdir 2>/dev/null || echo mkdir)"

        find /opt -maxdepth 1 \( -name '.gbox_rst_*' -o -name '.gbox_old_*' -o -name '.gbox_meta' \) -exec "${'$'}_RM" -rf {} + 2>/dev/null || true
        find /opt -maxdepth 1 -name '.gbox_prerestore.*' -type f -exec "${'$'}_RM" -f {} + 2>/dev/null || true

        RST="/opt/.gbox_rst_${'$'}${'$'}"
        "${'$'}_RM" -rf "${'$'}RST" && "${'$'}_MKDIR" -p "${'$'}RST"
        echo "=== [1/5] Распаковка архива (${'$'}TAR) ==="
        if ! (cd "${'$'}RST" && "${'$'}TAR" -xzf "${'$'}ARCH"); then
            echo "[FATAL] Ошибка распаковки архива (сохранён на /opt/.gbox_restore.tar.gz)"
            echo "PREFLIGHT_FAIL"
            "${'$'}_RM" -rf "${'$'}RST"
            exit 1
        fi
        echo "OK: архив распакован"

        PRE="/opt/.gbox_prerestore.${'$'}${'$'}.tar.gz"
        echo "=== [2/5] Страховочный снимок текущего /opt ==="
        if [ "${'$'}_tar_can_create" = "1" ]; then
            PRE_EXCL="/opt/tmp/.gbox_pre_excl_${'$'}${'$'}"
            "${'$'}_MKDIR" -p /opt/tmp 2>/dev/null || true
            {
              printf '%s\n' 'opt/.gbox_prerestore.*' 'opt/.gbox_restore.tar.gz' 'opt/.gbox_rst_*' 'opt/.gbox_old_*' 'opt/.gbox_meta'
              printf '%s\n' 'opt/gor4akov-offline' 'opt/gor4akov-offline/*' 'opt/z2k-work' 'opt/z2k-work/*' 'opt/tmp' 'opt/tmp/*'
              printf '%s\n' 'opt/var/log' 'opt/var/log/*' 'opt/var/run' 'opt/var/run/*' '*.log' '*.sock' '*.pid'
            } > "${'$'}PRE_EXCL"
            if [ "${'$'}_tar_can_x" = "1" ]; then
                (cd / && "${'$'}TAR" -czf "${'$'}PRE" -X "${'$'}PRE_EXCL" opt >/dev/null 2>&1)
                PRE_RC=${'$'}?
            else
                set --
                for _it in /opt/* /opt/.[!.]* /opt/..?*; do
                    [ -e "${'$'}_it" ] || [ -L "${'$'}_it" ] || continue
                    case "${'$'}{_it##*/}" in .gbox_*|gor4akov-offline|z2k-work|tmp) continue ;; esac
                    set -- "${'$'}@" "opt/${'$'}{_it##*/}"
                done
                (cd / && "${'$'}TAR" -czf "${'$'}PRE" "${'$'}@" >/dev/null 2>&1)
                PRE_RC=${'$'}?
            fi
            "${'$'}_RM" -f "${'$'}PRE_EXCL"
        else
            PRE_RC=1
        fi

        if [ "${'$'}PRE_RC" -gt 1 ] || [ ! -s "${'$'}PRE" ] || ! "${'$'}TAR" -tzf "${'$'}PRE" >/dev/null 2>&1; then
            "${'$'}_RM" -f "${'$'}PRE"
            PRE=""
            _free_kb=${'$'}(df -Pk /opt 2>/dev/null | awk 'NR==2{print ${'$'}4+0}')
            _arch_kb=${'$'}(du -sk "${'$'}ARCH" 2>/dev/null | awk '{print ${'$'}1+0}')
            if [ -n "${'$'}_free_kb" ] && [ -n "${'$'}_arch_kb" ] && [ "${'$'}_free_kb" -lt ${'$'}(( _arch_kb * 2 )) ]; then
                echo "[FATAL] Pre-snapshot не создан (нет места) и свободного меньше 2× размера архива: ${'$'}_free_kb КБ vs ${'$'}_arch_kb КБ."
                echo "PREFLIGHT_FAIL"
                "${'$'}_RM" -rf "${'$'}RST"
                exit 1
            fi
            echo "[WARN] Pre-snapshot не создан (tar_create=${'$'}_tar_can_create), но места хватает (${'$'}_free_kb vs ${'$'}_arch_kb КБ)."
        else
            PRE_KB=${'$'}(du -sk "${'$'}PRE" 2>/dev/null | awk '{print ${'$'}1}')
            echo "[OK] Снимок отката: ${'$'}PRE (${'$'}PRE_KB КБ)"
        fi

        echo "=== [3/5] Остановка служб /opt (сохраняя SSH dropbear для завершения отката) ==="
        for ini in /opt/etc/init.d/S*; do
            [ -x "${'$'}ini" ] && [ -f "${'$'}ini" ] || continue
            case "${'$'}{ini##*/}" in
                *dropbear*) continue ;;
            esac
            "${'$'}ini" stop >/dev/null 2>&1 || true
        done
        for _d in nfqws2 AdGuardHome lighttpd z2k-warpd z2k-http tg-mtproxy crond sing-box mihomo xray stubby dnscrypt-proxy; do
            killall "${'$'}_d" 2>/dev/null || true
        done
        sleep 1

        RESTORE_OK=1
        if [ "$mode" = "bypass" ]; then
            echo "=== [4/5] bypass: /opt/zapret2 + netfilter.d ==="
            if [ -d "${'$'}RST/opt/zapret2" ]; then
                "${'$'}_RM" -rf /opt/zapret2 && "${'$'}_CP" -a "${'$'}RST/opt/zapret2" /opt/zapret2 || RESTORE_OK=0
            fi
            if [ -d "${'$'}RST/opt/etc/ndm/netfilter.d" ]; then
                "${'$'}_RM" -rf /opt/etc/ndm/netfilter.d && "${'$'}_MKDIR" -p /opt/etc/ndm/netfilter.d
                "${'$'}_CP" -a "${'$'}RST/opt/etc/ndm/netfilter.d/." /opt/etc/ndm/netfilter.d/ 2>/dev/null || RESTORE_OK=0
            fi
        else
            echo "=== [4/5] Полный 100% откат /opt (все папки, файлы в корне и скрытые файлы) ==="
            if [ ! -d "${'$'}RST/opt" ] || [ -z "${'$'}(ls -A "${'$'}RST/opt" 2>/dev/null)" ]; then
                echo "[FATAL] В архиве отсутствует каталог opt/ — отмена во избежание потери Entware."
                RESTORE_OK=0
            else
                # Удаляем ВСЁ в /opt, кроме служебных .gbox_* и lost+found, чтобы не осталось хвостов после бэкапа
                for _cur in /opt/* /opt/.[!.]* /opt/..?*; do
                    [ -e "${'$'}_cur" ] || [ -L "${'$'}_cur" ] || continue
                    _cbn="${'$'}{_cur##*/}"
                    case "${'$'}_cbn" in
                        .gbox_*|lost+found) continue ;;
                    esac
                    "${'$'}_RM" -rf "${'$'}_cur" || RESTORE_OK=0
                done
                # Копируем ВСЁ из архива (никаких белых списков из 13 папок — восстанавливается и корень /opt!)
                for _src in "${'$'}RST"/opt/* "${'$'}RST"/opt/.[!.]* "${'$'}RST"/opt/..?*; do
                    [ -e "${'$'}_src" ] || [ -L "${'$'}_src" ] || continue
                    _sbn="${'$'}{_src##*/}"
                    case "${'$'}_sbn" in
                        .gbox_meta|.gbox_backup_stage*|.gbox_prerestore.*|.gbox_rst_*|.gbox_old_*|lost+found) continue ;;
                    esac
                    "${'$'}_MV" -f "${'$'}_src" "/opt/${'$'}_sbn" 2>/dev/null || "${'$'}_CP" -a "${'$'}_src" "/opt/${'$'}_sbn" || RESTORE_OK=0
                done
                "${'$'}_MKDIR" -p /opt/tmp /opt/var/log /opt/var/run 2>/dev/null || true
                chmod 1777 /opt/tmp 2>/dev/null || true
            fi
        fi

        if [ "${'$'}RESTORE_OK" -ne 1 ]; then
            echo "[FATAL] Ошибка восстановления /opt -> аварийный откат из pre-snapshot"
            if [ -n "${'$'}PRE" ] && [ -s "${'$'}PRE" ]; then
                (cd / && "${'$'}TAR" -xzf "${'$'}PRE" 2>&1 | tail -5)
                echo "[OK] Pre-snapshot восстановлен"
            else
                echo "[ERR] Pre-snapshot отсутствует"
            fi
            "${'$'}_RM" -rf "${'$'}RST"
            exit 1
        fi

        if [ "$mode" = "system_full" ]; then
            echo "=== [4b/5] 100% откат USB / носителей (UUID-защита от записи в RAM) ==="
            _opt_dev=${'$'}(awk '${'$'}2=="/opt"{print ${'$'}1; exit}' /proc/mounts 2>/dev/null)
            _opt_ino=${'$'}(ls -id /opt 2>/dev/null | awk '{print ${'$'}1}')
            _opt_bind=${'$'}(_gbox_opt_bind_sub)

            _cur_mnts="${'$'}RST/.cur_mnts"
            _gbox_collect_mounts "${'$'}_cur_mnts"
            _ext_cnt=0
            _ext_only=""
            while IFS= read -r _cm; do
                [ -n "${'$'}_cm" ] && [ -d "${'$'}_cm" ] || continue
                _cm_ino=${'$'}(ls -id "${'$'}_cm" 2>/dev/null | awk '{print ${'$'}1}')
                [ -n "${'$'}_opt_ino" ] && [ "${'$'}_cm_ino" = "${'$'}_opt_ino" ] && continue
                _ext_cnt=${'$'}((_ext_cnt + 1))
                _ext_only="${'$'}_cm"
            done < "${'$'}_cur_mnts"

            _tsv="${'$'}RST/.gbox_mounts.tsv"
            [ -s "${'$'}_tsv" ] || _tsv="${'$'}RST/opt/.gbox_meta/.gbox_mounts.tsv"
            if [ ! -s "${'$'}_tsv" ]; then
                : > "${'$'}RST/.legacy_mounts.tsv"
                for _rd in "${'$'}RST"/tmp/mnt/* "${'$'}RST"/media/* "${'$'}RST"/mnt/* "${'$'}RST"/storage; do
                    [ -d "${'$'}_rd" ] || continue
                    _rel="${'$'}{_rd#${'$'}RST/}"
                    printf '%s\t/%s\t\t\t\t\n' "${'$'}_rel" "${'$'}_rel" >> "${'$'}RST/.legacy_mounts.tsv"
                done
                _tsv="${'$'}RST/.legacy_mounts.tsv"
            fi

            _TAB=${'$'}(printf '\t')
            while IFS="${'$'}_TAB" read -r _arel _orig_mnt _orig_uuid _orig_lbl _orig_fs _orig_sub; do
                [ -n "${'$'}_arel" ] && [ -d "${'$'}RST/${'$'}_arel" ] || continue
                _target=""
                # 1) Совпадение по точному пути монтирования (проверяем что это реальная ФС, не tmpfs!)
                if [ -n "${'$'}_orig_mnt" ] && grep -Fxq "${'$'}_orig_mnt" "${'$'}_cur_mnts" 2>/dev/null; then
                    _target="${'$'}_orig_mnt"
                fi
                # 2) Поиск по UUID (если флешка смонтировалась под другим именем/меткой)
                if [ -z "${'$'}_target" ] && [ -n "${'$'}_orig_uuid" ] && command -v blkid >/dev/null 2>&1; then
                    while IFS= read -r _cm; do
                        _cdev=${'$'}(df -Pm "${'$'}_cm" 2>/dev/null | awk 'NF==1{d=${'$'}0; getline; ${'$'}0=d" "${'$'}0} NR==2{print ${'$'}1}')
                        _cu=${'$'}(blkid "${'$'}_cdev" 2>/dev/null | sed -n 's/.*UUID="\([^"]*\)".*/\1/p' | head -n1)
                        if [ "${'$'}_cu" = "${'$'}_orig_uuid" ]; then
                            _target="${'$'}_cm"
                            break
                        fi
                    done < "${'$'}_cur_mnts"
                fi
                # 3) Если вставлена новая единственная пользовательская флешка — маппим на неё (без записи в /tmp RAM!)
                if [ -z "${'$'}_target" ] && [ "${'$'}_ext_cnt" -eq 1 ] && [ -n "${'$'}_ext_only" ]; then
                    _target="${'$'}_ext_only"
                    echo "[INFO] Маппинг носителя ${'$'}_orig_mnt -> ${'$'}_target (другая флешка)"
                fi
                if [ -z "${'$'}_target" ]; then
                    echo "[WARN] Носитель ${'$'}_orig_mnt не примонтирован — пропуск (защита ОЗУ от OOM)"
                    continue
                fi

                # Находим на целевом разделе активную подпапку /opt (если Entware в подпапке), чтобы её не снести!
                _keep_sub=""
                _tdev=${'$'}(df -Pm "${'$'}_target" 2>/dev/null | awk 'NF==1{d=${'$'}0; getline; ${'$'}0=d" "${'$'}0} NR==2{print ${'$'}1}')
                _tfs=${'$'}(df -PT "${'$'}_target" 2>/dev/null | awk 'NF==1{d=${'$'}0; getline; ${'$'}0=d" "${'$'}0} NR==2{print ${'$'}2}')
                if [ -n "${'$'}_opt_dev" ] && [ "${'$'}_tdev" = "${'$'}_opt_dev" ]; then
                    _tino=${'$'}(ls -id "${'$'}_target" 2>/dev/null | awk '{print ${'$'}1}')
                    if [ -n "${'$'}_opt_ino" ] && [ "${'$'}_tino" = "${'$'}_opt_ino" ]; then
                        echo "[INFO] ${'$'}_target == корень /opt, уже полностью восстановлен"
                        continue
                    fi
                    if [ "${'$'}_opt_bind" != "/" ] && [ -d "${'$'}_target${'$'}_opt_bind" ]; then
                        _keep_sub="${'$'}_opt_bind"
                        _keep_sub="${'$'}{_keep_sub#/}"
                    else
                        for _sub in "${'$'}_target"/* "${'$'}_target"/*/*; do
                            [ -d "${'$'}_sub" ] || continue
                            _sino=${'$'}(ls -id "${'$'}_sub" 2>/dev/null | awk '{print ${'$'}1}')
                            if [ -n "${'$'}_opt_ino" ] && [ "${'$'}_sino" = "${'$'}_opt_ino" ]; then
                                _keep_sub="${'$'}{_sub#${'$'}_target/}"
                                break
                            fi
                        done
                    fi
                fi

                # Очищаем целевой USB до состояния снимка (включая корень USB!), сохраняя _keep_sub и lost+found
                for _cur in "${'$'}_target"/* "${'$'}_target"/.[!.]* "${'$'}_target"/..?*; do
                    [ -e "${'$'}_cur" ] || [ -L "${'$'}_cur" ] || continue
                    _cbn="${'$'}{_cur#${'$'}_target/}"
                    case "${'$'}_cbn" in
                        lost+found|.gbox_*) continue ;;
                    esac
                    if [ -n "${'$'}_keep_sub" ] && { [ "${'$'}_cbn" = "${'$'}_keep_sub" ] || [ "${'$'}_cbn" = "${'$'}{_keep_sub%%/*}" ]; }; then
                        if [ "${'$'}_cbn" = "${'$'}_keep_sub" ]; then
                            continue
                        fi
                        # Если Entware на 2 уровне глубже, чистим соседние подпапки аккуратно, не трогая _keep_sub
                        for _cur2 in "${'$'}_cur"/* "${'$'}_cur"/.[!.]*; do
                            [ -e "${'$'}_cur2" ] || [ -L "${'$'}_cur2" ] || continue
                            _c2rel="${'$'}{_cur2#${'$'}_target/}"
                            [ "${'$'}_c2rel" = "${'$'}_keep_sub" ] && continue
                            "${'$'}_RM" -rf "${'$'}_cur2" || true
                        done
                        continue
                    fi
                    "${'$'}_RM" -rf "${'$'}_cur" || RESTORE_OK=0
                done

                # Копируем все файлы (включая файлы в корне USB и скрытые файлы)
                for _src in "${'$'}RST/${'$'}_arel"/* "${'$'}RST/${'$'}_arel"/.[!.]* "${'$'}RST/${'$'}_arel"/..?*; do
                    [ -e "${'$'}_src" ] || [ -L "${'$'}_src" ] || continue
                    _sbn="${'$'}{_src#${'$'}RST/${'$'}_arel/}"
                    case "${'$'}_sbn" in
                        lost+found|.gbox_*) continue ;;
                    esac
                    [ -n "${'$'}_keep_sub" ] && [ "${'$'}_sbn" = "${'$'}_keep_sub" ] && continue
                    [ -n "${'$'}_orig_sub" ] && [ "${'$'}_sbn" = "${'$'}_orig_sub" ] && continue
                    case "${'$'}_tfs" in
                        vfat|ntfs|fuseblk|exfat|msdos)
                            "${'$'}_CP" -rf "${'$'}_src" "${'$'}_target/${'$'}_sbn" || RESTORE_OK=0
                            ;;
                        *)
                            "${'$'}_CP" -a "${'$'}_src" "${'$'}_target/${'$'}_sbn" || RESTORE_OK=0
                            ;;
                    esac
                done
                echo "[OK] USB носитель восстановлен: ${'$'}_target"
            done < "${'$'}_tsv"

            echo "=== [4c/5] NDMS конфиг (diff-очистка + fail-safe + точный opkg disk) ==="
            CFG_SRC="${'$'}RST/running-config.txt"
            [ -s "${'$'}CFG_SRC" ] || CFG_SRC="${'$'}RST/opt/.gbox_meta/running-config.txt"
            [ -s "${'$'}CFG_SRC" ] || CFG_SRC="${'$'}RST/startup-config.txt"
            [ -s "${'$'}CFG_SRC" ] || CFG_SRC="${'$'}RST/opt/.gbox_meta/startup-config.txt"

            if [ -s "${'$'}CFG_SRC" ]; then
                # Справочник NDMS 5.1 (3.188.6): защитный таймер на случай обрыва связи при применении конфига
                LD_LIBRARY_PATH= /bin/ndmc -c "system configuration fail-safe timer 180" >/dev/null 2>&1 || true

                # Сохраняем список виртуальных объектов, созданных ПОСЛЕ бэкапа, чтобы удалить их через `no ...`
                CUR_CFG="${'$'}RST/.cur_running.txt"
                LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" > "${'$'}CUR_CFG" 2>/dev/null || : > "${'$'}CUR_CFG"
                tr -d '\r' < "${'$'}CFG_SRC" > "${'$'}RST/.bkp_clean.txt"
                tr -d '\r' < "${'$'}CUR_CFG" | awk -v BKP="${'$'}RST/.bkp_clean.txt" '
                  BEGIN {
                    while ((getline l < BKP) > 0) {
                      sub(/^[[:space:]]+/, "", l)
                      sub(/[[:space:]]+$/, "", l)
                      bkp[l] = 1
                      if (l ~ /^interface /) bkp_if[l] = 1
                      if (l ~ /^ip policy /) bkp_pol[l] = 1
                    }
                    close(BKP)
                  }
                  /^[[:space:]]*#/ || /^[[:space:]]*!/ || /^[[:space:]]*$/ { next }
                  /^interface / {
                    if (!(${'$'}0 in bkp_if)) {
                      name = ${'$'}2
                      if (name ~ /^(Bridge[1-9][0-9]*|Wireguard[0-9]+|OpenVPN[0-9]+|L2TP[0-9]+|PPTP[0-9]+|PPPoE[0-9]+|SSTP[0-9]+|Gre[0-9]+|Ipip[0-9]+|Eoip[0-9]+|Tunnel[0-9]+|GuestWiFi.*)/) {
                        print "no " ${'$'}0
                      }
                    }
                    next
                  }
                  /^ip policy / {
                    if (!(${'$'}0 in bkp_pol)) print "no " ${'$'}0
                    next
                  }
                  /^ip route / {
                    if (!(${'$'}0 in bkp)) print "no " ${'$'}0
                    next
                  }
                  /^opkg dns-override$/ {
                    if (!(${'$'}0 in bkp)) print "no opkg dns-override"
                    next
                  }
                ' > "${'$'}RST/.ndms_cleanup.txt"

                while IFS= read -r cln; do
                    [ -n "${'$'}cln" ] || continue
                    LD_LIBRARY_PATH= /bin/ndmc -c "${'$'}cln" >/dev/null 2>&1 || true
                done < "${'$'}RST/.ndms_cleanup.txt"

                err_cnt=0
                ok_cnt=0
                tr -d '\r' < "${'$'}CFG_SRC" | awk '
                  function skip(cmd) {
                    if (cmd ~ /^(user|no user|system mode|opkg disk|no opkg disk) /) return 1
                    if (cmd ~ /^interface Bridge0 ip address /) return 1
                    if (cmd ~ /^interface Bridge0 mac address /) return 1
                    if (cmd ~ /^(no )?service (telnet|ssh|ftp|cifs|upnp|dlna|torrent|afp|snmp|mdns|igmp-proxy|vpn-server|oc-server)/) return 1
                    if (cmd ~ /^ip http webdav/) return 1
                    if (cmd ~ /^no ip http webdav/) return 1
                    return 0
                  }
                  /^[[:space:]]*${'$'}/ {next}
                  /^[[:space:]]*#/ {next}
                  /^[[:space:]]*!/ {if (${'$'}0 ~ /^!${'$'}/) blk=""; next}
                  /^[[:space:]]/ {sub(/^[[:space:]]+/,""); cmd=(blk!="" ? blk" "${'$'}0 : ${'$'}0); if (!skip(cmd)) print cmd; next}
                  {blk=${'$'}0; if (!skip(blk)) print blk}
                ' > "${'$'}RST/.ndms_flat.txt"
                : > "${'$'}RST/.ndms_retry.txt"
                while IFS= read -r line; do
                    [ -n "${'$'}line" ] || continue
                    out_c=${'$'}(LD_LIBRARY_PATH= /bin/ndmc -c "${'$'}line" 2>&1)
                    if printf '%s' "${'$'}out_c" | grep -qiE 'error|invalid|no such command'; then
                        printf '%s\n' "${'$'}line" >> "${'$'}RST/.ndms_retry.txt"
                    else
                        ok_cnt=${'$'}((ok_cnt + 1))
                    fi
                done < "${'$'}RST/.ndms_flat.txt"
                if [ -s "${'$'}RST/.ndms_retry.txt" ]; then
                    while IFS= read -r line; do
                        [ -n "${'$'}line" ] || continue
                        out_c=${'$'}(LD_LIBRARY_PATH= /bin/ndmc -c "${'$'}line" 2>&1)
                        if printf '%s' "${'$'}out_c" | grep -qiE 'error|invalid|no such command'; then
                            err_cnt=${'$'}((err_cnt + 1))
                            [ "${'$'}err_cnt" -le 5 ] && echo "[WARN] ndmc: ${'$'}line -> ${'$'}out_c"
                        else
                            ok_cnt=${'$'}((ok_cnt + 1))
                        fi
                    done < "${'$'}RST/.ndms_retry.txt"
                fi
                LD_LIBRARY_PATH= /bin/ndmc -c "system configuration fail-safe commit" >/dev/null 2>&1 || true
                LD_LIBRARY_PATH= /bin/ndmc -c "system configuration save" >/dev/null 2>&1 || true
                echo "[OK] NDMS: применено ${'$'}ok_cnt, ошибок ${'$'}err_cnt"
            fi

            # Auto-UUID + точный bind-путь opkg disk (п. 3.143: <UUID>: или <UUID>:/subfolder)
            OPT_DEV=${'$'}(awk '${'$'}2=="/opt"{print ${'$'}1;exit}' /proc/mounts 2>/dev/null)
            [ -z "${'$'}OPT_DEV" ] && OPT_DEV=${'$'}(mount 2>/dev/null | awk '${'$'}3=="/opt"{print ${'$'}1;exit}')
            if [ -n "${'$'}OPT_DEV" ]; then
                OPT_UUID=${'$'}(blkid "${'$'}OPT_DEV" 2>/dev/null | sed -n 's/.*UUID="\([^"]*\)".*/\1/p' | head -n1)
                if [ -n "${'$'}OPT_UUID" ]; then
                    _spec="${'$'}OPT_UUID:"
                    if [ -n "${'$'}_opt_bind" ] && [ "${'$'}_opt_bind" != "/" ]; then
                        _spec="${'$'}OPT_UUID:${'$'}_opt_bind"
                    fi
                    LD_LIBRARY_PATH= /bin/ndmc -c "opkg disk ${'$'}_spec" >/dev/null 2>&1
                    LD_LIBRARY_PATH= /bin/ndmc -c "opkg initrc /opt/etc/init.d/rc.unslung" >/dev/null 2>&1
                    LD_LIBRARY_PATH= /bin/ndmc -c "system configuration save" >/dev/null 2>&1
                    echo "[OK] opkg disk привязан: ${'$'}_spec"
                else
                    echo "[WARN] UUID текущей /opt не определён"
                fi
            fi
        fi

        echo "=== [5/5] Запуск служб /opt и проверка opkg ==="
        for ini in /opt/etc/init.d/S*; do
            [ -x "${'$'}ini" ] && [ -f "${'$'}ini" ] || continue
            case "${'$'}{ini##*/}" in
                *dropbear*)
                    if (ps w 2>/dev/null || ps 2>/dev/null) | grep -q '[d]ropbear'; then
                        continue
                    fi
                    ;;
            esac
            "${'$'}ini" start >/dev/null 2>&1 || true
        done
        if [ -x /opt/bin/opkg ] && /opt/bin/opkg --version >/dev/null 2>&1; then
            echo "[OK] Entware opkg здоров (${'$'}(/opt/bin/opkg --version 2>/dev/null | head -n1))"
        else
            echo "[WARN] После отката /opt/bin/opkg не отвечает — проверьте флешку /opt"
        fi
        [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart >/dev/null 2>&1 || true
        sleep 2
        NFQ=${'$'}(ps w 2>/dev/null | grep -c "[n]fqws2")
        AGH=${'$'}(ps w 2>/dev/null | grep -c "[A]dGuardHome")
        LTP=${'$'}(ps w 2>/dev/null | grep -c "[l]ighttpd")
        DBR=${'$'}(ps w 2>/dev/null | grep -c "[d]ropbear")
        echo "=== Готово: nfqws2=${'$'}NFQ AdGuard=${'$'}AGH lighttpd=${'$'}LTP dropbear=${'$'}DBR ==="
        if [ "${'$'}NFQ" -eq 0 ]; then
            echo "[WARN] nfqws2 НЕ стартовал — проверьте: /opt/etc/init.d/S99zapret2 restart"
        fi
        if [ "${'$'}LTP" -eq 0 ]; then
            echo "[WARN] lighttpd НЕ стартовал — проверьте: /opt/etc/init.d/S99lighttpd start"
        fi

        "${'$'}_RM" -f "${'$'}ARCH"
        "${'$'}_RM" -rf "${'$'}RST"
        if [ -n "${'$'}PRE" ] && [ -s "${'$'}PRE" ]; then
            echo "[OK] Pre-snapshot сохранён: ${'$'}PRE"
        fi
        echo "INSTALL_RESTORE_DONE"
    """.trimIndent()
}
