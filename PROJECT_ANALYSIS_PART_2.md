# ДЕТАЛЬНЫЙ АНАЛИЗ ПРОЕКТА ULTRAMINE CORE - ЧАСТЬ 2
## АНАЛИЗ ПАКЕТОВ ORG.ULTRAMINE

---

## 8. КЛЮЧЕВОЙ КОД ULTRAMINE (org.ultramine.*)

### 8.1 Обзор структуры Ultramine

Ultramine - это набор расширений для Minecraft Server 1.7.10, который добавляет:
- Продвинутую систему команд
- Встроенную экономику
- Систему разрешений
- Dependency Injection контейнер
- Планировщик задач (Cron-like)
- Оптимизации производительности
- Многомировую систему
- И многое другое

**Общее количество файлов org.ultramine:** 167 Java файлов

**Основные модули:**
1. `org.ultramine.commands` - Система команд (22 файла)
2. `org.ultramine.core` - Ядро фреймворка (26 файлов)
3. `org.ultramine.scheduler` - Планировщик (9 файлов)
4. `org.ultramine.server` - Серверная логика (110 файлов)

---

## 9. МОДУЛЬ: org.ultramine.commands

### 9.1 Описание модуля

Продвинутая система команд с:
- Декларативным API через @Command аннотации
- Автодополнением аргументов (Tab completion)
- Синтаксическим разбором паттернов
- Поддержкой транслитерации RU↔EN
- Системой валидации аргументов

**Количество файлов:** 22

### 9.2 Структура пакета

```
org.ultramine.commands/
├── basic/                      # Базовые команды
│   ├── GenWorldCommand.java   # Генерация миров
│   ├── TechCommands.java       # Технические команды
│   └── VanillaCommands.java    # Обертки vanilla команд
├── syntax/                     # Парсинг и автодополнение
│   ├── ArgumentCompleter.java
│   ├── ArgumentsPattern.java
│   ├── ArgumentsPatternParser.java
│   ├── ArgumentValidator.java
│   ├── DefaultCompleters.java
│   ├── HandlerBasedArgument.java
│   ├── IArgument.java
│   ├── IArgumentCompletionHandler.java
│   └── IArgumentValidationHandler.java
├── Action.java                 # @Action аннотация
├── Command.java                # @Command аннотация
├── CommandContext.java         # Контекст выполнения
├── CommandRegistry.java        # Реестр команд
├── HandlerBasedCommand.java    # Handler-based команда
├── ICommandHandler.java        # Интерфейс обработчика
├── IExtendedCommand.java       # Расширенный интерфейс команды
├── MethodBasedCommandHandler.java # Method-based обработчик
├── OfflinePlayer.java          # Представление оффлайн игрока
└── VanillaCommandWrapper.java  # Обертка для vanilla команд
```

### 9.3 Ключевые классы

#### 9.3.1 CommandRegistry.java (299 строк)

**Назначение:** Центральный реестр всех команд сервера.

**Ключевая функциональность:**
```java
public class CommandRegistry {
    private Map<String, NameInfo> nameInfos          // Имена команд
    private SortedSet<IExtendedCommand> registeredCommands  // Зарегистрированные команды
    private ArgumentsPatternParser argumentsPatternParser   // Парсер синтаксиса

    // Регистрация команд
    void registerCommand(IExtendedCommand command)
    void registerCommands(Class<?> cls)  // Сканирует @Command методы
    IExtendedCommand registerVanillaCommand(ICommand command)

    // Поиск команд
    IExtendedCommand get(String name)
    List<String> filterPossibleCommandsNames(ICommandSender sender, String filter)
    List<IExtendedCommand> getPossibleCommands(ICommandSender sender)
}
```

**Особенности:**
1. **Транслитерация:** Автоматически создает RU/EN варианты команд
   ```java
   Iterables.transform(names, TranslitTable::translitENRU)
   ```
   Пример: `/help` → `/помощь`

2. **Приоритеты команд:** Несколько модов могут переопределять одну команду
   ```java
   class NameInfo {
       List<IExtendedCommand> available  // Все варианты
       IExtendedCommand current          // Текущая активная
   }
   ```

3. **Vanilla совместимость:** Оборачивает Vanilla команды в IExtendedCommand

