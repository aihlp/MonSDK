# MonSDK: подготовка к роли шаблона и стабилизация AI

Статус: **не готов к публикации как шаблон**. Проект является работающим reference-приложением для мониторинга давления, но текущая ветка содержит незавершённую интеграцию локального AI и подтверждённую регрессию сохранения записей.

## Контекст

Цель MonSDK — давать новое Android-приложение мониторинга через замену program-модуля, а не копирование экранов и бизнес-логики. Базовая цепочка данных должна быть общей:

`форма → ingestion → нормализация → Room → analytics → история/графики/отчёт → AI (опционально)`.

AI должен быть дополнительным локальным слоем. Он не может блокировать запись данных, базовую аналитику, навигацию или фоновую синхронизацию.

## Что готово и подтверждено

- Compose UI для формы, истории, статистики, напоминаний, CSV и конфигурируемых program-модулей.
- Room-хранилище, ingestion/normalization pipeline, базовая аналитика, Health Connect и конфигурационный аудит.
- Сборка debug APK под `arm64-v8a`; native llama.cpp runtime и `llama_jni` включены в APK.
- Pixel 8: APK устанавливается, native runtime загружается, 16-KB page alignment проверен.
- Модель Qwen 0.5B скачивается с Hugging Face, файл проверяется по размеру и SHA-256, а `.part` продолжает скачивание через HTTP Range.
- Unit-тесты и существующие четыре instrumentation-теста запускаются на Pixel 8.

## Подтверждённые блокеры

| Приоритет | Проблема | Доказательство | Требуемый результат |
|---|---|---|---|
| P0 | Сохранение записей | После десяти действий сохранения в UI в статистике остаётся одна запись | Десять уникальных записей присутствуют в Room, истории и analytics input |
| P0 | Native AI crash | Tombstone Pixel 8: `SIGABRT` в `llama_context::decode` | AI-запрос не убивает процесс; ошибка модели возвращается в UI/Worker |
| P0 | Зависший AI Worker | После crash исторический `RUNNING` WorkManager блокирует AI controls | Перезапуск приложения освобождает UI, а новая manual-задача заменяет старую |
| P1 | Установка модели после interruption | GGUF может быть валиден, но статус БД оставаться `ERROR` | При старте валидный файл становится `READY` и доступен кнопкой «Использовать» |
| P1 | Контракт шаблона устарел | Документация сообщает, что native runtime не включён | README и guides отражают выбранную модель поставки AI |

## Целевая архитектура

### Границы слоёв

- `program/*`: только вертикальная конфигурация — поля, теги, метрики, правила, mapping, визуальный стиль, prompt context.
- `core/domain`, `core/storage`, `core/analytics`, `core/normalization`: не импортируют `app`, конкретный program или Android UI.
- `app/*`: Compose, DI, Android permissions, notifications, навигация.
- `core/ai`: контракты AI, prompt, model registry, worker orchestration. Native adapter изолирован за `AiEngine`.
- `app/src/main/cpp`: один implementation adapter llama.cpp. Он не должен протекать в program/config слой.

### AI pipeline

`UI action → unique foreground Worker → snapshot analytics → AiEngine → validated JSON → repository transaction → UI state`.

Обязательные правила:

1. Любая ошибка native/runtime/JSON переводится в `Unavailable` без process abort.
2. Worker публикует конечное состояние `success/failure/cancelled`; UI не делает вывод о занятости по устаревшей записи.
3. Загрузка модели имеет одну уникальную задачу на модель, notification, процент, количество байт, resume и integrity check.
4. Модель сначала проверяется и переводится в `READY`, затем может быть выбрана. Генерация никогда не запускается для `ERROR`, `DOWNLOADING` или неизвестного файла.
5. Промпт ограничен по tokens/context; JNI декодирует блоками или гарантирует `batch.n_tokens <= n_batch`.

## План доработки

### Этап 1 — остановить регрессии данных (P0)

