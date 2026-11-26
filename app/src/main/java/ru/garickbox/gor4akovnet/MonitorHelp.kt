package ru.garickbox.gor4akovnet

data class MonitorHelpTopic(
    val title: String,
    val what: String,
    val why: String,
    val recommend: String,
    val caution: String = ""
)

data class ServiceGuide(
    val key: String,
    val title: String,
    val port: String,
    val summary: String,
    val details: String,
    val tips: String,
    val configPath: String = ""
)

object MonitorHelp {
    val topics = mapOf(
        "overview" to MonitorHelpTopic(
            title = "Обзор и состояние роутера",
            what = "Главная сводка состояния Keenetic / Netcraze: светофор здоровья, загрузка CPU, RAM и Swap, температура радиомодулей, Conntrack, аптайм, свободное место на /opt и статус всех служб.",
            why = "Позволяет за 2 секунды увидеть, работает ли DPI-обход, не перегружена ли память и не отвалился ли USB-носитель.",
            recommend = "Если Conntrack > 80% — увеличьте лимит в разделе «PPE & Порты». Для стабильной работы используйте «Оптимизатор (До/После)».",
            caution = "При 100% заполнении /opt перестают записываться логи и обновления списков."
        ),
        "services" to MonitorHelpTopic(
            title = "Службы Entware (/opt/etc/init.d), Cron и пакеты opkg",
            what = "Таблица всех автозапускаемых служб /opt/etc/init.d/S* с реальной нагрузкой CPU%, потреблением RAM (МБ), PID и открытыми портами, плюс менеджер задач Cron и пакеты opkg.",
            why = "Позволяет увидеть, сколько памяти ест AdGuard Home или Zapret2, перезапустить зависший сервис, настроить расписание Cron и управлять пакетами opkg.",
            recommend = "Держите свободными минимум 20–30 МБ RAM (или включите ZRAM). Используйте кнопку «?» рядом с известной службой для справки.",
            caution = "Остановка S99zapret2 отключает обход блокировок; остановка Dropbear лишит доступа по SSH."
        ),
        "storage" to MonitorHelpTopic(
            title = "Накопители USB / FLASH и системные опции NDMS",
            what = "Управление системными функциями прошивки (SMB, WebDAV, ZRAM, режим USB 2.0) и диагностикой монтирования /opt.",
            why = "Надёжная работа Entware и AdGuard напрямую зависит от стабильности питания USB-носителя и наличия ZRAM.",
            recommend = "Держите ZRAM включённым на роутерах с 256–512 МБ RAM. Если USB-диск периодически отваливается — включите USB 2.0 режим.",
            caution = "Не выдёргивайте USB-флешку без остановки служб /opt."
        ),
        "wan" to MonitorHelpTopic(
            title = "WAN, маршруты и интернет-канал",
            what = "Текущий внешний IP, шлюз провайдера, активный WAN-интерфейс, DNS-серверы, объём трафика и параметры политики Gor4akovNET.",
            why = "Показывает реальный активный интерфейс выхода в сеть на любой модели Keenetic / Netcraze без привязки к имени порта.",
            recommend = "Встроенный замер скорости и пинг выполняются прямо с роутера — это исключает влияние Wi-Fi.",
            caution = ""
        ),
        "dns" to MonitorHelpTopic(
            title = "AdGuard Home, DoH/DoT DNS и журнал запросов",
            what = "Управление блокировщиком рекламы AdGuard Home (:53 и :3000), шифрованными апстримами (DoH/DoT), фильтрами, перехватом opkg dns-override и живым журналом с 1-тап действиями (+обход / +исключения).",
            why = "Шифрованный DNS защищает от подмены и фильтрации DNS у провайдера, а журнал позволяет за 1 нажатие добавить нужный домен в обход или белый список.",
            recommend = "Включите DoH Стабильный и Оптимистичный кэш.",
            caution = "При выключенном AdGuard и включённом opkg dns-override домены перестанут открываться — отключайте dns-override при остановке AdGuard."
        ),
        "nat_ppe" to MonitorHelpTopic(
            title = "Аппаратное ускорение (PPE), UPnP и проброс портов",
            what = "Управление Hardware/Software NAT (PPE), UPnP, IGMP Proxy, лимитом Conntrack и статическим пробросом портов (ip static).",
            why = "Hardware/Software PPE снижают нагрузку на CPU и повышают скорость WAN, а проброс портов даёт прямой доступ к серверам и консолям.",
            recommend = "PPE Hardware + Software, UPnP ON и Conntrack 32768–65536.",
            caution = "При отключении PPE скорость на гигабитных тарифах падает из-за загрузки процессора."
        ),
        "z2k" to MonitorHelpTopic(
            title = "Zapret2 (z2k) — стратегии, режимы, пулы и автоподбор",
            what = "Полное управление движком nfqws2: режимы (Autohostlist / Hostlist / Direct), автоподбор стратегий с применением в пул (rkn_tcp, yt_tcp, yt_quic, gv_tcp, discord_udp), счётчики NFQUEUE, дополнительные домены и исключения.",
            why = "Позволяет чинить конкретные сайты за 10–30 секунд без переустановки и сразу сохранять найденную стратегию в рабочий пул.",
            recommend = "Держите банки, Госуслуги и локальные сервисы в «Исключениях».",
            caution = "После смены режима или списков nfqws2 автоматически перезагружается."
        ),
        "warp" to MonitorHelpTopic(
            title = "Cloudflare WARP (туннель z2k, транспорт и маршрутизация)",
            what = "Точечный WireGuard / MASQUE (H2) туннель Cloudflare WARP: переключение транспорта, проверка трассировки Cloudflare colo/IP, регистрация, правила игр/сервисов, домены и выбор целых устройств.",
            why = "Обычный DPI-обход не меняет ваш IP. WARP выборочно направляет заблокированные по российскому IP сервисы через ближайший узел Cloudflare.",
            recommend = "Для лучшего пинга используйте WireGuard (wg); если провайдер режет WireGuard — переключитесь на MASQUE (h2).",
            caution = "Потребляет ~25–35 МБ RAM и добавляет 15–40 мс задержки выбранным правилам."
        ),
        "wifi" to MonitorHelpTopic(
            title = "Wi-Fi диапазоны, каналы и диагностика слотов AP",
            what = "Мониторинг точек доступа (2.4 ГГц и 5 ГГц), выбор чистых каналов, ширина полосы, 802.11r (FT), Band Steering, TWT и очистка «мёртвых» слотов AccessPoint.",
            why = "Помогает убрать лаги и наводки от соседских роутеров, а также удалить зависшие пустые точки доступа.",
            recommend = "На 2.4 ГГц — каналы 1, 6 или 11 (20 МГц). На 5 ГГц — не-DFS каналы 36, 40, 44 или 48.",
            caution = "Смена канала перезапускает Wi-Fi на 5–15 секунд."
        ),
        "devices" to MonitorHelpTopic(
            title = "Клиенты сети, таймеры доступа, шейпер и политики",
            what = "Полное управление подключёнными устройствами: фильтры (Онлайн / Обход / В блоке), карточка устройства с таймером (интернет/обход на 15–240 мин), расписанием NDMS, ограничением скорости (Мбит/с), политикой Gor4akovNET / WARP, приоритетом QoS, статическим IP и WoL.",
            why = "Заменяет ПК-админку для контроля детей, ТВ-приставок и серверов прямо с экрана телефона.",
            recommend = "Используйте быстрый таймер на 30/60 минут, чтобы выдать ребёнку или гостю временный доступ.",
            caution = "Таймеры на роутере работают автономно (даже если вы закроете приложение на телефоне)."
        ),
        "backups" to MonitorHelpTopic(
            title = "Полные резервные копии v2, экспорт/импорт и откат",
            what = "Потоковый 100%-бэкап системы (v2 .tar.gz: running/startup-config, Весь /opt/etc с ключами Dropbear и AdGuard, /opt/zapret2, /opt/root, /opt/home, Cron и при желании все данные USB-носителей), хранение в памяти телефона, импорт с телефона и 3-режимный откат (совместим с v1, v2 и .txt).",
            why = "Обеспечивает 100% страховку перед любыми экспериментами — откат восстанавливает даже ключи SSH, настройки роутера и файлы носителя.",
            recommend = "Начальный снимок до настройки защищён от удаления. Для переноса на другой телефон используйте «Экспорт» и «Импорт с телефона».",
            caution = "Опция «Включая файлы USB/Storage» может весить сотни МБ — сначала проверьте оценку размера."
        ),
        "files" to MonitorHelpTopic(
            title = "Файловый менеджер роутера (X-plore)",
            what = "Навигация по файловой системе роутера по SSH, создание папок/файлов, редактор конфигов (с автосозданием .bak) и быстрый откат из .bak.",
            why = "Удобно быстро поправить /opt/zapret2/config, списки доменов или AdGuardHome.yaml с телефона.",
            recommend = "При ошибке в правке нажмите «Откатить .bak» рядом с нужным конфигом.",
            caution = "Файлы больше 32 КБ открываются в безопасном режиме просмотра."
        ),
        "torch" to MonitorHelpTopic(
            title = "Live Torch, потоки трафика и DPI-трассировка",
            what = "Живой монитор скоростей (Мбит/с + PPS) по интерфейсам каждые 2 секунды, таблица активных соединений с именами клиентов и доменами из AdGuard, сравнение DNS (Роутер vs 1.1.1.1 vs 8.8.8.8), диагностика TLS/DPI-блокировок, замер tcpdump и счётчики NFQUEUE.",
            why = "Показывает в реальном времени, кто грузит канал, на какой домен идут пакеты, подменяет ли провайдер DNS или режет TLS ClientHello.",
            recommend = "Если сайт не открывается даже с обходом — проверьте его через «Сравнить DNS» и «DPI/TLS трасса».",
            caution = "Живой режим отправляет лёгкий опрос каждые 2 секунды; выключайте автообновление при уходе со вкладки (выключается само)."
        ),
        "logs" to MonitorHelpTopic(
            title = "Журналы служб, NDMS Syslog и SSH-терминал",
            what = "Просмотр свежих логов Zapret2, AdGuard Home, WARP, планировщика, системного журнала роутера, dmesg, сводки ошибок Syslog и прямой SSH-терминал.",
            why = "Любая ошибка запуска службы или обрыв WAN/USB видна сразу в сводке или последних строках журнала.",
            recommend = "Сначала откройте «Сводку ошибок (Syslog + dmesg)», затем — детальный журнал нужной службы.",
            caution = "Не запускайте бесконечные команды без таймаута."
        ),
        "app_center" to MonitorHelpTopic(
            title = "Центр пакетов (App Center)",
            what = "Кураторский каталог туннелей, DNS и CLI-утилит для Entware: Sing-box, Mihomo / XKeen, AmneziaWG, AdGuard Home, iperf3, tcpdump, mtr, htop, mc, nano.",
            why = "Показывает архитектуру роутера, свободное место на /opt, статус установки/работы и позволяет в 1 нажатие поставить, запустить или удалить пакет.",
            recommend = "Установите iperf3 и mtr для точных замеров скорости и трассировки прямо с роутера.",
            caution = "Перед установкой убедитесь, что на /opt свободно минимум 25–30 МБ и хватает inodes (см. USB & Накопители)."
        ),
        "ndms_ifaces" to MonitorHelpTopic(
            title = "NDMS: Интерфейсы, WAN и VPN-туннели",
            what = "Прямое управление сетевыми интерфейсами роутера Keenetic / Netcraze через RCI (с резервным каналом SSH ndmc): ISP, Bridge LAN, WireGuard, OpenVPN, SSTP, USB-модемы.",
            why = "Позволяет без веб-админки роутера включить/отключить интерфейс, изменить MTU, проверить состояние пира WireGuard и внешний адрес.",
            recommend = "Перед любым изменением Gor4akovNET автоматически сохраняет снимок конфигурации в Config Vault.",
            caution = "Не отключайте Bridge0 или основной WAN (ISP), если подключены к роутеру по этой сети — потеряете связь."
        ),
        "ndms_policies" to MonitorHelpTopic(
            title = "NDMS: Политики маршрутизации, Маршруты и Доктор сети",
            what = "Редактор ip policy и статических маршрутов ip route, совмещённый с глубоким инспектором Linux ip rule, таблиц маршрутизации, реального исходящего IP по интерфейсам и проверки утечек DNS/IPv6.",
            why = "Помогает точно видеть, через какой шлюз или VPN-туннель реально уходит трафик и не утекают ли DNS-запросы мимо AdGuard / туннеля.",
            recommend = "После правок политик запустите «Доктор маршрутов», чтобы убедиться в корректности исходящего IP.",
            caution = "Обход блокировок Zapret2 работает глобально через NFQUEUE и не требует отдельной политики."
        ),
        "ndms_lan_sys" to MonitorHelpTopic(
            title = "NDMS: LAN DHCP, Config Vault и NDM-консоль",
            what = "Настройка пулов DHCP (DNS-серверы и срок аренды), управление именем роутера и сохранением flash, интерактивная NDM CLI-консоль и хранилище откатов Config Vault.",
            why = "Даёт полный контроль над локальной сетью и страховку от неудачных правок: любой снимок до изменения можно откатить в 1 тап.",
            recommend = "Перед ручными экспериментами в NDM-консоли нажмите «Снимок сейчас» в Config Vault.",
            caution = "Форматирование USB-флешки в ext4 полностью стирает выбранный раздел (блокировано для активного /opt)."
        )
    )

