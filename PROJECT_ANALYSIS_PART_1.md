# ДЕТАЛЬНЫЙ АНАЛИЗ ПРОЕКТА ULTRAMINE CORE - ЧАСТЬ 1

## ДАТА АНАЛИЗА
2025-10-09

---

## 1. ОБЩАЯ ИНФОРМАЦИЯ О ПРОЕКТЕ

### 1.1 Идентификация проекта
- **Название проекта**: ultramine_core-1.7.10
- **Группа**: org.ultramine.core
- **Версия Minecraft**: 1.7.10
- **Тип релиза**: indev
- **Общее количество файлов**: 3162
- **Количество Java файлов**: 2045
- **Репозиторий**: Git (текущая ветка: master)

### 1.2 Описание проекта
Ultramine Core - это модифицированное ядро сервера Minecraft 1.7.10, основанное на Forge Mod Loader (FML). Проект представляет собой комплексную систему для запуска модифицированных серверов Minecraft с расширенными возможностями управления модами, производительностью и функциональностью.

### 1.3 Технологический стек
- **Язык программирования**: Java 8 (sourceCompatibility & targetCompatibility = 1.8)
- **Система сборки**: Gradle
- **Основные фреймворки**:
  - Forge Mod Loader (FML)
  - Minecraft 1.7.10
  - ASM 5.0.3 (bytecode manipulation)
  - Netty 4.0.10.Final (networking)
  - Log4j 2.0-beta9 (logging)

---

## 2. СТРУКТУРА ПРОЕКТА

### 2.1 Корневая структура директорий

```
ultramine_core_master_branch_brutalcat/
├── buildSrc/                    # Custom Gradle build logic
│   └── src/main/java/org/ultramine/gradle/
│       ├── internal/            # Internal utilities
│       └── task/                # Custom Gradle tasks
├── conf/                        # Configuration files
│   ├── mcp.exc                  # MCP exception mappings
│   ├── mcp2notch.srg           # MCP to Notch mappings
│   ├── mcp2srg.srg             # MCP to SRG mappings
│   ├── notch2mcp.srg           # Notch to MCP mappings
│   ├── notch2srg.srg           # Notch to SRG mappings
│   └── srg.exc                 # SRG exception mappings
├── gradle/                      # Gradle wrapper
├── src/main/                    # Main source code
│   ├── java/                    # Java source files
│   └── resources/               # Resource files
├── build.gradle                 # Main build configuration
├── settings.gradle              # Gradle settings
├── gradle.properties            # Project properties
├── gradlew                      # Gradle wrapper (Unix)
├── gradlew.bat                  # Gradle wrapper (Windows)
└── LICENSE                      # License file
```

### 2.2 Основные пакеты исходного кода

#### 2.2.1 Пакет cpw.mods.fml (Forge Mod Loader)
Ядро системы загрузки и управления модами.

**Подпакеты:**
- `cpw.mods.fml.client` - Клиентская часть FML
  - `config/` - Система конфигурации GUI
  - `event/` - События клиента
  - `registry/` - Реестры клиента
- `cpw.mods.fml.common` - Общая логика FML
  - `asm/` - ASM transformers для манипуляции байткодом
  - `discovery/` - Обнаружение модов
  - `event/` - Система событий
  - `eventhandler/` - Обработчики событий
  - `launcher/` - Запуск приложения
  - `network/` - Сетевая подсистема
  - `registry/` - Глобальные реестры
  - `versioning/` - Управление версиями
- `cpw.mods.fml.relauncher` - Система перезапуска
- `cpw.mods.fml.server` - Серверная часть FML

#### 2.2.2 Пакет net.minecraft
Основной код Minecraft (модифицированный).

**Структура:**
- `net.minecraft.block` - Блоки
  - `material/` - Материалы блоков
