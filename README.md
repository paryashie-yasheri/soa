# soa

Вариант 67303

Внимание! У разных вариантов разный текст задания!

Разработать спецификацию в формате OpenAPI для набора веб-сервисов, реализующего следующую функциональность:

Первый веб-сервис должен осуществлять управление коллекцией объектов. В коллекции необходимо хранить объекты класса Organization, описание которого приведено ниже:

public class Organization {
    private int id; //Значение поля должно быть больше 0, Значение этого поля должно быть уникальным, Значение этого поля должно генерироваться автоматически
    private String name; //Поле не может быть null, Строка не может быть пустой
    private Coordinates coordinates; //Поле не может быть null
    private java.time.LocalDate creationDate; //Поле не может быть null, Значение этого поля должно генерироваться автоматически
    private Integer annualTurnover; //Поле может быть null, Значение поля должно быть больше 0
    private String fullName; //Поле может быть null
    private OrganizationType type; //Поле не может быть null
    private Address postalAddress; //Поле не может быть null
}

public class Coordinates {
    private float x;
    private long y;
}

public class Address {
    private String street; //Длина строки не должна быть больше 158, Поле не может быть null
}

public enum OrganizationType {
    COMMERCIAL,
    TRUST,
    PRIVATE_LIMITED_COMPANY,
    OPEN_JOINT_STOCK_COMPANY;
}

Веб-сервис должен удовлетворять следующим требованиям:

    API, реализуемый сервисом, должен соответствовать рекомендациям подхода RESTful.
    Необходимо реализовать следующий базовый набор операций с объектами коллекции: добавление нового элемента, получение элемента по ИД, обновление элемента, удаление элемента, получение массива элементов.
    Операция, выполняемая над объектом коллекции, должна определяться методом HTTP-запроса.
    Операция получения массива элементов должна поддерживать возможность сортировки и фильтрации по любой комбинации полей класса, а также возможность постраничного вывода результатов выборки с указанием размера и порядкового номера выводимой страницы.
    Все параметры, необходимые для выполнения операции, должны передаваться в URL запроса.
    Информация об объектах коллекции должна передаваться в формате xml.
    В случае передачи сервису данных, нарушающих заданные на уровне класса ограничения целостности, сервис должен возвращать код ответа http, соответствующий произошедшей ошибке.

Помимо базового набора, веб-сервис должен поддерживать следующие операции над объектами коллекции:

    Сгруппировать объекты по значению поля name, вернуть количество элементов в каждой группе.
    Вернуть количество объектов, значение поля annualTurnover которых меньше заданного.
    Вернуть массив уникальных значений поля annualTurnover по всем объектам.

Эти операции должны размещаться на отдельных URL.

Второй веб-сервис должен располагаться на URL /orgdirectory, и реализовывать ряд дополнительных операций, связанных с вызовом API первого сервиса:

    /filter/employees/{min-employees-count}/{max-employees-count} : отфильтровать организации по количеству сотрудников
    /order/{param-name}/{desc} : вывести список организаций отсортированных по заданному полю в порядке возрастания / убывания

Эти операции также должны размещаться на отдельных URL.

Для разработанной спецификации необходимо сгенерировать интерактивную веб-документацию с помощью Swagger UI. Документация должна содержать описание всех REST API обоих сервисов с текстовым описанием функциональности каждой операции. Созданную веб-документацию необходимо развернуть на сервере helios.
Вопросы к защите лабораторной работы №1

    Подходы к проектированию приложений. "Монолитная" Обновили главную ссылку на подписку, Если ранее она у вас не добавлялась - попробуйте сейчаси сервис-ориентированная архитектура.
    Понятие сервиса. Общие свойства сервисов.
    Основные принципы SOA. Подходы к реализации SOA, стандарты и протоколы.
    Общие принципы построения и элементы сервис-ориентированных систем.
    Понятие веб-сервиса. Определение, особенности, отличия от веб-приложений.
    Категоризация веб-сервисов. RESTful и SOAP. Сходства и отличия, области применения.
    RESTful веб-сервисы. Особенности подхода. Понятия ресурса, URI и полезной нагрузки (payload).
    Виды RESTful-сервисов. Интерпретация методов HTTP в RESTful.
    Правила именования ресурсов в RESTful сервисах.
    Спецификация RESTful-сервисов. Стандарт OpenAPI.
    Автодокументирование RESTful-сервисов. Swagger Editor, Swagger UI (и Swagger Codegen).
    Архитектурный принцип HATEOAS.

# Лабораторная работа №2

Вариант 67321

Внимание! У разных вариантов разный текст задания!

На основе разработанной в рамках лабораторной работы №1 спецификации реализовать два веб-сервиса и использующее их API клиентское приложение.

## Требования к реализации и развёртыванию сервисов