#### 9.3.2 Command.java & Action.java (Аннотации)

**Command.java:**
```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Command {
    String name();                    // Имя команды
    String[] aliases() default {};    // Алиасы
    String group() default "vanilla"; // Группа (для namespace)
    String[] permissions() default {};// Права доступа
    String[] syntax() default {};     // Синтаксис для autocomplete
    boolean isUsableFromServer() default true;
}
```

**Action.java:**
```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Action {
    String command();  // К какой команде относится
    String name();     // Имя действия
}
```

**Пример использования:**
```java
@Command(
    name = "world",
    aliases = {"w"},
    permissions = {"ultramine.world"},
    syntax = {"create <name>", "delete <world>"}
)
public static void worldCommand(CommandContext ctx) {
    // Логика команды
}

@Action(command = "world", name = "create")
public static void createWorld(CommandContext ctx) {
    String name = ctx.getString(0);
    // Создание мира
}
```

#### 9.3.3 CommandContext.java

**Назначение:** Контекст выполнения команды, предоставляет удобный API для работы с аргументами.

**Основные методы:**
```java
public class CommandContext {
    ICommandSender getSender()
    String getString(int index)
    int getInt(int index)
    double getDouble(int index)
    boolean getBoolean(int index)
    EntityPlayerMP getPlayer(int index)
    WorldServer getWorld(int index)

    boolean has(int index)
    int length()
    String[] getArgs()
}
```

#### 9.3.4 ArgumentsPatternParser.java

**Назначение:** Парсит синтаксические паттерны для автодополнения.

**Примеры паттернов:**
```
"<player>"           - Обязательный игрок
"[world]"            - Опциональный мир
"<x> <y> <z>"        - Координаты
"{on|off}"           - Выбор из списка
"<player> [amount:int]" - Игрок + опциональное число
```

### 9.4 Директория basic/

#### 9.4.1 TechCommands.java

**Технические команды для администраторов:**
- `/tps` - Показывает TPS сервера
- `/mem` - Информация о памяти
- `/gc` - Принудительная сборка мусора
- `/savechunks` - Сохранение чанков
- `/restart` - Перезагрузка сервера
- `/backup` - Создание бэкапа
- И другие

#### 9.4.2 GenWorldCommand.java

**Команды управления мирами:**
- `/genworld create` - Создать мир
- `/genworld delete` - Удалить мир
- `/genworld load` - Загрузить мир
- `/genworld unload` - Выгрузить мир
- `/genworld list` - Список миров

#### 9.4.3 VanillaCommands.java

**Обертки для улучшения vanilla команд:**
- Добавляет автодополнение
- Добавляет синтаксическую проверку
- Интегрирует с Permissions системой

### 9.5 Директория syntax/

Система разбора и валидации синтаксиса команд.

**DefaultCompleters.java** - Стандартные автодополнители:
- `@PlayerCompleter` - Список онлайн игроков
- `@WorldCompleter` - Список миров
- `@IntegerCompleter` - Числовой ввод
- `@BooleanCompleter` - true/false
- `@ItemCompleter` - Предметы
- `@BlockCompleter` - Блоки

---

## 10. МОДУЛЬ: org.ultramine.core

### 10.1 Описание модуля

Ядро фреймворка Ultramine, содержащее:
- **Service система** - Dependency Injection контейнер
- **Economy система** - Встроенная экономика
- **Permissions система** - Управление правами
- **Утилиты** - Undoable действия

**Количество файлов:** 26

### 10.2 Структура пакета