- `net.minecraft.client` - Клиент
  - `audio/` - Аудио система
  - `entity/` - Клиентские сущности
  - `gui/` - Графический интерфейс
  - `model/` - 3D модели
  - `multiplayer/` - Мультиплеер
  - `network/` - Сетевой код
  - `particle/` - Частицы
  - `renderer/` - Рендеринг
  - `resources/` - Ресурсы
  - `settings/` - Настройки
  - `shader/` - Шейдеры
  - `stream/` - Twitch стриминг
- `net.minecraft.command` - Команды
  - `server/` - Серверные команды
- `net.minecraft.crash` - Обработка крашей
- `net.minecraft.creativetab` - Творческие вкладки
- `net.minecraft.dispenser` - Раздатчики
- `net.minecraft.enchantment` - Зачарования
- `net.minecraft.entity` - Сущности
  - `ai/` - Искусственный интеллект
  - `boss/` - Боссы
  - `effect/` - Эффекты
  - `item/` - Предметы-сущности
  - `monster/` - Монстры
  - `passive/` - Мирные мобы
  - `player/` - Игроки
  - `projectile/` - Снаряды
- `net.minecraft.init` - Инициализация
- `net.minecraft.inventory` - Инвентарь
- `net.minecraft.item` - Предметы
  - `crafting/` - Крафтинг
- `net.minecraft.nbt` - NBT формат
- `net.minecraft.network` - Сеть
  - `handshake/` - Handshake протокол
  - (продолжается в следующих разделах)

#### 2.2.3 Пакет org.ultramine
Специфичный код Ultramine (будет проанализирован детально).

---

## 3. СИСТЕМА СБОРКИ (GRADLE)

### 3.1 Конфигурация build.gradle

#### 3.1.1 Применяемые плагины
```gradle
apply plugin: 'java'
apply plugin: 'maven-publish'
apply plugin: 'eclipse'
```

#### 3.1.2 Java конфигурация
- Source Compatibility: Java 8
- Target Compatibility: Java 8
- Кодировка компиляции: UTF-8
- Инкрементальная компиляция: опциональная (compile_incremental)

#### 3.1.3 Репозитории
Проект использует множество Maven репозиториев:
1. **forge** - https://files.minecraftforge.net/maven
2. **minecraftforge** - https://maven.minecraftforge.net/
3. **mavenCentral** - Центральный Maven репозиторий
4. **sonatypeSnapshot** - Snapshot версии
5. **sonatype** - https://central.sonatype.com/
6. **minecraft** - https://libraries.minecraft.net/
7. **maven2** - https://repo.maven.apache.org/maven2/
8. **apache** - https://maven.apache.org/
9. **mvnrepository** - https://mvnrepository.com/

#### 3.1.4 Конфигурации зависимостей
Проект использует собственную систему разделения зависимостей:

**Базовые конфигурации:**
- `compileCommon` - Общие зависимости для компиляции
- `compileClient` - Клиентские зависимости
- `compileServer` - Серверные зависимости
- `runtimeCommon` - Общие runtime зависимости
- `runtimeClient` - Клиентские runtime
- `runtimeServer` - Серверные runtime

**Пакетные конфигурации:**
- `packageClient` extends: compileCommon, compileClient, runtimeCommon, runtimeClient
- `packageServer` extends: compileCommon, compileServer, runtimeCommon, runtimeServer
- `packageAll` extends: все вышеперечисленные

### 3.2 Зависимости проекта

#### 3.2.1 CompileCommon зависимости (общие)

**Launcher & Core:**
- `net.minecraft:launchwrapper:1.12` - Minecraft launcher wrapper
- `net.sf.jopt-simple:jopt-simple:5.0.1` - Command-line parsing

**Bytecode manipulation:**
- `org.ow2.asm:asm-debug-all:5.0.3` - ASM bytecode library

**Collections & Utilities:**
- `net.sf.trove4j:trove4j:3.0.3` - High-performance collections
- `net.openhft:koloboke-api-jdk8:0.6.8` - Fast primitive collections API

