# Логи бота в Kibana

Учебный стенд на ноутбуке: **Filebeat** читает JSON-лог бота `~/.combats/logs/bot*.json`,
**Elasticsearch** хранит записи, **Kibana** показывает их в браузере. Всё работает в Docker Desktop.

```
бот ──► ~/.combats/logs/bot.json ──► Filebeat ──► Elasticsearch :9200 ──► Kibana :5601
```

Поля записей (`level`, `game_id`, `battle`, `damage_hp`…) описаны в README проекта, раздел «Логи».

## Запуск

Нужен запущенный Docker Desktop и около 2 ГБ свободной памяти (Elasticsearch берёт 1 ГБ).
Из папки `elk`:

```bash
docker compose up -d
```

Первый запуск скачивает образы (около 2 ГБ) и поднимается 1–2 минуты. Проверка:

```bash
docker compose ps
```

У `elasticsearch` должно быть `healthy`, у `kibana` и `filebeat` — `running`.

Остановить, сохранив записи: `docker compose stop`. Удалить всё вместе с записями: `docker compose down -v`.

Логи из другой папки (например, скачанные с сервера) — переменной `COMBATS_LOGS` перед запуском:

```bash
$env:COMBATS_LOGS="C:\Users\sane4\Downloads\server-logs"; docker compose up -d --force-recreate filebeat
```

## Первая настройка Kibana

1. Откройте http://localhost:5601.
2. Меню (☰) → **Stack Management** → **Data Views** → **Create data view**.
3. Name: `Combats bot`, Index pattern: `filebeat-*`, Timestamp field: `@timestamp`. **Save**.
4. Меню → **Discover**, выберите data view `Combats bot`, справа вверху период, например **Last 7 days**.

В списке полей слева нажмите «+» у `level`, `mode`, `battle`, `message`, чтобы они стали колонками таблицы.

## Что потренировать

**Поиск в Discover (KQL):**

| Запрос | Что найдёт |
|---|---|
| `level: "ERROR" or level: "WARN"` | все ошибки и предупреждения |
| `game_id: "ab12cd34"` | все записи одной игры (id берите из любой записи) |
| `battle: 12 and game_id: "ab12cd34"` | всё, что было в 12-м круге игры |
| `message: *Kicked*` | когда бота выбрасывало со страницы заявок |
| `damage_hp >= 1000` | бои с уроном от 1000 HP |
| `logger_name: *BattlePage and level: "WARN"` | предупреждения экрана боя |

**Графики в Lens** (Меню → **Visualize Library** → **Create visualization** → **Lens**):

- урон по боям: ось X — `@timestamp`, ось Y — Median / Sum of `damage_hp`;
- опыт за день: Bar, X — `@timestamp` (интервал 1 день), Y — Sum of `experience`;
- ошибки по классам: Bar, разбивка по `logger_name`, фильтр `level: "ERROR"`.

Сохраните графики в один **Dashboard** — получится панель «как играл бот».

## Если записи не появляются

```bash
docker compose logs filebeat --tail 50
```

- `harvester started` с путём `/logs/bot.json` — файл читается;
- нет ни одного harvester — папка не смонтировалась или в ней нет `bot*.json`
  (JSON-лог пишут сборки бота с 24.10.2026);
- в Discover выбран слишком короткий период: записи старые.

Проверить, сколько записей уже в Elasticsearch:

```bash
curl.exe "http://localhost:9200/filebeat-*/_count"
```