- [ ] Воспроизвести сохранение десяти записей в чистой и обновлённой базе.
- [ ] Добавить логирование/результат `ingestionManager.ingestData` и отображение ошибки сохранения вместо безусловного snackbar.
- [ ] Проверить required metrics, timestamp/slot resolution и уникальность ID на каждой операции.
- [ ] Добавить instrumentation test: 10 ручных записей с разными давлением, пульсом, medication status и тегами; assert Room count=10.
- [ ] Assert: history=10, analytics `recordCount=10`, статистика использует все десять записей.

### Этап 2 — стабилизировать local AI (P0)

- [ ] Применить и проверить исправление batch/context в JNI на реальном длинном prompt.
- [ ] Добавить native smoke test с установленным Qwen 0.5B: загрузка модели, одна генерация с JSON grammar, JSON parse.
- [ ] Ограничить prompt и output token budget; явно сообщать о превышении лимита.
- [ ] Проверить process survival: AI action, сворачивание приложения, возврат, завершение Worker.
- [ ] Проверить негативные сценарии: отсутствующая модель, повреждённый GGUF, некорректная grammar, cancellation.

### Этап 3 — загрузка моделей и UX (P1)

- [ ] Background/foreground upload/download test: начать, свернуть приложение, дождаться роста `.part`.
- [ ] Resume test: прервать после 5–10%, перезапустить app/Worker, подтвердить `Range` и рост существующего файла.
- [ ] Recovery test: валидный GGUF + `ERROR` в БД → после старта `READY`.
- [ ] UX: у каждой модели показать состояние `Available/Downloading n%/Ready/Error`; не использовать кликабельные декоративные статусы.

### Этап 4 — сделать проект нейтральным шаблоном (P1/P2)

- [ ] Убрать `blood-pressure-monitor` из общих AI persistence contracts.
- [ ] Убрать `ActiveProgramModuleProvider` из Worker; передавать program identity через DI/worker input.
- [ ] Разделить reusable core и Android app shell; core не должен зависеть от `MainActivity`, `R` и app string provider.
- [ ] Обновить README и migration guides для выбранного варианта local AI runtime.
- [ ] Создать второй минимальный program fixture и прогнать полный config/permission/AI test suite на обоих program IDs.
- [ ] Удалить deprecated AGP compatibility flags после обновления зависимостей.

## Матрица приёмочных сценариев

| ID | Сценарий | Шаги | Ожидаемый результат |
|---|---|---|---|
| D1 | 10 ручных записей | Создать 10 записей с разными САД/ДАД/пульсом, `TAKEN`/`MISSED`, тегами сон/стресс/активность | Room, history и analytics содержат 10 записей |
| D2 | Проверка аналитики | Открыть статистику после D1 | Count=10; средние и finding изменяются при изменении входных значений |
| D3 | Перезапуск | Закрыть и открыть приложение после D1 | Все 10 записей остаются, дубликаты не появляются |
| A1 | Model download | Скачать Qwen 0.5B | notification и UI показывают bytes/percent; SHA-256 проходит; `READY` |
| A2 | Resume | Прервать на 5–10%, запустить снова | запрос Range, размер `.part` растёт, загрузка не начинается с нуля |
| A3 | AI foreground | Запустить AI analysis, свернуть и вернуть app | Worker продолжает/корректно завершает работу; UI не блокируется навсегда |
| A4 | AI output | Запустить анализ на D1 | Приложение не падает; появляется валидный результат либо безопасный `Unavailable` |
| A5 | Invalid model | Повредить/удалить GGUF | `ERROR` с понятным текстом, без crash; повторная загрузка доступна |
| T1 | Новый program | Запустить fixture с иным program ID | Нет pressure-specific AI data/permissions/labels |

## Definition of Done

Задача завершена только когда:

- D1–D3 и A1–A5 выполнены на Pixel 8 и приложены результаты (logcat без crash, screenshots/XML, значения счётчиков);
- `testDebugUnitTest`, `connectedDebugAndroidTest`, `assembleDebug` и `lintDebug` проходят в чистой среде;
- AI Worker не вызывает native abort и не оставляет UI permanently busy;
- два program fixture проходят config and persistence tests;
- документация шаблона соответствует фактической поставке runtime и модели.
