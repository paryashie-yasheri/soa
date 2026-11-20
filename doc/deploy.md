# Развёртывание и диагностика на Helios

Документ описывает текущий стенд Lab 3 (вариант 67352).
Дополнительное задание с CPU-heavy отчётами и S3 записано в конце README;
его реализация в эту конфигурацию ещё не входит.

## Аккаунты и сеть

SSH aliases `helios` и `helios2` ведут на один сервер
`helios.se.ifmo.ru:2222`. Сам сервер может сообщать hostname
`helios.cs.ifmo.ru`. Аккаунты имеют отдельные квоты памяти, процессов и диска,
но используют общий loopback и пространство портов.

| Alias | Аккаунт | Каталог | Компоненты |
| --- | --- | --- | --- |
| helios | s389491 | ~/soa-lab2 | WildFly, Consul, оба HAProxy, Spring №1 |
| helios2 | s408614 | ~/soa-lab3 | Spring №2 |

Все приведённые ниже команды запускаются на локальном компьютере из корня
репозитория, если явно не указано иное. При недоступности прямого соединения
используйте `proxy-launch ssh ...` и `proxy-launch rsync ...`.
Локальный SOCKS proxy — `socks5h://127.0.0.1:12334`.
Для curl можно задать `ALL_PROXY=socks5h://127.0.0.1:12334`;
OpenSSH сам эту переменную не использует.

## Порты и маршруты

| Порт | Назначение |
| --- | --- |
| 61815 HTTPS | Основной публичный HAProxy, UI и API |
| 61811 HTTP/TLS | Прежний публичный вход: HTTP 308 на HTTPS :61815, TLS passthrough в WildFly |
| 61810 HTTP | Redirect listener WildFly |
| 61812 loopback | Management WildFly |
| 61813 HTTPS loopback | WildFly, Organization Service и UI |
| 61814 HTTP loopback | Внутренний обработчик redirect старого HAProxy |
| 61816 HTTP loopback | Spring №1, s389491 |
| 61817 HTTP loopback | Spring №2, s408614 |
| 61818 HTTP loopback | Consul API |
| 61819 | Внутренний RPC Consul |
| 61820 / 61821 | Gossip LAN / WAN Consul |
| 61822 TCP/UDP loopback | Consul DNS |

Публичный UI: https://helios.se.ifmo.ru:61815/ui/.
Swagger UI: https://helios.se.ifmo.ru:61815/ui/docs/.

Публичный HAProxy отправляет `/orgdirectory/...` в пул Spring,
предварительно удалив префикс `/orgdirectory`.
Другие пути направляются в WildFly по HTTPS.
Поэтому прямой путь реплики — `/order/name/false`,
а путь через шлюз — `/orgdirectory/order/name/false`.

WildFly развёртывает `organization-service.ear` с JAX-RS WAR и EJB JAR,
а также `ui.war`. EJB использует PostgreSQL через datasource SoaDS:
сервер pg, база studs, схема s389491. Конкретные приватные параметры находятся
в `~/soa-lab2/.env`.

## Consul и динамический пул HAProxy

Каждая Spring JVM после старта регистрирует сервис `soa-orgdirectory` с ID
`soa-orgdirectory-61816` или `soa-orgdirectory-61817`.
Consul проверяет HTTP `/health` каждые 5 секунд с timeout 2 секунды.
При штатной остановке JVM удаляет свою регистрацию.
При аварийной остановке запись может остаться, но её check станет critical.

Consul DNS настроен с `only_passing=true`.
HAProxy запрашивает SRV `_soa-orgdirectory._tcp.service.consul`
у `127.0.0.1:61822` и получает адреса вместе с портами.
`server-template` выделяет до 10 слотов и обновляет пул без перезапуска.
Round-robin выполняется между найденными репликами.
Имена `orgdirectory1…10` в HAProxy — динамические слоты;
номер слота не привязан навсегда к аккаунту.

Organization Service регистрируется отдельно как `soa-organizations`.
Consul проверяет его HTTPS `/health` каждые 10 секунд.
Spring использует Health API с `passing=true`, чтобы найти WildFly.
При успешном ответе Consul без здоровых инстансов возвращается 503.
При недоступности Consul используется настроенный fallback URL.

Health endpoints проверяют доступность приложения, без запроса к PostgreSQL.
Переключение занимает время health check и обновления DNS.
Запрос в момент отказа может завершиться ошибкой.
Когда здоровых Spring-реплик нет, шлюз возвращает 503.

Consul работает одним агентом в dev mode. Его реестр не переживает перезапуск
процесса. После перезапуска Consul перезапустите WildFly и обе Spring-реплики
для повторной регистрации.

## Полный деплой

Локально нужны Java 17+, Bun, rsync и SSH-доступ к обоим аккаунтам.
На сервере нужны Java, rsync, Consul и HAProxy с поддержкой OpenSSL.

```bash
./deploy-wildfly.sh
# При необходимости через прокси:
proxy-launch ./deploy-wildfly.sh
```

