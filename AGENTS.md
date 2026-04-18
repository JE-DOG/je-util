# AGENTS

## Назначение проекта

`je-util` — это CLI-проект для автоматизации и упрощения моих рутинных действий.

## Архитектурные принципы

Проект следует принципам:

- Clean Architecture
- KISS
- SOLID
- DRY

Ожидаемый стиль решений:

- простые и понятные реализации без лишней сложности;
- слабая связность между слоями;
- расширяемость без поломки существующего поведения;
- минимум дублирования кода.

## Технологический стек

- Kotlin/JVM (`kotlin("jvm")`).
- Gradle (Kotlin DSL).
- `kotlinx-cli` для CLI-парсинга аргументов и subcommands.
- `kotlinx-coroutines` для suspend/runBlocking сценариев в CLI.
- `org.jraf:klibnotion` для интеграции с Notion API.
- `kotlin("test")` (JUnit Platform) для тестов.
- Java Toolchain 20.

## Базовые утилиты

- [CliUtil.kt](src/main/kotlin/dag/je_dog/common/cli/CliUtil.kt)
  - чтение пользовательского ввода из консоли;
  - подтверждение действий через `askYesNo(...)`.
- [AppLogger.kt](src/main/kotlin/dag/je_dog/common/logger/AppLogger.kt)
  - единая точка логирования через `System.Logger`;
  - методы уровней `i(...)`, `w(...)`, `e(...)`.

## Дополнительные правила

- Обязательно логировать все неуспешные кейсы с описанием, что именно пошло не так.
- Тексты помощи (`help`, описания `subcommand`, описания аргументов/опций CLI) должны быть на английском языке.
- В слое `presentation` имя папки должно совпадать с именем CLI-команды.
  - Пример: команда `create_task_branch` -> папка `presentation/create_task_branch`.

## Dependency Injection

DI в проекте выполняется вручную через `object` с `lazy`-полями.

Правила именования:

- `<что провайдит>Component` для фичевых зависимостей (например, `TasksComponent`).
- `NetworkComponent` для общих/инфраструктурных зависимостей (например, сеть).

Рекомендации:

- компоненты отвечают только за сборку зависимостей;
- бизнес-логика не должна жить внутри компонентов;
- зависимости собираются явно, прозрачно и предсказуемо.
- DI-компоненты должны находиться в папке `di` (например, `presentation/<command>/di` или `di/<feature>`).

## Структура фич по слоям

Фичи размещаются по слоям в отдельных папках:

```text
src/main/kotlin/dag/je_dog/
  data/
    feature1/
    feature2/
    feature3/
  domain/
    feature1/
    feature2/
    feature3/
  presentation/
    feature1/
    feature2/
    feature3/
```

Примечание:

- имена фич должны быть одинаковыми во всех слоях (`data/domain/presentation`) для предсказуемой навигации.