**Scala (полная поддержка):**
- `org.scala-lang:scala-actors-migration_2.11:1.1.0`
- `org.scala-lang:scala-compiler:2.11.7`
- `org.scala-lang.plugins:scala-continuations-library_2.11:1.0.2`
- `org.scala-lang.plugins:scala-continuations-plugin_2.11.1:1.0.2`
- `org.scala-lang:scala-library:2.11.7`
- `org.scala-lang:scala-parser-combinators:2.11.0-M4`
- `org.scala-lang:scala-reflect:2.11.7`
- `org.scala-lang:scala-swing:2.11.0-M7`
- `org.scala-lang:scala-xml:2.11.0-M4`

**Apache Commons:**
- `org.apache.commons:commons-dbcp2:2.1.1` - Database connection pooling
- `org.apache.commons:commons-compress:1.21` - Compression utilities
- `org.apache.commons:commons-lang3:3.12.0` - Language utilities
- `commons-logging:commons-logging:1.1.3`
- `commons-io:commons-io:2.4`
- `commons-codec:commons-codec:1.9`

**HTTP & Networking:**
- `org.apache.httpcomponents:httpcore:4.3.2`
- `org.apache.httpcomponents:httpclient:4.4.1`
- `io.netty:netty-all:4.0.10.Final` - Async network framework

**Logging:**
- `org.apache.logging.log4j:log4j-api:2.0-beta9-fixed` (CUSTOM VERSION)
- `org.apache.logging.log4j:log4j-core:2.0-beta9-fixed` (CUSTOM VERSION)
- `com.lmax:disruptor:3.2.1` - Required by Log4j async

**Serialization & Config:**
- `org.yaml:snakeyaml:1.16` - YAML parser
- `com.google.code.gson:gson:2.2.4` - JSON parser
- `com.typesafe:config:1.2.1` - Configuration library

**Google Libraries:**
- `com.google.guava:guava:17.0` - Google core libraries
- `com.google.code.findbugs:jsr305:1.3.9` - Annotations

**Mojang Libraries:**
- `com.mojang:authlib:1.5.21` - Authentication library
- `com.ibm.icu:icu4j-core-mojang:51.2` - Unicode support

**Other:**
- `java3d:vecmath:1.3.1` - Vector math library
- `lzma:lzma:0.0.1` - LZMA compression
- `com.typesafe.akka:akka-actor_2.11:2.3.3` - Actor framework

#### 3.2.2 CompileClient зависимости

**Mojang:**
- `com.mojang:realms:1.3.5` - Minecraft Realms

**Audio (PaulsCode Sound System):**
- `com.paulscode:codecjorbis:20101023` - Ogg Vorbis codec
- `com.paulscode:codecwav:20101023` - WAV codec
- `com.paulscode:libraryjavasound:20101123` - JavaSound library
- `com.paulscode:librarylwjglopenal:20100824` - OpenAL library
- `com.paulscode:soundsystem:20120107` - Sound system core

**Input & Graphics:**
- `net.java.jinput:jinput:2.0.5` - Input handling
- `net.java.jutils:jutils:1.0.0` - Java utilities
- `org.lwjgl.lwjgl:lwjgl:2.9.1` - Lightweight Java Game Library
- `org.lwjgl.lwjgl:lwjgl_util:2.9.1` - LWJGL utilities

**Streaming:**
- `tv.twitch:twitch:5.16` - Twitch integration

#### 3.2.3 CompileServer зависимости

**Console:**
- `jline:jline:2.13` - Console input library (transitive=false)

#### 3.2.4 Runtime зависимости

**Common:**
- `net.openhft:koloboke-impl-jdk8:0.6.8` - Koloboke implementation

**Server:**
- `mysql:mysql-connector-java:8.0.33` - MySQL database driver

### 3.3 Custom Gradle Tasks

