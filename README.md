<div align="center">

# Gor4akovNET

**Обход DPI на Keenetic/Netcraze — прямо с телефона. Без ПК, без Termux, без root.**

[📥 Скачать APK](https://github.com/Garickbox/Gor4akovNET/releases/latest) · [🐛 Баги](https://github.com/Garickbox/Gor4akovNET/issues)

</div>

## Зачем

Провайдеры режут YouTube, Discord, Instagram через DPI. Gor4akovNET поднимает **2k + .ipk **вшиты в APK**
- **Ведёт за руку** — 5 шагов с проверкой каждого
- **Автобэкап** — конфиг сохраняется до любых изменений
- **Безопасный откат** — одной кнопкой

## Совместимость

**Прошивка:** KeeneticOS 3.7+ / Netcraze OS 5.x · `OPKG` + `Netfilter` + `WebDAV` · USB `ext4` или внутренняя память

### ✅ Рекомендуются (512 МБ RAM + USB)

| Модель | RAM | USB |
|---|---|---|
| **Giga X · KN-1012 / NC-1012** | 512 МБ | 2.0 |
| **Ultra · KN-1810** | 512 МБ | 2.0 + 3.0 |
| **Ultra SE · KN-1811 / NC-1811** · KN-2610** | 512 МБ | 3.0 |
| **Peak · KN-2710** | 512 МБ | 2.0 + 3.0 |

### 🟡 С оговорками (256 МБ RAM, swap обязателен)

| Модель | RAM |
|---|---|
| **Giga · KN-1010 / 1011** | 256 МБ |
| **Hopper · KN-3810** | 256 МБ |
| **Extra · KN-1710** | 256 МБ |
| **Hero 4G+ · KN-2311** | 256 МБ |

### 🔴 Не подходят (128 МБ RAM или без USB)

Speedster KN-3010 · Air KN-1610/1611 · Skipper 4G KN-2910 · Runner 4G KN-2210/2211=Ultra SE, NC-2610=Giant, NC-2710=Peak

## Быстрый старт

1. **Установи** APK → подключись к Wi-Fi роутера
2. **Вход** — IP роутера + пароль от веб-меню
3. **USB** — вставь флешку, отформатируй в ext4 через веб222** — введи пароль root (обычно `keenetic`)
6. **Установка** — Сетапы → «Установить в 1 клик» (60–90 сек)

После — подключись к Wi-Fi **`Gor4akovNET`** — обход работает.

## Что умеет

- **Подключение** — автоопределение IP, паспорт роутера, клиенты, Zapret2, AdGuard querylog, Wi-Fi, файлы по SSH, логи, бэкапы, автодоктор

## Безопасность

- Домашняя сеть и провайдер **не меняются**
- Бэкап до старта любых изменений
- Кнопка **«Откатить сеть Gor4akovNET»** в Сетапах

## Ссылки

- 📥 [Скачать APK](https://github.com/Garickbox/Gor4akovNET/releases/latest)
- 🐛 [Issues](https://github.com/Garickbox/Gor4akovNET/issues)
- 📚 [Карта RCI API](https://github.com/Garickbox/Gor4akovNET/tree/main/docs/rci)
- ⚙️ На базе [zapret2](https://github.com/bol-van/zapret)

Не связан с Keenetic Ltd. и Netcraze.
