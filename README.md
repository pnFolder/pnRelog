# pnRelog

Developer: `inventoryType`.

Самостоятельный combat-tag и anti-relog плагин для Paper/Purpur/Folia.
Реализация повторяет функциональный охват GreatCombat, но не копирует его исходную структуру или классы.

## Возможности

- Боевые связи между игроками с независимыми таймерами, merge/join/continue и статистикой урона.
- Single-player combat через API и `/pnrelog tag <игрок> [секунды]`.
- Наказание за quit/kick, reconnect grace, persistent penalty debt и circuit breaker массового дисконнекта.
- Защита от телепортаций, элитр, команд и команд, направленных на игрока в бою.
- Предметные cooldown/prevention rules для consume, кликов, block break, shield, resurrect, bow, projectile, hit, riptide и fishing.
- 15 встроенных meta matchers, linked cooldowns, material cooldown и custom matchers через API.
- BossBar, встроенный Bukkit scoreboard, TAB и SternalBoard providers.
- Powerup providers для Bukkit, Essentials и CMI через API/reflection.
- WorldGuard, Towny и Lands region hooks без обязательных зависимостей.
- PlaceholderAPI expansion `%pnrelog_*%`, actions, local audit JSONL и optional webhook.
- bStats plugin id `33313`; локальный переключатель bStats в конфигурации отсутствует.
- Paper и Folia scheduler abstraction с entity/global scheduling.
- Русская/английская локализация, Legacy/MiniMessage renderer.
- Updater с hardcoded repository `pnFolder/pnRelog`; repository не настраивается в config.yml.

## Требования

- Paper/Purpur/Folia 1.16.5+
- Java 17+ для базового JAR.
- Java 21 для `pnRelog-1.0.1-java21.jar`.
- Java 25 для `pnRelog-1.0.1-java25.jar`.

PlaceholderAPI, TAB, SternalBoard, Essentials, CMI, WorldGuard, Towny, Lands и LangHelper являются optional.

## Установка Для Владельца Сервера

1. Положите `pnRelog-1.0.1.jar` в папку `plugins/`.
2. Запустите сервер один раз.
3. Откройте `plugins/pnRelog/config.yml`.
4. Измените только нужные значения и выполните `/pnrelog reload`.
5. Если меняли зависимости или тип платформы, сделайте полный restart сервера.

После запуска автоматически создаются:

- `config.yml` - основные настройки.
- `messages.yml` и `messages-en.yml` - русский и английский текст.
- `examples.yml` - готовые рабочие сценарии с пояснениями.
- `audit.jsonl` - локальная история решений anti-relog.
- `penalties.yml` - долговые наказания, переживающие restart.

`examples.yml` не применяется автоматически. Скопируйте нужный блок в `config.yml`, замените имена и выполните `/pnrelog reload`.

## bStats

pnRelog запускает bStats с plugin id `33313` и не показывает отдельную настройку отключения в своём `config.yml`.
Однако сервер принадлежит владельцу сервера: bStats сохраняет общий opt-out в `plugins/bStats/config.yml`, и pnRelog не обходит этот выбор.

Это ограничение платформы и корректное поведение bStats, а не настройка pnRelog.

## Как Работает Бой

1. Игрок A наносит подтверждённый урон игроку B.
2. pnRelog вызывает `CombatPreStartEvent`, затем transition event (`Start`, `Join`, `Merge` или `Continue`).
3. Если событие не отменено, A и B получают combat-link с таймером.
4. Каждый следующий успешный удар обновляет только нужную пару.
5. Команды, телепортации, элитры и предметы проверяются только пока есть активная связь.
6. После истечения всех связей вызывается `CombatEndEvent` и очищаются интерфейсы.

Важно: бой A-B и бой B-C имеют отдельные дедлайны. Удар по C не продлевает искусственно связь A-B.

## Anti-Relog По Шагам

При выходе игрока pnRelog делает следующее:

1. Сохраняет snapshot боя и причину disconnect.
2. Вызывает `PlayerLeaveInCombatEvent` или `PlayerKickInCombatEvent`.
3. На время `reconnect-grace-seconds` оставляет бой активным для остальных участников.
4. При своевременном возврате отменяет наказание и продолжает бой.
5. Если игрок не вернулся, вызывает cancellable `CombatEscapeEvent`.
6. Применяет kill, broadcast и console/opponent commands из penalty-конфига.
7. Если игрок офлайн, сохраняет наказание в `penalties.yml` и применяет его при следующем входе.