#### 3.3.1 injectVersion
**Тип:** SpeicialClassTransformTask
**Зависимости:** compileJava
**Описание:** Инжектирует версию проекта в байткод
**Конфигурация:**
```gradle
inputDir = tasks.compileJava.destinationDir
replaceIn 'org.ultramine.server.UltramineServerModContainer'
replace '@version@', version
```

#### 3.3.2 reobf (Reobfuscation)
**Тип:** ReobfTask
**Зависимости:** compileJava, injectVersion
**Описание:** Обфускация классов обратно в Notch naming
**Конфигурация:**
```gradle
classpath = sourceSets.main.compileClasspath
srg = 'conf/mcp2notch.srg'
inputDir = tasks.compileJava.destinationDir
overrideInputDir = tasks.injectVersion.outputDir
```

#### 3.3.3 sidesplit
**Тип:** SideSplitTask
**Зависимости:** reobf
**Описание:** Разделяет классы на клиентские и серверные
**Конфигурация:**
```gradle
inputDir = tasks.reobf.outputDir
```

#### 3.3.4 processServerResources
**Тип:** ProcessResources
**Описание:** Обработка серверных ресурсов
**Исключения:**
- assets/minecraft/font
- assets/minecraft/shaders
- assets/minecraft/texts
- assets/minecraft/textures
- assets/fml/textures

#### 3.3.5 processClientResources
**Тип:** ProcessResources
**Описание:** Обработка клиентских ресурсов
**Исключения:**
- org/ultramine/defaults

#### 3.3.6 jar_server
**Тип:** Jar
**Зависимости:** sidesplit, processServerResources
**Классификатор:** server
**Manifest:**
```
Main-Class: cpw.mods.fml.relauncher.ServerLaunchWrapper
TweakClass: cpw.mods.fml.common.launcher.FMLTweaker
Class-Path: <generated from packageServer dependencies>
```

#### 3.3.7 jar_client
**Тип:** Jar
**Зависимости:** sidesplit, processClientResources
**Классификатор:** client

#### 3.3.8 jar_universal
**Тип:** Jar
**Зависимости:** reobf, processResources
**Классификатор:** universal

#### 3.3.9 jar (default)
**Классификатор:** dev

#### 3.3.10 jar_source
**Тип:** Jar
**Содержимое:** sourceSets.main.allSource
**Классификатор:** sources

#### 3.3.11 dumpLibs
**Тип:** Copy
**Описание:** Копирует все runtime зависимости в структуру Maven
**Выход:** $buildDir/libs/libraries

#### 3.3.12 storeLastRevision
**Описание:** Сохраняет информацию о последней ревизии
**Выход:** $buildDir/versions

#### 3.3.13 changelog
**Описание:** Генерирует changelog на основе git истории
**Выход:** $buildDir/libs/$project.name-$version-changelog.txt

### 3.4 Версионирование

#### 3.4.1 Схема версионирования
Версия формируется на основе git тегов и типа релиза:

**Формат версии:**
- **indev**: {major}.{minor}.0-indev
- **stable**: {major}.{minor}.{revision}
- **other (alpha/beta/rc)**: {major}.{minor}.0-{type}.{revision}

**Источник major.minor:**
- Извлекается из последнего git тега командой `git describe --tags --long`
- Формат тега: `v{major}.{minor}` (например, v1.0)

**Revision:**
- Автоматически инкрементируется при каждой сборке
- Хранится в файле: `$buildDir/versions/{major}.{minor}`
- Формат файла: `{commit_hash}:{revision}`

**Commit hash:**
- Используется для отслеживания изменений
- Извлекается из `git describe`

#### 3.4.2 Текущие настройки версии
```properties
minecraft_version=1.7.10
concat_mc_version_to=name  # Добавляет версию MC к имени
release_type=indev
```

**Результат:**
- Project name: ultramine_core-1.7.10
- Version: {computed from git}

