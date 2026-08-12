## Кратко

Первый публичный выпуск pnRelog добавляет самостоятельную систему combat-tag и anti-relog для Paper, Purpur и Folia. В релиз входят наказания за выход, reconnect grace, предметные ограничения, интеграции, публичный API и подробные рабочие примеры конфигурации.

## Для игроков

- Добавлен боевой таймер с отображением в actionbar, bossbar и scoreboard.
- Добавлена защита от команд, телепортаций и полёта на элитрах во время боя.
- Добавлены cooldown и запреты для золотых яблок, жемчуга и любых настроенных предметов.
- Добавлены сообщения о начале, продолжении и завершении боя.
- Добавлена команда `/pnrelog status` для просмотра текущего боевого состояния.

## Для администраторов

- Добавлена команда `/pnrelog tag <игрок> [секунды]` для одиночного боевого режима.
- Добавлена команда `/pnrelog tag <игрок1> <игрок2> [секунды]` для ручного старта боя.
- Добавлена команда `/pnrelog clear <игрок|all>` для снятия боевого режима.
- Добавлена команда `/pnrelog permit <игрок> [секунды] [причина]` для разрешённого выхода или proxy-перехода.
- Добавлена команда `/pnrelog history <игрок> [лимит]` для просмотра локальной истории решений.
- Добавлена команда `/pnrelog copy` для получения Base64 предмета из основной руки.
- Добавлена команда `/pnrelog update [check|install]` для проверки и staged-установки обновлений.
- Добавлена команда `/pnrelog reload` для перечитывания настроек без перезапуска.
- Добавлены права `pnrelog.admin`, `pnrelog.status`, `pnrelog.bypass.tag`, `pnrelog.bypass.restrictions`, `pnrelog.bypass.logout`, `pnrelog.bypass.cooldowns`, `pnrelog.bypass.prevention` и `pnrelog.bypass.powerups`.
- Добавлены `config.yml`, `messages.yml`, `messages-en.yml` и `examples.yml` с пояснениями и готовыми сценариями.
- Добавлена локальная история `audit.jsonl` с ротацией файла и командой просмотра записей.
- Добавлен optional Discord-compatible webhook только для важных событий наказаний и circuit breaker.

## Для разработчиков

- Добавлен `PnRelogApi` через Bukkit `ServicesManager`.
- Добавлены события `CombatPreStartEvent`, `CombatStartEvent`, `CombatJoinEvent`, `CombatMergeEvent`, `CombatContinueEvent`, `CombatTickEvent` и `CombatEndEvent`.
- Добавлены события `PlayerLeaveInCombatEvent`, `PlayerKickInCombatEvent` и cancellable `CombatEscapeEvent`.
- Добавлены события `ItemControlEvent` и `RestrictionDeniedEvent`.
- Добавлены `ItemControlApi`, `ActionApi`, `CombatDisplayApi`, `PowerupApi`, `RegionApi` и `PnScheduler`.
- Добавлена PlaceholderAPI expansion с placeholders времени, состояния, противников, урона и последнего атакующего.
- Добавлены custom item meta matchers и custom item name provider.
- Добавлены заменяемые scoreboard, powerup и region providers.

## Добавлено

- Добавлена независимая модель combat graph с отдельным дедлайном для каждой пары игроков.
- Добавлен reconnect grace с persistent penalty debt в `penalties.yml`.
- Добавлен circuit breaker для массового disconnect.
- Добавлены атрибуция урона от melee, projectile, TNT, pets, area effects и End Crystal.
- Добавлены обработчики `CONSUME`, `RIGHT_CLICK_AIR`, `RIGHT_CLICK_BLOCK`, `LEFT_CLICK_AIR`, `LEFT_CLICK_BLOCK`, `BLOCK_BREAK`, `RESURRECT`, `BOW_SHOOT`, `PROJECTILE_LAUNCH`, `PLAYER_HIT_ENTITY`, `PLAYER_HIT_PLAYER`, `PROJECTILE_HIT_PLAYER`, `SHIELD_BLOCK`, `RIPTIDE` и `FISHING`.
- Добавлены 15 встроенных item meta matchers, linked cooldowns и material cooldown.
- Добавлены actions `MESSAGE`, `ACTIONBAR`, `SOUND`, `TITLE`, `PLAYER`, `CONSOLE`, broadcast actions, `REMOVE_ITEMS` и `BACK_ITEMS`.
- Добавлены Bukkit, TAB и SternalBoard scoreboard providers.
- Добавлены Bukkit, Essentials и CMI powerup providers.
- Добавлены WorldGuard, Towny и Lands hooks.
- Добавлена поддержка Legacy и MiniMessage форматов.
- Добавлена Folia-compatible global/entity scheduler abstraction.
- Добавлена анонимная статистика bStats с plugin id `33313`.

## Изменено

- Обновления проверяются только в hardcoded repository `pnFolder/pnRelog`; repository не задаётся в конфигурации.
- Updater сохраняет скачанный JAR в `plugins/update/` и не перезаписывает работающий файл.
- Конфигурация разделяет blacklist и whitelist политики команд.
- Конфигурация разделяет сообщения на русский и английский файлы через `locale`.

## Исправлено

- Исправлено искусственное продление связи A-B при бою B-C.
- Исправлено наказание при массовом disconnect прокси или сервера.
- Исправлена потеря наказания при restart до окончания grace period.
- Исправлена обработка kick и последующего quit как одного disconnect.
- Исправлена обработка directional damage и выбора последнего агрессора.
- Исправлена обработка кастомных предметов без ItemMeta.
- Исправлена совместимость scheduler-операций с Folia entity и global regions.

## Совместимость

- Добавлена поддержка Paper/Purpur/Folia 1.16.5+.
- Добавлена сборка с target release Java 17.
- Добавлены отдельные shaded-варианты для Java 21 и Java 25.
- Для сборки проекта используется Gradle 9.7 wrapper.
- PlaceholderAPI, TAB, SternalBoard, Essentials, CMI, WorldGuard, Towny, Lands и LangHelper остаются optional dependencies.

## Миграция

1. Помести `pnRelog-1.0.0.jar` в папку `plugins/`.
2. Запусти сервер один раз для создания конфигурации и `examples.yml`.
3. Проверь `config.yml` и выбери нужный сценарий из `examples.yml`.
4. Выполни `/pnrelog reload` после изменения конфигурации.
5. При включении webhook укажи собственный URL в `audit.webhook.url` и не публикуй его в репозитории.

## Важно

> По умолчанию наказание за quit отложено на `reconnect-grace-seconds`, а наказание за kick отключено. Это защищает игроков от ложных смертей при timeout, рестарте и сбоях proxy.

> Для реального proxy-перехода сначала выдавай одноразовый permit через API или `/pnrelog permit`.

> bStats уважает глобальный opt-out в `plugins/bStats/config.yml`. pnRelog не может и не должен обходить решение владельца сервера.