Первый («вызываемый») веб-сервис должен быть реализован на фреймворке JAX-RS и развёрнут в окружении под управлением сервера приложений WildFly.

Второй веб-сервис должен быть реализован на фреймворке JAX-RS, развёрнут в окружении под управлением ещё одного экземпляра сервера приложений WildFly и вызывать REST API первого сервиса.

Для обоих сервисов необходимо реализовать все функции, задокументированные в API, в строгом соответствии со спецификацией. Доступ к обоим сервисам должен осуществляться по протоколу HTTPS с самоподписанным сертификатом сервера. Доступ к сервисам по нешифрованному протоколу HTTP должен быть запрещён.

## Требования к клиентскому приложению

Клиентское приложение может быть написано на любом веб-фреймворке, который можно запустить на сервере helios. Приложение должно обеспечивать полный набор возможностей, предоставляемых API обоих сервисов, включая сортировку, фильтрацию и постраничный вывод элементов коллекции.

Приложение должно преобразовывать передаваемые сервисами данные в человеко-читаемый вид: например, параграф текста или таблицу. Клиентское приложение должно информировать пользователя об ошибках, возникающих на стороне сервисов, в частности, о передаче сервису невалидных данных.

Оба веб-сервиса и клиентское приложение должны быть развёрнуты на сервере helios.

## Дополнительные замечания по спецификации Lab 1

Модель и API должны соответствовать спецификации лабораторной работы №1. У `Organization` в ней нет поля количества сотрудников, а операция второго сервиса `/orgdirectory/filter/employees/{min-employees-count}/{max-employees-count}` использует это число для фильтрации. Реализацию этой операции нужно согласовать со спецификацией Lab 1. Изменения модели `Organization` требуют обновления спецификации.

# WildFly deployment

`./deploy-wildfly.sh` builds and deploys both JAX-RS WARs to Helios. All runtime
files, scripts, TLS material, logs and configuration live in `~/soa-lab2`.
Swagger UI is packaged in `ui.war`; deployment does not write to `public_html`.
The dropdown selects Organization Service or OrgDirectory Service. WildFly serves both OpenAPI documents at `/openapi/organizations` and
`/openapi/orgdirectory`. The SmallRye OpenAPI subsystem serves the explicit XML contracts packaged in each
service WAR. Annotation scanning is disabled because the handlers accept raw XML
strings, which otherwise overwrite object schemas with `type: string`.
Both specifications use relative server URLs, so “Try it out” uses the current host and port, including
SSH tunnels. `./gradlew uiWar` builds Swagger UI, which fetches the live documents. The old custom frontend is removed.

- UI: `https://se.ifmo.ru:61811/ui/`
- Organizations: `https://se.ifmo.ru:61811/organizations`
- Directory: `https://se.ifmo.ru:61811/orgdirectory/order/name/false`

The HTTPS certificate is self-signed. The application HTTP listener is removed;
management listens only on localhost (port 61812).
Both services currently share one WildFly instance; the lab specification's
requirement for two separate instances is not implemented by this deployment.

## Database configuration

`~/soa-lab2/.env` is a private Bash assignment file (mode 600) loaded by
`setup-wildfly.sh` on every start. See `wildfly/.env.example` for supported values.
On the first run only, if credentials are absent, the script reads the existing
`~/.pgpass` and saves the resulting connection settings to `.env` inside the bundle.
Later deployments preserve that file. Its values take precedence over environment
variables; when no file exists, exported `SOA_DB_USER`, `SOA_DB_PASSWORD` and
`SOA_JDBC_URL` can supply the initial settings instead.
WildFly's datasource configuration references environment variables rather than
embedding the database password in XML. Do not commit `.env`.

Hibernate schema generation is disabled. Liquibase only creates missing application
tables; existing tables are marked as already migrated. Deployment performs no
DROP, TRUNCATE, data reset, or destructive API tests.
The current entity mappings and changelogs use the existing `s389491` schema.

Restart or reconfigure on Helios after editing `.env`:

```bash
cd ~/soa-lab2
./setup-wildfly.sh
```

Logs: `~/soa-lab2/logs/wildfly.log`. Startup fails if either API or the UI fails its
HTTPS check. The server uses `nohup` with detached stdin; if the host stops user
processes, run the startup script again.

Verify without modifying data:

```bash
curl -fk https://se.ifmo.ru:61811/organizations
curl -fk https://se.ifmo.ru:61811/orgdirectory/order/name/false
curl -fk https://se.ifmo.ru:61811/ui/
```

If direct access to the port is unavailable, forward it through SSH:

```bash
ssh -N -L 61811:localhost:61811 helios
# In another terminal:
curl -fk https://localhost:61811/organizations
```

## Local verification

```bash
./gradlew e2eTest
```

This uses the isolated PostgreSQL/WildFly stack in `wildfly/compose.yaml`.
The local test database is separate from Helios.