```
org.ultramine.core/
├── economy/                    # Экономическая система
│   ├── account/
│   │   ├── Account.java
│   │   └── PlayerAccount.java
│   ├── exception/
│   │   ├── AccountTypeNotSupportedException.java
│   │   ├── CurrencyNotFoundException.java
│   │   ├── CurrencyNotSupportedException.java
│   │   ├── EconomyException.java
│   │   ├── InsufficientFundsException.java
│   │   ├── InternalEconomyException.java
│   │   └── NegativeAmountException.java
│   ├── holdings/
│   │   ├── AbstractAsyncHoldings.java
│   │   ├── AsyncHoldings.java
│   │   ├── FakeAsyncHoldings.java
│   │   ├── Holdings.java
│   │   ├── HoldingsFactory.java
│   │   ├── MemoryHoldings.java
│   │   └── RealAsyncHoldings.java
│   ├── service/
│   │   ├── DefaultCurrencyService.java
│   │   ├── DefaultHoldingsProvider.java
│   │   ├── Economy.java
│   │   └── EconomyRegistry.java
│   └── Currency.java
├── permissions/
│   ├── MinecraftPermissions.java
│   └── Permissions.java
├── service/                    # DI контейнер
│   ├── EventBusRegisteredService.java
│   ├── InjectService.java
│   ├── Service.java
│   ├── ServiceBytecodeAdapter.java
│   ├── ServiceDelegate.java
│   ├── ServiceManager.java
│   ├── ServiceProviderLoader.java
│   ├── ServiceStateHandler.java
│   └── ServiceSwitchEvent.java
└── util/
    ├── Undoable.java
    ├── UndoableAction.java
    ├── UndoableOnce.java
    └── UndoableValue.java
```

### 10.3 Подмодуль: service (Dependency Injection)

#### 10.3.1 ServiceManager.java (19 строк - интерфейс)

**Назначение:** Центральный DI контейнер для всех сервисов Ultramine.

```java
@Service(singleProvider = true)
@ThreadSafe
public interface ServiceManager {
    // Регистрация провайдера с приоритетом
    <T> Undoable register(Class<T> serviceClass, T provider, int priority);

    // Регистрация lazy loader
    <T> Undoable register(Class<T> serviceClass, ServiceProviderLoader<T> providerLoader, int priority);

    // Получение активного провайдера
    @Nonnull <T> T provide(Class<T> service);
}
```

**Принцип работы:**
1. **Регистрация:** Моды регистрируют свои имплементации сервисов
2. **Приоритет:** Мод с большим приоритетом переопределяет сервис
3. **Провайдинг:** Код запрашивает сервис и получает актуальную реализацию
4. **Hot-swap:** Сервисы можно менять в runtime

**Пример:**
```java
// Регистрация
services.register(Economy.class, new MyEconomy(), 100);

// Инжекция
@InjectService
private static Economy economy;

// Использование
economy.getBalance(player);
```

#### 10.3.2 @Service аннотация

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Service {
    boolean singleProvider() default false;  // Только один провайдер?
}
```

#### 10.3.3 @InjectService аннотация

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InjectService {
    // Автоматическая инжекция сервиса в поле
}
```

**Механизм инжекции:** Использует ASM трансформер (`ServiceInjectionTransformer.java`) для замены null на delegate прокси.

#### 10.3.4 ServiceDelegate & ServiceBytecodeAdapter

**ServiceDelegate** - прокси-объект, который:
- Хранит Class<T> сервиса
- При вызове метода получает актуального провайдера
- Перенаправляет вызов провайдеру

**ServiceBytecodeAdapter** - генерирует bytecode для delegate классов.

### 10.4 Подмодуль: economy

#### 10.4.1 Архитектура экономики

```
Currency                 - Валюта (название, символ)
  ↓
Account                  - Аккаунт (игрок, банк, система)
  ↓
Holdings                 - Хранилище денег в определенной валюте
  ├─ MemoryHoldings      - В памяти (volatile)
  └─ AsyncHoldings       - Асинхронное (БД, файлы)
     ├─ FakeAsyncHoldings  - Для тестов
     └─ RealAsyncHoldings  - Реальная реализация
```

#### 10.4.2 Currency.java

**Интерфейс валюты:**
```java
public interface Currency {
    String getName();
    String getSymbol();
    String getPluralName();
    int getDecimalPlaces();
}
```

**Пример:**
- Name: "Dollar"
- Symbol: "$"
- PluralName: "Dollars"
- DecimalPlaces: 2 (центы)

#### 10.4.3 Holdings.java

**Интерфейс хранилища денег:**
```java
public interface Holdings {
    Account getAccount();
    Currency getCurrency();

    double getBalance();
    boolean hasEnough(double amount);

    void set(double amount);
    void add(double amount);
    void subtract(double amount) throws InsufficientFundsException;

    void transfer(Holdings to, double amount) throws InsufficientFundsException;
}
```

#### 10.4.4 AsyncHoldings.java