    val toggleHelps = mapOf(
        "cifs" to MonitorHelpTopic(
            title = "Windows-сеть (SMB / CIFS)",
            what = "Открывает подключённую USB-флешку как сетевую папку в Windows / macOS / Android.",
            why = "Удобно сбрасывать файлы или бэкапы на диск роутера без SSH.",
            recommend = "Включайте только при необходимости доступа по локальной сети.",
            caution = "В открытых/недоверенных сетях держите выключенным."
        ),
        "webdav" to MonitorHelpTopic(
            title = "WebDAV-сервер NDMS",
            what = "Встроенный HTTP-сервер доступа к файлам накопителя роутера.",
            why = "Используется приложением на шаге установки Entware без ПК и для удалённого доступа к флешке.",
            recommend = "После установки Entware и обхода можно отключить для экономии ресурсов.",
            caution = ""
        ),
        "zram" to MonitorHelpTopic(
            title = "ZRAM (сжатие оперативной памяти)",
            what = "Создаёт сжатый раздел подкачки прямо в RAM (~50–128 МБ).",
            why = "Спасает роутеры с 256–512 МБ RAM от падения AdGuard Home и nfqws2 при нехватке памяти без износа USB-флешки.",
            recommend = "Обязательно держите ВКЛЮЧЁННЫМ.",
            caution = ""
        ),
        "usb2" to MonitorHelpTopic(
            title = "Принудительный режим USB 2.0",
            what = "Ограничивает порт USB 3.0 скоростью и шиной USB 2.0.",
            why = "Порт USB 3.0 создаёт сильные радиопомехи на диапазоне Wi-Fi 2.4 ГГц и иногда перегружает питание дешёвых флешек.",
            recommend = "Включите, если флешка периодически отваливается или падает скорость Wi-Fi 2.4 ГГц.",
            caution = "Максимальная скорость чтения USB снизится до ~30–35 МБ/с (для Entware достаточно с запасом)."
        ),
        "agh_prot" to MonitorHelpTopic(
            title = "AdGuard: Общая защита (protection_enabled)",
            what = "Главный выключатель фильтрации и модификации DNS в AdGuard Home.",
            why = "Позволяет в 1 тап временно пропустить всё без фильтрации (например, для проверки сломанного сайта) без отключения AdGuard.",
            recommend = "Держите ВКЛЮЧЁННЫМ.",
            caution = ""
        ),
        "agh_filt" to MonitorHelpTopic(
            title = "AdGuard: Фильтры рекламы",
            what = "Проверка DNS-запросов по подключённым чёрным спискам (Hostlists).",
            why = "Убирает рекламу, баннеры, телеметрию и вредоносные домены на всех устройствах сети.",
            recommend = "Держите ВКЛЮЧЁННЫМ.",
            caution = "При проблеме с конкретным сайтом используйте +Исключения прямо в журнале запросов."
        ),
        "dns_ov" to MonitorHelpTopic(
            title = "Перехват DNS роутером (opkg dns-override)",
            what = "Отключает встроенный DNS NDMS и передаёт порт :53 процессу Entware (AdGuard Home).",
            why = "Без этого переключателя устройства продолжат ходить мимо AdGuard Home в DNS провайдера.",
            recommend = "Держите ВКЛЮЧЁННЫМ, пока работает AdGuard Home.",
            caution = "Если AdGuard остановлен, а dns-override включён — сайты перестанут открываться по имени!"
        ),
        "dnssec" to MonitorHelpTopic(
            title = "AdGuard: DNSSEC",
            what = "Криптографическая проверка цифровой подписи DNS-ответов.",
            why = "Защищает от DNS-спуфинга на стороне транзитных сетей.",
            recommend = "Можно включить с DoH-апстримами (Quad9 / Cloudflare). Добавляет +2–10 мс только при холодном кэше.",
            caution = "Некоторые старые корпоративные домены с битой подписью могут не резолвиться."
        ),
        "cache_opt" to MonitorHelpTopic(
            title = "AdGuard: Оптимистичный кэш",
            what = "Сразу отдаёт устаревшую запись из кэша за 0.5 мс, а затем незаметно обновляет её в фоне.",
            why = "Резко ускоряет открытие сайтов и снижает ощущаемую задержку DNS до нуля.",
            recommend = "Настоятельно рекомендуется ВКЛЮЧИТЬ (особенно для DoH).",
            caution = ""
        ),
        "no_aaaa" to MonitorHelpTopic(
            title = "AdGuard: Блокировать IPv6 (AAAA)",
            what = "Не отдаёт IPv6-адреса устройствам, оставляя только IPv4.",
            why = "Если провайдер выдал IPv6, браузеры часто уходят через IPv6 в обход локальных правил маршрутизации и обхода.",
            recommend = "Включите, если есть утечки или тормоза на YouTube / соцсетях.",
            caution = ""
        ),
        "ppe_hw" to MonitorHelpTopic(
            title = "Hardware NAT (ppe hardware)",
            what = "Аппаратное ускорение NAT и маршрутизации прямо на чипе коммутатора.",
            why = "Снимает нагрузку с процессора роутера на высоких тарифах (300–1000 Мбит/с).",
            recommend = "Держите ВКЛЮЧЁННЫМ (если конкретный обход не требует полного отключения offload).",
            caution = "nfqws2 в z2k сам отключает offload только там, где это мешает DPI-пакетам."
        ),
        "ppe_sw" to MonitorHelpTopic(
            title = "Software PPE (ppe software)",
            what = "Программный fast-path ядра Linux/NDMS для потоков, не попавших в аппаратный чип.",
            why = "Ускоряет VPN, PPPoE и сложные маршруты без полной загрузки CPU.",
            recommend = "Рекомендуется ВКЛЮЧИТЬ.",
            caution = ""
        ),
        "upnp" to MonitorHelpTopic(
            title = "UPnP",
            what = "Позволяет играм, консолям (PS5/Xbox), торрентам автоматически просить роутер открыть нужный порт.",
            why = "Убирает «Строгий NAT» в онлайн-играх без ручного проброса десятков портов.",
            recommend = "Для домашней сети — ВКЛЮЧИТЬ.",
            caution = ""
        ),
        "igmp" to MonitorHelpTopic(
            title = "IGMP Proxy (IPTV)",
            what = "Маршрутизирует мультикаст-потоки ТВ провайдера в домашнюю сеть.",
            why = "Нужно только тем, у кого ТВ-приставка или VLC смотрят мультикаст-IPTV напрямую от провайдера.",
            recommend = "Если IPTV не пользуетесь — можно выключить.",
            caution = ""
        ),
        "no_ipv6" to MonitorHelpTopic(
            title = "Zapret2: Игнорировать IPv6",
            what = "Отключает обработку/утечку IPv6-пакетов в конфигурации Zapret2.",
            why = "Большинство провайдерских DPI и правил z2k настроены под IPv4.",
            recommend = "Включите при проблемах с двойным стеком IPv4+IPv6.",
            caution = ""
        ),
        "autohost" to MonitorHelpTopic(
            title = "Zapret2: Autohostlist",
            what = "Режим автоматического обучения: nfqws2 сам замечает зависший TLS-хендшейк и вносит домен в список обхода.",
            why = "Удобно для неизвестных заблокированных ресурсов, которых ещё нет в hostlist.",
            recommend = "Держите ВКЛЮЧЁННЫМ в паре с основным набором списков.",
            caution = ""
        ),
        "pol_ex" to MonitorHelpTopic(
            title = "Инвертировать политику (exclude)",
            what = "Определяет логику `POLICY_EXCLUDE`: 0 = обходить ТОЛЬКО указанную политику; 1 = обходить ВСЕХ, кроме указанной политики.",
            why = "В режиме Gor4akovNET обычно стоит 0 — так обход работает строго на Wi-Fi/клиентах Gor4akovNET, не трогая домашнюю сеть.",
            recommend = "Для изолированной сети оставьте ВЫКЛ (0).",
            caution = ""
        ),
        "band_steer" to MonitorHelpTopic(
            title = "Band Steering (Bridge0)",
            what = "Мягко подсказывает двухдиапазонным телефонам переходить на быстрый 5 ГГц вместо загруженного 2.4 ГГц.",
            why = "Увеличивает реальную скорость Wi-Fi в квартире.",
            recommend = "Рекомендуется ВКЛЮЧИТЬ при общем имени сети 2.4/5 ГГц.",
            caution = ""
        ),
        "ft" to MonitorHelpTopic(
            title = "Быстрый роуминг 802.11r (FT)",
            what = "Ускоряет переключение между точками 2.4/5 ГГц или Mesh-ретрансляторами до <20 мс.",
            why = "Звонки Telegram/WhatsApp не обрываются при ходьбе по квартире.",
            recommend = "Рекомендуется ВКЛЮЧИТЬ.",
            caution = "Единичные очень старые умные розетки (2015 г.) могут не подключаться при FT."
        ),
        "twt" to MonitorHelpTopic(
            title = "Target Wake Time (Wi-Fi 6 AX)",
            what = "Планирует пробуждение Wi-Fi-чипа смартфона по расписанию.",
            why = "Экономит до 15–20% заряда батареи современных смартфонов.",
            recommend = "Включайте на роутерах с Wi-Fi 6.",
            caution = ""
        ),
        "guest_up" to MonitorHelpTopic(
            title = "Гостевая сеть (AccessPoint1)",
            what = "Включает/отключает встроенную гостевую точку доступа (отличную от Gor4akovNET).",
            why = "Позволяет дать интернет гостям без доступа к вашим локальным ресурсам.",
            recommend = "Держите выключенной, если используете только основную и Gor4akovNET сети.",
            caution = ""
        )
    )