Массовый disconnect открывает circuit breaker и подавляет наказания всей обнаруженной волны, чтобы падение прокси не убило игроков.

## Готовые Настройки

### Классический Anti-Relog

```yaml
combat:
  duration-seconds: 30

logout:
  punish-quits: true
  punish-kicks: true
  reconnect-grace-seconds: 5
  penalty:
    kill: true
    broadcast: true
```

### Мягкое Наказание

Игрок не умирает, решение пишется в локальный audit и сервер выполняет команду:

```yaml
logout:
  penalty:
    kill: false
    broadcast: true
    console-commands:
      - "warn {player} Выход во время боя"
```

Доступные placeholders команд: `{player}`, `{uuid}`, `{opponent}`, `{opponent_uuid}`, `{damage_dealt}`, `{damage_taken}`.

### Команды В Бою

```yaml
restrictions:
  commands:
    enabled: true
    mode: WHITELIST
    entries:
      - msg
      - tell
      - r
      - reply
    filter-tab-complete: true
    targeting-prefixes:
      - heal
      - feed
      - tpa
```

`WHITELIST` оставляет только безопасные команды. `targeting-prefixes` отдельно блокирует команды, применяемые к игроку, который находится в бою.

### Предметы

Cooldown ограничивает повторное использование:

```yaml
items:
  cooldowns:
    golden_apple:
      enabled: true
      name: "Золотое яблоко"
      material: GOLDEN_APPLE
      interactions: [CONSUME]
      matchers: [MATERIAL]
      duration-seconds: 30
      material-cooldown: true
      linked: []
```

Prevention полностью запрещает действие:

```yaml
items:
  preventions:
    pearl:
      enabled: true
      name: "Эндер-жемчуг"
      material: ENDER_PEARL
      interactions: [RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK, PROJECTILE_LAUNCH]
      matchers: [MATERIAL]
      roles: [INTERACTED_ITEM]
      material-cooldown: true
```

Поддерживаемые interactions: `CONSUME`, `RIGHT_CLICK_AIR`, `RIGHT_CLICK_BLOCK`, `LEFT_CLICK_AIR`, `LEFT_CLICK_BLOCK`, `BLOCK_BREAK`, `RESURRECT`, `BOW_SHOOT`, `PROJECTILE_LAUNCH`, `PLAYER_HIT_ENTITY`, `PLAYER_HIT_PLAYER`, `PROJECTILE_HIT_PLAYER`, `SHIELD_BLOCK`, `RIPTIDE`, `FISHING`.

Для точной копии кастомного предмета возьмите его в main hand, выполните `/pnrelog copy` и вставьте результат в `item-base64`.

### Actions

Actions работают по событиям и не требуют написания Java-кода:

```yaml
actions:
  combat-start-attacker:
    - "[MESSAGE] &cВы начали бой с &f{0}"
    - "[SOUND] ENTITY_PLAYER_ATTACK_STRONG;1;1"
    - "[TITLE] &cБОЙ;Не выходите с сервера;5;30;5"
  combat-end:
    - "[MESSAGE] &aБой окончен"
```

Поддерживаются `MESSAGE`, `ACTIONBAR`, `SOUND`, `TITLE`, `PLAYER`, `CONSOLE`, `BROADCAST_MESSAGE`, `BROADCAST_ACTIONBAR`, `BROADCAST_SOUND`, `BROADCAST_TITLE`, `REMOVE_ITEMS`, `BACK_ITEMS`.

Формат параметров: `SOUND;громкость;тон`, `TITLE;заголовок;подзаголовок;fadeIn;stay;fadeOut`. Полный список событий и готовые блоки находятся в `examples.yml`.

## PlaceholderAPI

Включение внешних placeholders:

```yaml
use-placeholderapi: true
```

Встроенные placeholders:

- `%pnrelog_active%` - `true/false`.
- `%pnrelog_time%` - оставшееся время в секундах.
- `%pnrelog_time_formatted%` - форматированное время.
- `%pnrelog_opponents%` - список противников.
- `%pnrelog_opponent_count%` - количество противников.
- `%pnrelog_damage_dealt%`, `%pnrelog_damage_taken%`.
- `%pnrelog_hits_dealt%`, `%pnrelog_hits_taken%`.
- `%pnrelog_last_attacker%`.
- `%pnrelog_player_Steve_time%` - данные другого игрока.

## API Пример

Получить API из Bukkit ServicesManager и начать бой:

```java
RegisteredServiceProvider<PnRelogApi> registration = Bukkit.getServicesManager()
        .getRegistration(PnRelogApi.class);
PnRelogApi api = registration.getProvider();

api.tag(attacker, target, Duration.ofSeconds(30), TagCause.API);
api.grantLogoutPermit(player.getUniqueId(), Duration.ofSeconds(10), "velocity-transfer");
```

Отменить начало боя из другого плагина:

```java
@EventHandler
public void onCombatStart(CombatPreStartEvent event) {
    if (event.getAttacker().getWorld().getName().equalsIgnoreCase("event-arena")) {
        event.setCancelled(true);
    }
}
```

Отменить наказание за disconnect:

```java
@EventHandler
public void onEscape(CombatEscapeEvent event) {
    if (event.getDisconnectReason().contains("proxy")) {
        event.setCancelled(true);
    }
}
```

## Интеграции

`regions.enabled: true` и `provider: AUTO` подключают WorldGuard, Towny или Lands, если соответствующий плагин установлен. Для собственной логики используйте `RegionApi.setPolicy(...)`.

`powerups.provider: AUTO` выбирает CMI, Essentials или Bukkit. Внешний provider доступен через `PowerupApi`.

`display.scoreboard.provider: AUTO` выбирает SternalBoard, TAB или встроенный Bukkit scoreboard.

`updates.repository` отсутствует намеренно. Updater всегда проверяет `pnFolder/pnRelog`.

## Команды

- `/pnrelog status [игрок]`
- `/pnrelog stats`
- `/pnrelog tag <игрок> [секунды]`
- `/pnrelog tag <игрок1> <игрок2> [секунды]`
- `/pnrelog clear <игрок|all>`
- `/pnrelog permit <игрок> [секунды] [причина]`
- `/pnrelog history <игрок> [лимит]`
- `/pnrelog copy`
- `/pnrelog update [check|install]`
- `/pnrelog reload`

`/pnrelog update install` скачивает проверенный JAR в `plugins/update/`; работающий файл не перезаписывается.

## Audit И Webhook

Audit нужен администратору для ответа на вопрос «почему игрок получил или не получил наказание».
В `audit.jsonl` записываются только служебные решения pnRelog:

- `COMBAT_LINK_CREATED` и `COMBAT_ENDED`.
- `ESCAPE_PENDING`, `ESCAPE_FORGIVEN`, `ESCAPE_EXEMPT`.
- `ESCAPE_PUNISHED`, `ESCAPE_SUPPRESSED`, `CIRCUIT_OPENED`.
- `LOGOUT_PERMIT_GRANTED` и `CONFIG_RELOADED`.

Команда `/pnrelog history <игрок> [лимит]` показывает последние записи конкретного игрока.
Файл локальный и никуда не отправляется, пока webhook явно не включён:

```yaml
audit:
  enabled: true
  file: audit.jsonl
  max-file-size-megabytes: 16
  recent-records: 100
  webhook:
    enabled: false
    url: "https://discord.com/api/webhooks/CHANGE_ME"
```

При `webhook.enabled: true` наружу отправляются только наказание, exemption, suppression и circuit breaker. Обычные удары и содержимое инвентаря не отправляются.

## API

Все публичные сервисы доступны через Bukkit `ServicesManager`:

- `PnRelogApi`
- `ItemControlApi`
- `CombatDisplayApi`
- `PowerupApi`
- `RegionApi`
- `ActionApi`
- `PnScheduler`

Lifecycle events находятся в `ru.privatenull.pnrelog.api.event`:

`CombatPreStartEvent`, `CombatStartEvent`, `CombatJoinEvent`, `CombatMergeEvent`, `CombatContinueEvent`, `CombatTickEvent`, `CombatEndEvent`, `PlayerLeaveInCombatEvent`, `PlayerKickInCombatEvent`, `CombatEscapeEvent`, `ItemControlEvent`, `RestrictionDeniedEvent`.

## Сборка

```powershell
./gradlew.bat clean test shadowJar
```

Итоговый файл: `build/libs/pnRelog-1.0.1.jar`.

Сборка вариантов для Java 21 и Java 25:

```powershell
./gradlew.bat clean test releaseJar
```

В `release/` создаются:

- `pnRelog-1.0.1.jar` - базовый вариант с target Java 17.
- `pnRelog-1.0.1-java21.jar` - вариант для Java 21.
- `pnRelog-1.0.1-java25.jar` - вариант для Java 25.

Для копирования готового JAR в `release/`:

```powershell
./gradlew.bat clean test releaseJar
```

## Лицензия

MIT.