**Асинхронное хранилище для работы с БД:**
```java
public interface AsyncHoldings {
    Future<Double> getBalance();
    Future<Boolean> hasEnough(double amount);
    Future<Void> set(double amount);
    Future<Void> add(double amount);
    Future<Void> subtract(double amount);
    Future<Void> transfer(AsyncHoldings to, double amount);
}
```

#### 10.4.5 Economy.java

**Главный интерфейс экономики:**
```java
@Service
public interface Economy {
    String getName();

    Currency getDefaultCurrency();

    Holdings getHoldings(Account account);
    Holdings getHoldings(Account account, Currency currency);

    AsyncHoldings getAsyncHoldings(Account account);
    AsyncHoldings getAsyncHoldings(Account account, Currency currency);

    boolean hasAccount(Account account);
    void createAccount(Account account);
}
```

#### 10.4.6 EconomyRegistry.java

**Реестр множественных экономик:**
```java
@Service
public interface EconomyRegistry {
    void register(Economy economy);
    void unregister(Economy economy);

    Economy getDefault();
    Economy get(String name);
    Collection<Economy> getAll();
}
```

**Зачем нужно:**
- Разные моды могут иметь свои экономики
- Можно иметь несколько валют (доллары, евро, кредиты)
- Per-world экономики

### 10.5 Подмодуль: permissions

#### 10.5.1 Permissions.java

**Интерфейс системы разрешений:**
```java
@Service
public interface Permissions {
    boolean has(EntityPlayer player, String permission);
    boolean has(UUID playerUUID, String permission);

    void add(EntityPlayer player, String permission);
    void remove(EntityPlayer player, String permission);

    Set<String> getPlayerPermissions(EntityPlayer player);
}
```

#### 10.5.2 MinecraftPermissions.java

**Стандартные разрешения Minecraft:**
```java
public class MinecraftPermissions {
    public static final String COMMAND_HELP = "minecraft.command.help";
    public static final String COMMAND_GIVE = "minecraft.command.give";
    public static final String COMMAND_GAMEMODE = "minecraft.command.gamemode";
    // ... и т.д.
}
```

### 10.6 Подмодуль: util

#### 10.6.1 Undoable.java

**Интерфейс отменяемого действия:**
```java
public interface Undoable {
    void undo();
}
```

**Использование:**
```java
Undoable reg = services.register(Economy.class, myEconomy, 10);
// ...
reg.undo(); // Отменить регистрацию
```

#### 10.6.2 UndoableAction.java

**Действие с кастомной логикой отмены:**
```java
public class UndoableAction implements Undoable {
    private final Runnable undoAction;

    public UndoableAction(Runnable undoAction) {
        this.undoAction = undoAction;
    }

    @Override
    public void undo() {
        undoAction.run();
    }
}
```

#### 10.6.3 UndoableValue.java

**Хранилище значения с возможностью отката:**
```java
public class UndoableValue<T> implements Undoable {
    private final Consumer<T> setter;
    private final T oldValue;

    @Override
    public void undo() {
        setter.accept(oldValue);
    }
}
```

---

## 11. МОДУЛЬ: org.ultramine.scheduler

### 11.1 Описание модуля

Планировщик задач в стиле Cron для Minecraft сервера.

**Количество файлов:** 9

### 11.2 Структура пакета

```
org.ultramine.scheduler/
├── pattern/
│   ├── AlwaysTrueValueMatcher.java
│   ├── DayOfMonthValueMatcher.java
│   ├── IntSetValueMatcher.java
│   ├── InvalidPatternException.java
│   ├── IValueMatcher.java
│   └── SchedulingPattern.java
├── ScheduledAsyncTask.java
├── ScheduledSyncTask.java
├── ScheduledTask.java
└── Scheduler.java
```

### 11.3 Ключевые классы

#### 11.3.1 Scheduler.java

**Главный класс планировщика:**
```java
public class Scheduler {
    void start()
    void stop()

    void schedule(SchedulingPattern pattern, Runnable task)
    void scheduleAsync(SchedulingPattern pattern, Runnable task)
    void scheduleSync(SchedulingPattern pattern, Runnable task)
}
```

#### 11.3.2 SchedulingPattern.java