Скрипт собирает EAR, Spring JAR и UI, передаёт файлы через rsync,
запускает основную инфраструктуру на helios, вызывает
`deploy-second-caller.sh` для helios2, затем запускает первую реплику и шлюз.

Переопределения: `HOST`, `REMOTE_DIR`, `SECOND_HOST`, `SECOND_DIR`.
По умолчанию используются аккаунты и каталоги из таблицы выше.

Основной `.env` — приватный Bash-файл, который имеет приоритет над
переменными запуска setup-wildfly.sh. `consul-url` хранит выбранный адрес
агента. Скрипт второй реплики копирует разрешённые настройки подключения
и truststore в её приватный каталог. Для изменения настроек повторно запустите
деплой второй реплики.

Секреты, TLS private keys, `.env`, логи и hs_err-файлы не коммитить.
Для второй реплики достаточно truststore; серверный private key ей не нужен.

## Быстрая проверка

```bash
ssh helios '
for url in \
  https://127.0.0.1:61813/health \
  https://127.0.0.1:61813/organizations \
  http://127.0.0.1:61816/health \
  http://127.0.0.1:61817/health \
  https://127.0.0.1:61815/orgdirectory/order/name/false \
  https://127.0.0.1:61815/ui/
do
  curl -ksS --max-time 10 -o /dev/null -w "%{http_code} %{url_effective}\n" "$url"
done'
```

Ожидаются 200. `/organizations` и directory-запрос дополнительно проверяют
путь до бизнес-логики и БД. `-k` здесь используется для CLI-проверки
доступности с самоподписанным сертификатом. Это не проверка доверия браузера.
Не отключайте проверку сертификатов в браузере и не устанавливайте сертификат
проекта в глобальное хранилище ОС.

Проверка redirect с сохранением path/query:

```bash
ssh helios 'curl -sS -D - -o /dev/null "http://127.0.0.1:61811/ui/?demo=1"'
```

Ожидается 308 с Location `https://127.0.0.1:61815/ui/?demo=1`.

## Логи и демонстрация round-robin

В отдельных терминалах:

```bash
ssh helios 'tail -f ~/soa-lab2/logs/access-orgdirectory1.*.log'
ssh helios2 'tail -f ~/soa-lab3/logs/access-orgdirectory2.*.log'
ssh helios 'tail -f ~/soa-lab2/logs/haproxy.log'
```

Генерация запросов через шлюз:

```bash
ssh helios '
for i in $(seq 1 20); do
  curl -fkSs -o /dev/null -w "HTTP %{http_code}\n" \
    "https://127.0.0.1:61815/orgdirectory/order/name/false?demo=$i"
  sleep 1
done'
```

Параметр demo помогает сопоставить запросы в access logs.
Периодические `GET /health HTTP/1.1` — проверки Consul.
Старые строки `HTTP/1.0` могут остаться от прежних проверок HAProxy.

Логи запуска и ошибок:

```bash
ssh helios 'tail -f ~/soa-lab2/logs/wildfly.log'
ssh helios 'tail -f ~/soa-lab2/logs/consul.log'
ssh helios 'tail -f ~/soa-lab2/logs/orgdirectory1.log'
ssh helios2 'tail -f ~/soa-lab3/logs/orgdirectory2.log'
```

## Проверка реестра, DNS, процессов и портов

```bash
ssh helios 'curl -fsS http://127.0.0.1:61818/v1/status/leader'
ssh helios 'curl -fsS "http://127.0.0.1:61818/v1/health/service/soa-orgdirectory?passing=true"'
ssh helios 'curl -fsS "http://127.0.0.1:61818/v1/health/service/soa-organizations?passing=true"'
ssh helios 'drill -p 61822 @127.0.0.1 _soa-orgdirectory._tcp.service.consul SRV'
ssh helios 'sockstat -l | grep -E "6181[0-9]|6182[0-2]"'
ssh helios 'ps -U s389491 -o pid,ppid,rss,vsz,nlwp,etime,command'
ssh helios2 'ps -U s408614 -o pid,ppid,rss,vsz,nlwp,etime,command'
```

RSS/VSZ выводятся в КиБ. NLWP — число потоков. В FreeBSD для выбора
пользователя у ps применяется `-U`.
Командная строка JVM может содержать пароль truststore; перед публикацией
диагностики скройте секреты.

Пустой healthy-список при работающем Spring требует проверки всех checks:

```bash
ssh helios 'curl -fsS http://127.0.0.1:61818/v1/health/service/soa-orgdirectory'
```

Смотрите `Status` и `Output`, включая maintenance check.

## Исключение и восстановление реплики

Предпочтительный способ демонстрации — maintenance, без остановки JVM:

```bash
ssh helios 'curl -fsS -X PUT "http://127.0.0.1:61818/v1/agent/service/maintenance/soa-orgdirectory-61817?enable=true"'
# После демонстрации обязательно вернуть:
ssh helios 'curl -fsS -X PUT "http://127.0.0.1:61818/v1/agent/service/maintenance/soa-orgdirectory-61817?enable=false"'
```

Для первой реплики замените 61817 на 61816.
В maintenance endpoint реплика продолжает отвечать напрямую, но исчезает
из DNS и backend-пула после обновления discovery.