### 3.5 Артефакты сборки

**Текущие настройки производства:**
```properties
produce_universal_jar=false
produce_server_jar=true
produce_client_jar=true
```

**Производимые артефакты:**
1. `ultramine_core-1.7.10-{version}-dev.jar` - Development версия (always)
2. `ultramine_core-1.7.10-{version}-server.jar` - Серверная версия
3. `ultramine_core-1.7.10-{version}-client.jar` - Клиентская версия
4. `ultramine_core-1.7.10-{version}-sources.jar` - Исходники (always)
5. `ultramine_core-1.7.10-{version}-changelog.txt` - Changelog

---

## 4. BUILDRC МОДУЛЬ (CUSTOM GRADLE TASKS)

### 4.1 Структура buildSrc
```
buildSrc/
└── src/main/java/org/ultramine/gradle/
    ├── internal/
    │   ├── DirectoryClassRepo.java
    │   ├── RepoInheritanceProvider.java
    │   └── UMFileUtils.java
    └── task/
        ├── ReobfTask.java
        ├── SideSplitTask.java
        └── SpeicialClassTransformTask.java
```

### 4.2 Классы в buildSrc (краткое описание)

#### 4.2.1 org.ultramine.gradle.internal

**DirectoryClassRepo.java**
- Репозиторий классов из директории
- Используется для работы с скомпилированными классами

**RepoInheritanceProvider.java**
- Провайдер информации о наследовании классов
- Необходим для корректной обработки при обфускации

**UMFileUtils.java**
- Утилиты для работы с файлами
- Вспомогательные функции для копирования, перемещения и т.д.

#### 4.2.2 org.ultramine.gradle.task

**ReobfTask.java**
- Задача реобфускации (MCP -> Notch naming)
- Использует SRG маппинги из conf/
- Критически важна для создания рабочих jar файлов

**SideSplitTask.java**
- Задача разделения классов на client/server/common
- Анализирует @SideOnly аннотации
- Создает отдельные наборы классов для клиента и сервера

**SpeicialClassTransformTask.java** (опечатка в названии - Special)
- Задача специальной трансформации классов
- Используется для инжектирования версии
- Может выполнять замены строк в байткоде

---

## 5. КОНФИГУРАЦИОННЫЕ ФАЙЛЫ

### 5.1 Файлы в директории conf/

#### 5.1.1 SRG маппинги (Searge mappings)

**mcp2notch.srg**
- Маппинг: MCP names → Notch (obfuscated) names
- Используется в ReobfTask
- Необходим для создания production jar

**mcp2srg.srg**
- Маппинг: MCP names → SRG (intermediate) names
- SRG - промежуточный формат между MCP и Notch

**notch2mcp.srg**
- Маппинг: Notch names → MCP names
- Обратный к mcp2notch.srg

**notch2srg.srg**
- Маппинг: Notch names → SRG names

**srg.exc**
- Exception mappings для SRG
- Обрабатывает исключения и особые случаи

**mcp.exc**
- Exception mappings для MCP
- Специальные правила маппинга

#### 5.1.2 build.gradle.forge
- Альтернативный build файл для Forge
- Возможно, legacy или для специальных сборок

### 5.2 Gradle конфигурация

**gradle.properties**
```properties
# Идентификация
project_name=ultramine_core
project_group=org.ultramine.core

# Версионирование
minecraft_version=1.7.10
concat_mc_version_to=name
release_type=indev

# Артефакты
produce_universal_jar=false
produce_server_jar=true
produce_client_jar=true

# Публикация
publish_jars=
publish_url=

# Опции сборки
compile_incremental=false
```

**gradle-wrapper.properties**
- Конфигурация Gradle Wrapper
- Обеспечивает использование определенной версии Gradle

---

## 6. РЕСУРСЫ ПРОЕКТА

### 6.1 Структура resources