**Паттерн расписания (Cron-подобный):**
```java
public class SchedulingPattern {
    // * * * * *
    // | | | | |
    // | | | | +-- День недели (0-6)
    // | | | +---- Месяц (1-12)
    // | | +------ День месяца (1-31)
    // | +-------- Час (0-23)
    // +---------- Минута (0-59)

    boolean matches(Calendar calendar)
}
```

**Примеры паттернов:**
```
"0 0 * * *"      - Каждый день в полночь
"*/15 * * * *"   - Каждые 15 минут
"0 */6 * * *"    - Каждые 6 часов
"0 0 * * 0"      - Каждое воскресенье в полночь
"0 12 1 * *"     - 1-го числа каждого месяца в 12:00
```

#### 11.3.3 IValueMatcher.java

**Интерфейс матчера значений:**
```java
public interface IValueMatcher {
    boolean matches(int value, int month);
}
```

**Реализации:**
- `AlwaysTrueValueMatcher` - * (любое значение)
- `IntSetValueMatcher` - конкретные значения (1,5,10)
- `DayOfMonthValueMatcher` - дни с учетом месяца

#### 11.3.4 ScheduledTask.java

**Абстрактная задача:**
```java
public abstract class ScheduledTask {
    protected final SchedulingPattern pattern;
    protected final Runnable task;

    abstract void execute();
    boolean shouldExecute(Calendar calendar) {
        return pattern.matches(calendar);
    }
}
```

**ScheduledSyncTask** - выполняется в главном потоке сервера
**ScheduledAsyncTask** - выполняется в отдельном потоке

---

## 12. МОДУЛЬ: org.ultramine.server

### 12.1 Описание модуля

Основная серверная логика Ultramine.

**Количество файлов:** 110+

### 12.2 Структура пакета

```
org.ultramine.server/
├── asm/                        # ASM трансформеры
│   ├── transformers/
│   │   ├── BlockLeavesBaseFixer.java
│   │   ├── PrintStackTraceTransformer.java
│   │   ├── ServiceInjectionTransformer.java
│   │   ├── TrigMathTransformer.java
│   │   └── UMTransformerCollection.java
│   ├── ComputeFramesClassWriter.java
│   └── UMTBatchTransformer.java
├── bootstrap/                  # Загрузка сервера
│   ├── log4j/
│   │   ├── IUnformattedMessage.java
│   │   ├── UMConsoleLayout.java
│   │   └── UMStripColorsRewritePolicy.java
│   └── UMBootstrap.java
├── chunk/                      # Система чанков (20+ файлов)
│   ├── alloc/                  # Аллокация памяти для чанков
│   │   ├── unsafe/
│   │   │   ├── AbstractUnsafeMemSlot.java
│   │   │   ├── Unsafe7MemSlot.java
│   │   │   ├── Unsafe8MemSlot.java
│   │   │   └── UnsafeChunkAlloc.java
│   │   ├── ChunkAllocService.java
│   │   └── MemSlot.java
│   ├── AntiXRayService.java
│   ├── CallbackAddDependency.java
│   ├── CallbackMultiChunkDependentTask.java
│   ├── ChunkBindState.java
│   ├── ChunkGC.java
│   ├── ChunkGenerationQueue.java
│   ├── ChunkHash.java
│   ├── ChunkLoadCallbackRunnable.java
│   ├── ChunkMap.java
│   ├── ChunkProfiler.java
│   ├── ChunkSendManager.java
│   ├── ChunkSnapshot.java
│   ├── IChunkDependency.java
│   ├── IChunkLoadCallback.java
│   └── PendingBlockUpdate.java
├── data/                       # Данные и БД
│   ├── player/
│   │   ├── PlayerCoreData.java
│   │   ├── PlayerData.java
│   │   ├── PlayerDataExtension.java
│   │   └── PlayerDataExtensionInfo.java
│   ├── Databases.java
│   ├── IDataProvider.java
│   ├── JDBCDataProvider.java
│   ├── NBTFileDataProvider.java
│   └── ServerDataLoader.java
├── economy/                    # Реализация экономики
│   ├── CurrencyImpl.java
│   ├── DefaultCurrencyServiceProvider.java
│   ├── PlayerAccountImpl.java
│   ├── UMEconomy.java
│   ├── UMEconomyRegistry.java
│   ├── UMIntegratedHoldingsProvider.java
│   ├── UMIntegratedPlayerHoldings.java
│   └── UMIntegratedPlayerHoldingsFactory.java
├── event/                      # События
├── internal/                   # Внутренние компоненты
├── mobspawn/                   # Спавн мобов
├── service/                    # Сервисы
├── tools/                      # Инструменты
├── util/                       # Утилиты (35+ файлов)
├── world/                      # Многомировая система
│   ├── imprt/                  # Импорт миров
│   └── load/                   # Загрузка миров
├── BackupManager.java
├── ConfigurationHandler.java
├── EntityType.java
├── RecipeCache.java
├── Restarter.java
├── ServerLoadBalancer.java
├── Teleporter.java
├── UltraminePlugin.java
├── UltramineServerConfig.java
├── UltramineServerModContainer.java (ГЛАВНЫЙ КЛАСС)
├── WorldBorder.java
├── WorldConstants.java
└── WorldsConfig.java
```

