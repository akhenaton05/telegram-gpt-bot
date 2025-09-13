# Telegram AI Bot

**Telegram AI Bot** — это универсальный Telegram-бот, поддерживающий интеграцию с несколькими AI-провайдерами (OpenAI GPT и Anthropic Claude) для обработки текстовых сообщений и изображений. Бот предоставляет возможность переключения между различными моделями в реальном времени и поддерживает контекст диалогов. Проект написан на Java с использованием Spring Boot и библиотеки `telegrambots`.

## Возможности

- **Мультипровайдерная поддержка**: Работа с моделями OpenAI (GPT-4.1 Mini, GPT-5 Nano) и Anthropic Claude (Claude 3 Haiku, Claude 3.5 Haiku, Claude 4 Sonnet)
- **Обработка контента**: 
  - Текстовые сообщения с поддержкой контекста
  - Анализ и описание изображений
  - Извлечение текста из документов и скриншотов
- **Динамическое переключение моделей**: Выбор AI-модели через inline-клавиатуру прямо в чате
- **Команды**:
  - `/start` — Приветственное сообщение с инструкциями по использованию
  - `/info` — Информация о боте, текущей модели и статусе прокси
  - `/model` — Выбор AI-модели из доступных вариантов
  - `/history` — Просмотр текущего контекста беседы
  - `/clear` — Очистка контекста диалога
- **Контекст диалогов**: Сохранение истории до 7 сообщений для каждого чата
- **Прокси-поддержка**: Настраиваемый прокси для обхода региональных ограничений
- **Markdown поддержка**: Автоматическое форматирование кода в HTML

## Архитектура

Проект использует паттерн Strategy для работы с различными AI-провайдерами:
- **AiClient** — интерфейс для унифицированной работы с AI-сервисами
- **AnthropicClient** — реализация для Anthropic Claude API
- **OpenAiClient** — реализация для OpenAI API
- **AiClientFactory** — фабрика для создания клиентов
- **ConversationContext** — управление контекстом диалогов
- **ProxyConfig** — конфигурация HTTP-клиентов с поддержкой прокси

## Требования

- **Java**: 22 или выше
- **Maven**: 3.6.0 или выше
- **Telegram Bot Token**: Получите токен через [BotFather](https://t.me/BotFather)
- **AI API Keys**:
  - **Anthropic API Key**: [аккаунт Anthropic](https://www.anthropic.com)
  - **OpenAI API Key**: [аккаунт OpenAI](https://www.openai.com)
- (Опционально) Прокси-сервер для доступа к AI API

## Установка

1. **Клонируйте репозиторий**:
   ```bash
   git clone https://github.com/your-username/telegram-ai-bot.git
   cd telegram-ai-bot
   ```

2. **Создайте конфигурационный файл**:
   Скопируйте `src/main/resources/application-template.yml` в `src/main/resources/application.yml`.
   Заполните `application.yml` вашими данными:
   ```yaml
   spring:
     main:
       web-application-type: none

   # Конфигурация Claude API
   claude:
     base-url: "${CLAUDE_BASE_URL:https://api.anthropic.com}"
     api-key: "${CLAUDE_API_KEY:your-claude-api-key}"
     model: "${CLAUDE_MODEL:claude-3-haiku-20240307}"
     system-prompt: "Ты дружелюбный ассистент, отвечай кратко и на русском."
     api-version: "2023-06-01"

   # Конфигурация OpenAI API  
   openai:
     baseUrl: "${OPENAI_BASE_URL:https://api.openai.com/v1}"
     apiKey: "${OPENAI_API_KEY:your-openai-api-key}"
     model: "${OPENAI_MODEL:gpt-4.1-mini-2025-04-14}"

   # Конфигурация Telegram Bot
   telegrambot:
     botToken: "${TELEGRAM_BOT_TOKEN:your-telegram-bot-token}"
     botUsername: "${TELEGRAM_BOT_USERNAME:@your_bot_username}"

   # Конфигурация прокси (опционально)
   proxy:
     enabled: "${PROXY_ENABLED:false}"
     host: "${PROXY_HOST:}"
     port: "${PROXY_PORT:0}"
     username: "${PROXY_USERNAME:}"
     password: "${PROXY_PASSWORD:}"
   ```

3. **Установите зависимости**:
   ```bash
   mvn clean install
   ```

4. **(Альтернативно) Настройте переменные окружения**:
   ```bash
   export TELEGRAM_BOT_TOKEN="your-telegram-bot-token"
   export CLAUDE_API_KEY="your-claude-api-key"
   export OPENAI_API_KEY="your-openai-api-key"
   export PROXY_ENABLED="true"
   export PROXY_HOST="your-proxy-host"
   export PROXY_PORT="your-proxy-port"
   export PROXY_USERNAME="your-proxy-username"
   export PROXY_PASSWORD="your-proxy-password"
   ```

5. **Запустите приложение**:
   ```bash
   mvn spring-boot:run
   ```

## Поддерживаемые модели

### OpenAI Models
- **GPT-4.1 Mini** (`gpt-4.1-mini-2025-04-14`) - Быстрый и экономичный
- **GPT-5 Nano** (`gpt-5-nano`) - Новейшая модель

### Anthropic Claude Models  
- **Claude 3 Haiku** (`claude-3-haiku-20240307`) - Быстрый и доступный
- **Claude 3.5 Haiku** (`claude-3-5-haiku-20241022`) - Улучшенная версия
- **Claude 4 Sonnet** (`claude-sonnet-4-20250514`) - Самый продвинутый

## Использование

1. **Текстовые сообщения**: Просто напишите любой вопрос боту
2. **Изображения**: Отправьте фото с подписью или без неё
3. **Переключение моделей**: Используйте команду `/model` для выбора AI-модели
4. **Управление контекстом**: Используйте `/clear` для очистки истории диалога

### Примеры использования с изображениями:
- Фото еды: "Что это за блюдо?"
- Скриншот кода: "Найди ошибку в коде"
- Документ: "Извлеки текст из изображения"
- Просто фото: "Опиши что видишь на картинке"

## Технические особенности

- **Контекст диалогов**: Автоматическое управление историей сообщений (до 7 сообщений)
- **Прокси поддержка**: HTTP и SOCKS прокси с аутентификацией
- **Безопасность**: API ключи через переменные окружения
- **Логирование**: Подробные логи для отладки и мониторинга

## Структура проекта

```
src/main/java/ru/practicum/
├── TelegramBotApplication.java     # Главный класс приложения
├── client/                         # AI клиенты
│   ├── AiClient.java              # Интерфейс AI клиента
│   ├── AiClientFactory.java       # Фабрика клиентов
│   ├── AnthropicClient.java       # Claude API клиент
│   └── OpenAiClient.java          # OpenAI API клиент
├── config/                        # Конфигурационные классы
│   ├── ClaudeConfig.java          # Конфигурация Claude
│   ├── OpenAiConfig.java          # Конфигурация OpenAI
│   ├── ProxyConfig.java           # Конфигурация прокси
│   └── TelegramBotConfig.java     # Конфигурация Telegram
├── service/
│   └── TelegramChatService.java   # Основной сервис бота
└── utils/
    └── ConversationContext.java   # Управление контекстом
```

## Разработка

Проект использует:
- **Java 22** - Современные возможности языка
- **Spring Boot 3.3+** - Основной фреймворк
- **Apache HTTP Client 5** - HTTP клиент с поддержкой прокси
- **Jackson** - JSON сериализация/десериализация
- **Lombok** - Уменьшение boilerplate кода
- **Telegram Bot API 6.8** - Взаимодействие с Telegram