```
src/main/resources/
├── assets/minecraft/
│   ├── font/                    # Шрифты (только client)
│   ├── shaders/                 # Шейдеры (только client)
│   │   ├── post/               # Post-processing shaders
│   │   └── program/            # Shader programs
│   ├── texts/                   # Текстовые ресурсы (только client)
│   └── textures/                # Текстуры (только client)
├── assets/fml/
│   └── textures/                # FML текстуры (только client)
├── org/ultramine/defaults/
│   └── defaultworlds.yml        # Конфигурация миров по умолчанию
├── fmlversion.properties        # Информация о версии FML
└── log4j2.xml                   # Конфигурация логирования
```

### 6.2 Шейдеры

Проект включает полный набор шейдеров Minecraft:

**Post-processing shaders (46 файлов):**
- antialias.json, art.json, bits.json
- blobs.json, blobs2.json, bloom.json
- blur.json, bumpy.json
- color_convolve.json
- deconverge.json, desaturate.json
- flip.json, fxaa.json
- green.json
- invert.json
- notch.json, ntsc.json
- outline.json
- pencil.json, phosphor.json
- scan_pincushion.json, sobel.json
- wobble.json
- и другие

**Shader programs:**
Соответствующие программы для каждого post-shader:
- antialias.json, bits.json, blit.json
- blobs.json, blobs2.json, bloom.json
- blur.json, bumpy.json
- color_convolve.json
- deconverge.json, downscale.json
- flip.json, fxaa.json
- invert.json
- notch.json, ntsc_decode.json, ntsc_encode.json
- outline.json, outline_combine.json, outline_soft.json, outline_watercolor.json
- overlay.json
- phosphor.json
- scan_pincushion.json, sobel.json
- wobble.json

---

## 7. ТОЧКА ВХОДА И ЗАПУСК

### 7.1 Start.java (Development Launcher)

**Расположение:** `src/main/java/Start.java`

**Назначение:**
- Development launcher для клиента Minecraft
- Обрабатывает аутентификацию
- Передает управление Main классу Minecraft

**Основной функционал:**

1. **Парсинг аргументов:**
   - `--username` - имя пользователя
   - `--password` - пароль (удаляется из памяти после использования)
   - `--session` - токен сессии
   - `--version` - версия

2. **Аутентификация:**
   - Метод getSession(username, password)
   - Использует старый login.minecraft.net API (УСТАРЕЛО)
   - URL: `http://login.minecraft.net/?user={user}&password={pass}&version=13`
   - Возвращает session token

3. **Безопасность:**
   - Затирает пароль в args после использования
   - Заменяет на "no_password_for_joo"

4. **Версия по умолчанию:**
   - Если не указана: устанавливает "--version fml_mcp"

5. **Запуск:**
   - Вызывает `net.minecraft.client.main.Main.main(args)`

**ВАЖНЫЕ ЗАМЕЧАНИЯ:**
- Использует УСТАРЕВШИЙ authentication API
- login.minecraft.net более не работает (Mojang мигрировал на новую систему)
- Этот класс работает только в legacy окружении или для разработки

### 7.2 Серверный запуск

**Main-Class в jar_server:**
```
cpw.mods.fml.relauncher.ServerLaunchWrapper
```

**TweakClass:**
```
cpw.mods.fml.common.launcher.FMLTweaker
```

**Процесс запуска:**
1. ServerLaunchWrapper запускается JVM
2. Загружает FMLTweaker
3. FMLTweaker инициализирует:
   - LaunchClassLoader
   - Transformers (ASM)
   - Mods discovery
4. Загружает основной server класс
5. Передает управление MinecraftServer

---

## КОНЕЦ ЧАСТИ 1

**В следующей части будет проанализировано:**
- Детальный анализ всех Java пакетов
- FML система загрузки модов
- ASM Transformers
- Сетевая архитектура
- Система событий
- И многое другое...