### 12.3 Ключевые классы верхнего уровня

#### 12.3.1 UltramineServerModContainer.java (300 строк)

**Назначение:** Главная точка входа Ultramine, FML ModContainer.

**Жизненный цикл:**
```java
@Subscribe preInit(FMLPreInitializationEvent)
    ↓ Регистрация базовых сервисов
    ↓ Загрузка конфигурации
    ↓ Инициализация БД

@Subscribe init(FMLInitializationEvent)
    ↓ Регистрация event handlers

@Subscribe postInit(FMLPostInitializationEvent)
    ↓ Сохранение конфигурации

@Subscribe serverAboutToStart(FMLServerAboutToStartEvent)
    ↓ Регистрация ChunkGenerationQueue
    ↓ Регистрация MultiWorld
    ↓ Загрузка ItemBlocker

@Subscribe serverStarting(FMLServerStartingEvent)
    ↓ Регистрация PlayerDataExtensions
    ↓ Регистрация команд
    ↓ Запуск Scheduler

@Subscribe serverStarted(FMLServerStartedEvent)
    ↓ Загрузка кешей
    ↓ Включение RecipeCache

@Subscribe serverStopped(FMLServerStoppedEvent)
    ↓ Остановка Scheduler
    ↓ Очистка ресурсов
```

**Регистрируемые сервисы:**
```java
services.register(ChunkAllocService.class, new UnsafeChunkAlloc(), 0);
services.register(AntiXRayService.class, new AntiXRayService.EmptyImpl(), 0);
services.register(EconomyRegistry.class, new UMEconomyRegistry(), 0);
services.register(Economy.class, new UMEconomy(), 0);
services.register(DefaultHoldingsProvider.class, new UMIntegratedHoldingsProvider(), 0);
services.register(Permissions.class, new OpBasedPermissions(), 0);
```

#### 12.3.2 ConfigurationHandler.java

**Назначение:** Загрузка и управление конфигурационными файлами.

**Загружаемые конфиги:**
- `server.yml` - Главный конфиг сервера
- `worlds.yml` - Конфигурация миров
- `backup.yml` - Настройки бэкапов
- `item-blocker.yml` - Блокировка предметов

#### 12.3.3 RecipeCache.java

**Назначение:** Кеширование результатов крафта для ускорения.

**Принцип:**
1. При крафте сохраняется: ItemStack[] → Result
2. При повторном крафте с теми же ингредиентами - мгновенный результат
3. Очистка при remap событиях (FMLModIdMappingEvent)

**Производительность:** ~10-100x ускорение для частого крафта

#### 12.3.4 Restarter.java

**Назначение:** Автоматический перезапуск сервера.

**Функции:**
- Запланированный перезапуск (через N минут)
- Перезапуск по расписанию
- Предупреждения игрокам
- Graceful shutdown

#### 12.3.5 BackupManager.java

**Назначение:** Автоматическое создание бэкапов миров.

**Функции:**
- Полный бэкап всех миров
- Инкрементальный бэкап
- Ротация старых бэкапов
- Сжатие ZIP
- Запуск по расписанию

---

## КОНЕЦ ЧАСТИ 2

**В следующей части будет проанализировано:**
- Детальный анализ chunk системы
- ASM трансформеры
- MultiWorld система
- Система данных и БД
- Mob spawn система
- Утилиты
- И другие подсистемы