    val services = mapOf(
        "nfqws2" to ServiceGuide(
            key = "nfqws2",
            title = "Zapret2 DPI (nfqws2)",
            port = "NFQUEUE",
            summary = "Основной движок обхода блокировок на уровне пакетов.",
            details = "Перехватывает пакеты для выбранных доменов через iptables NFQUEUE и применяет стратегии десинхронизации без VPN и снижения пропускной способности.",
            tips = "Для изолированной работы используйте отдельную политику Gor4akovNET.",
            configPath = "/opt/zapret2/config"
        ),
        "gvsub" to ServiceGuide(
            key = "gvsub",
            title = "Autocircular / z2k-detect",
            port = "Фон",
            summary = "Автоматическая подстройка стратегий при сбоях соединения.",
            details = "Отслеживает неуспешные TLS/HTTP-сессии и переключает активную стратегию nfqws2 на рабочую.",
            tips = "Работает в фоне, не требует ручного вмешательства.",
            configPath = "/opt/zapret2/extra_strats"
        ),
        "adguard" to ServiceGuide(
            key = "adguard",
            title = "AdGuard Home",
            port = ":53 / :3000",
            summary = "Сетевой блокировщик рекламы, трекеров и DoH/DoT DNS.",
            details = "Обрабатывает DNS-запросы сети через opkg dns-override, фильтрует рекламу и кэширует ответы в памяти.",
            tips = "Проверяйте в журнале запросов, не блокируются ли нужные вам сервисы.",
            configPath = "/opt/etc/AdGuardHome/AdGuardHome.yaml"
        ),
        "tg_tunnel" to ServiceGuide(
            key = "tg_tunnel",
            title = "Telegram Прокси-туннель",
            port = ":1443",
            summary = "Локальный прокси-туннель z2k для стабильной работы звонков и медиа Telegram.",
            details = "Запускается при необходимости вспомогательными скриптами z2k.",
            tips = "Можно включать и отключать при проблемах с голосом или загрузкой файлов.",
            configPath = "/opt/etc/init.d/S98tg-tunnel"
        ),
        "http_tunnel" to ServiceGuide(
            key = "http_tunnel",
            title = "HTTP-туннель z2k",
            port = ":1444",
            summary = "Вспомогательный HTTP-прокси шлюз z2k.",
            details = "Используется скриптами z2k для загрузки списков и обновлений.",
            tips = "Обычно не требует ручной настройки.",
            configPath = "/opt/etc/init.d/S97z2k-http-tunnel"
        ),
        "warp" to ServiceGuide(
            key = "warp",
            title = "Cloudflare WARP (z2k-warpd)",
            port = "z2ktun0",
            summary = "Точечный туннель Cloudflare WARP внутри z2k.",
            details = "Маршрутизирует только трафик сервисов, блокируемых по IP, не затрагивая остальную сеть.",
            tips = "Держите выключенным, если вам не нужен обход геоблокировок по IP.",
            configPath = "/opt/zapret2/z2k-warp.sh"
        ),
        "z2k_panel" to ServiceGuide(
            key = "z2k_panel",
            title = "Веб-панель Zapret2 (lighttpd)",
            port = ":8088",
            summary = "Локальный веб-интерфейс и CGI API Zapret2.",
            details = "Обслуживает веб-панель z2k и API (/cgi-bin/api/*), включая автоподбор стратегий и управление WARP-играми.",
            tips = "Рекомендуется держать включённой для полного функционала подбора стратегий и WARP.",
            configPath = "/opt/zapret2/webpanel/lighttpd.conf"
        ),
        "scheduler" to ServiceGuide(
            key = "scheduler",
            title = "Планировщик z2k",
            port = "Фон",
            summary = "Автообновление списков блокировок и стратегий по расписанию.",
            details = "Периодически синхронизирует списки доменов без перезагрузки роутера.",
            tips = "Обновления запускаются в фоновом режиме в выбранный час.",
            configPath = "/opt/etc/init.d/S99z2k-scheduler"
        ),
        "cron" to ServiceGuide(
            key = "cron",
            title = "Планировщик Cron (S10cron)",
            port = "Фон",
            summary = "Системный демон периодических задач Entware.",
            details = "Выполняет скрипты по расписанию из /opt/var/spool/cron/crontabs/root (включая бэкапы, таймеры и перезапуски).",
            tips = "Управлять задачами можно прямо в таблице ниже.",
            configPath = "/opt/var/spool/cron/crontabs/root"
        ),
        "dropbear" to ServiceGuide(
            key = "dropbear",
            title = "Dropbear SSH (:222)",
            port = ":222",
            summary = "Лёгкий SSH-сервер Entware — главный канал связи Gor4akovNET.",
            details = "Обеспечивает защищённое удалённое управление, передачу файлов и бэкапов между телефоном и роутером.",
            tips = "Никогда не останавливайте Dropbear, если работаете с роутером с телефона.",
            configPath = "/opt/etc/dropbear"
        )
    )

    fun guideForInitId(id: String): ServiceGuide? {
        val l = id.lowercase()
        return when {
            l.contains("zapret") -> services["nfqws2"]
            l.contains("adguard") -> services["adguard"]
            l.contains("tg-tunnel") -> services["tg_tunnel"]
            l.contains("http-tunnel") -> services["http_tunnel"]
            l.contains("warp") -> services["warp"]
            l.contains("webpanel") || l.contains("lighttpd") -> services["z2k_panel"]
            l.contains("scheduler") -> services["scheduler"]
            l.contains("cron") -> services["cron"]
            l.contains("dropbear") -> services["dropbear"]
            else -> null
        }
    }
}