Для остановки второй JVM сначала сверяйте PID и команду:

```bash
ssh helios2 'pid=$(cat ~/soa-lab3/run/orgdirectory2.pid); ps -p "$pid" -o pid,command'
ssh helios2 'kill "$(cat ~/soa-lab3/run/orgdirectory2.pid)"'
ssh helios2 'cd ~/soa-lab3 && bash run-lab3-caller.sh'
```

Повторный запуск на основном аккаунте перезапускает его реплику и шлюз:

```bash
ssh helios 'cd ~/soa-lab2 && bash run-lab3-caller.sh'
```

Обновить вторую реплику собранным локальным JAR и актуальными настройками:

```bash
./gradlew :spring-orgdirectory-service:bootJar
./deploy-second-caller.sh
```

Перезапустить WildFly с применением конфигурации:

```bash
ssh helios 'cd ~/soa-lab2 && bash setup-wildfly.sh'
```

Это создаёт краткий перерыв в Organization API. После рестарта Consul
потребуется также перезапуск обеих Spring-реплик.

## Типичные проблемы

| Симптом | Что проверить и как исправить |
| --- | --- |
| SSH timeout | Проверить proxy-launch и SOCKS на 127.0.0.1:12334, aliases, порт SSH 2222 |
| Disk quota exceeded | `du -k -d 1 "$HOME" 2>/dev/null \| sort -nr` на нужном аккаунте. Consul binary занимает около 176 МиБ. Удалять только подтверждённо ненужные файлы; БД и приватную конфигурацию сохранять |
| JVM просит десятки ГиБ heap | Убедиться, что запускается актуальный скрипт с ограничениями JVM |
| Failed to reserve metaspace / compressed class space | Проверить все JVM и VSZ аккаунта, настройки памяти и отдельный запуск второй реплики под helios2 |
| pthread_create EAGAIN | Лимит потоков/процессов или памяти. Проверить NLWP и лишние процессы, ActiveProcessorCount=2 |
| Consul bind 8300: operation not permitted | Использовать настроенные порты 61818–61822, а не стандартные порты Consul |
| Consul failed to become ready | Читать consul.log. Проверить бинарник, занятость портов и доступность API |
| API Consul работает, SRV не отвечает | Проверить DNS listener :61822 и only_passing. Старый уже запущенный агент с выключенным DNS setup автоматически не переконфигурирует |
| HAProxy unknown keyword ssl | Установлен бинарник без TLS. `~/.local/bin/haproxy -vv` должен показывать +SSL. Пересобрать через install-haproxy.sh с USE_OPENSSL=1 |
| Address already in use на 61815 | Сверить sockstat, ps и run/haproxy.pid. Старый живой процесс может отличаться от PID в файле после неудачного запуска |
| /health отвечает, gateway даёт 503 | Проверить регистрацию Spring, passing checks, SRV-ответ и DNS resolver HAProxy; затем его лог |
| Spring отвечает 503 для directory | Нет passing Organization Service. Проверить его Consul check и HTTPS :61813/health |
| Spring отвечает 502/504 | Проверить адрес WildFly, TLS truststore, лог WildFly, БД и timeout исходящего запроса |
| WFLYCTL0216 remoting connector not found / org.wildfly.ejb.remote missing | Старый Lab 2 удалял remote EJB resources. Использовать актуальный configure-wildfly.sh, восстанавливающий HTTPS remoting и ejb3/service=remote |
| WildFly started with errors | Искать первую причину ERROR / Caused by в wildfly.log. Успешная проверка datasource сама по себе не подтверждает развёртывание EAR |
| Connection refused первые секунды | JVM ещё стартует. Дождаться завершения скрипта; при длительном ожидании читать лог соответствующей реплики |
| HTTPS browser warning | Сертификат самоподписанный. Проверить адрес и сертификат; CLI -k не доказывает доверие браузера |

Основные лимиты по умолчанию: WildFly heap 384 МиБ, Spring heap 256 МиБ
на реплику, ActiveProcessorCount=2 и SerialGC.
Полное потребление JVM больше heap: есть metaspace, code cache,
стеки потоков и нативная память.
WildFly можно настроить через SOA_SERVER_JAVA_OPTS,
Spring heap — через SOA_CALLER_MAX_HEAP.

## Безопасное восстановление

1. Определить проблемный слой по прямым /health, API и логам.
2. Проверить процессы нужного аккаунта и владельца занятого порта.
3. Перед kill сопоставить PID с командой. PID-файлы могут устареть.
4. Проверить HAProxy-конфигурацию до замены процесса:
   `~/.local/bin/haproxy -c -f ~/soa-lab2/run/haproxy.cfg`.
5. Перезапустить затронутый компонент и дождаться passing в Consul.
6. Проверить обе реплики, SRV, gateway directory и UI.

Настройка HAProxy со списком сервисов обновляется через DNS.
Ручные изменения run/haproxy.cfg перезаписываются при следующем запуске.
Постоянные изменения вносить в haproxy/helios.cfg и скрипты репозитория.
