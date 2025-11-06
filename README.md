# Telegram AI Bot

**Telegram AI Bot** — это универсальный Telegram-бот, поддерживающий интеграцию с восемью AI-провайдерами (OpenAI, Anthropic, xAI, Google Gemini, DeepSeek, Perplexity, Meta Llama, Kimi) для обработки текстовых сообщений и изображений. Бот предоставляет возможность переключения между различными моделями в реальном времени и сохраняет контекст диалогов. Проект написан на Java с использованием Spring Boot и библиотеки `telegrambots`.

## Возможности

- **Мультипровайдерная поддержка**: Работа с 16 AI-моделями от 8 провайдеров.
- **Обработка контента**: 
  - Текстовые сообщения с поддержкой контекста.
  - Анализ и описание изображений (включая vision-модели).
  - Извлечение текста из документов и скриншотов.
- **Динамическое переключение моделей**: Выбор AI-модели через inline-клавиатуру прямо в чате.
- **Эффективное управление ресурсами**:
  - Кэширование клиентов для переиспользования HTTP-соединений
  - Автоматическая инвалидация кэша при смене модели
  - Многопоточная безопасность через ConcurrentHashMap
  - Изоляция контекста и клиентов между пользователями
- **Команды**:
  - `/start` — Приветственное сообщение с инструкциями по использованию.
  - `/info` — Информация о боте, текущей модели и статусе прокси.
  - `/model` — Выбор AI-модели из доступных вариантов.
  - `/history` — Просмотр текущего контекста беседы.
  - `/clear` — Очистка контекста диалога.
- **Контекст диалогов**: Сохранение истории до 7 сообщений для каждого чата.
- **Прокси-поддержка**: Настраиваемый прокси для обхода региональных ограничений.
- **Markdown поддержка**: Автоматическое форматирование кода в HTML.

## Поддерживаемые модели

### OpenAI Models
- **GPT-5 Nano** (`gpt-5-nano`) - Быстрый и экономичный.
- **GPT-5** (`gpt-5`) - Полнофункциональная версия.

### Anthropic Claude Models
- **Claude 4.5 Haiku** (`claude-haiku`) - Быстрый и доступный.
- **Claude 4.5 Sonnet** (`claude-sonnet`) - Самый продвинутый с vision.

### xAI Grok Models
- **Grok 4 Fast** (`grok-4-fast`) - Оптимизированная для скорости.
- **Grok 4 Code** (`grok-4-code`) - Специализация на коде.

### Perplexity Models
- **Sonar** (`sonar`) - Базовая модель.
- **Sonar Pro** (`sonar-pro`) - Расширенная версия.

### Google Gemini Models
- **Gemini 2.5 Flash** (`gemini-flash`) - Быстрая обработка.
- **Gemini 2.5 Pro** (`gemini-pro`) - Продвинутые возможности.

### DeepSeek Models
- **DeepSeek 3.1** (`deepseek`) - Общего назначения.
- **DeepSeek Reasoning** (`deepseek-reasoning`) - С улучшенным reasoning.

### Meta Llama Models
- **Llama 4 Scout** (`llama-scout`) - Лёгкая версия.
- **Llama 4 Maverick** (`llama-maverick`) - Полнофункциональная.

### Kimi Models
- **Kimi K2** (`kimi-k2`) - Поддержка больших контекстов.

## Требования

- **Java**: 22 или выше.
- **Maven**: 3.6.0 или выше.
- **Telegram Bot Token**: Получите токен через [BotFather](https://t.me/BotFather).
- **AI API Keys**:
  - **OpenAI API Key**: [аккаунт OpenAI](https://www.openai.com).
  - **Anthropic API Key**: [аккаунт Anthropic](https://www.anthropic.com).
  - **xAI API Key**: [аккаунт xAI](https://console.x.ai).
  - **Google API Key**: [Google AI Studio](https://aistudio.google.com).
  - **DeepSeek API Key**: [DeepSeek Platform](https://platform.deepseek.com).
  - **Perplexity API Key**: [Perplexity AI](https://www.perplexity.ai).
  - **Groq API Key**: [Groq Console](https://console.groq.com).
- (Опционально) Прокси-сервер для доступа к AI API.

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
   
   ai:
     default-model: "gpt-5-nano"
   # Провайдеры
     providers:
       openai:
         baseUrl: "https://api.openai.com/v1"
         apiKey: "${OPENAI_API_KEY:your-openai-api-key}"
       
       anthropic:
         baseUrl: "https://api.anthropic.com/v1"
         apiKey: "${ANTHROPIC_API_KEY:your-anthropic-api-key}"
       
       grok:
         baseUrl: "https://api.x.ai/v1"
         apiKey: "${GROK_API_KEY:your-xai-api-key}"
       
       perplexity:
         baseUrl: "https://api.perplexity.ai"
         apiKey: "${PERPLEXITY_API_KEY:your-perplexity-api-key}"
       
       gemini:
         baseUrl: "https://generativelanguage.googleapis.com/v1beta"
         apiKey: "${GEMINI_API_KEY:your-gemini-api-key}"
       
       deepseek:
         baseUrl: "https://api.deepseek.com/v1"
         apiKey: "${DEEPSEEK_API_KEY:your-deepseek-api-key}"
       
       groq:
         baseUrl: "https://api.groq.com/openai/v1"
         apiKey: "${GROQ_API_KEY:your-groq-api-key}"
       
       openrouter:
         baseUrl: "https://openrouter.ai/api/v1"
         apiKey: "${OPENROUTER_API_KEY:your-openrouter-api-key}"
     
     # Модели
     models:
       gpt-5-nano:
         provider: "openai"
         modelName: "gpt-5-nano"
       
       gpt-5:
         provider: "openai"
         modelName: "gpt-5"
       
       claude-haiku:
         provider: "anthropic"
         modelName: "claude-3-5-haiku-20241022"
       
       claude-sonnet:
         provider: "anthropic"
         modelName: "claude-sonnet-4-20250514"
       
       grok-4-fast:
         provider: "grok"
         modelName: "grok-4-fast"
       
       grok-4-code:
         provider: "grok"
         modelName: "grok-4-code"
       
       sonar:
         provider: "perplexity"
         modelName: "sonar"
       
       sonar-pro:
         provider: "perplexity"
         modelName: "sonar-pro"
       
       gemini-flash:
         provider: "gemini"
         modelName: "gemini-2.5-flash-latest"
       
       gemini-pro:
         provider: "gemini"
         modelName: "gemini-2.5-pro-latest"
       
       deepseek:
         provider: "deepseek"
         modelName: "deepseek-chat"
       
       deepseek-reasoning:
         provider: "deepseek"
         modelName: "deepseek-reasoner"
       
       llama-scout:
         provider: "groq"
         modelName: "llama-4-scout"
       
       llama-maverick:
         provider: "groq"
         modelName: "llama-4-maverick"
       
       kimi-k2:
         provider: "openrouter"
         modelName: "moonshot/kimi-k2"

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

4. **Запустите приложение**:
   ```bash
   mvn spring-boot:run
   ```

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

## Разработка

Проект использует:
- **Java 22** - Современные возможности языка
- **Spring Boot 3.3+** - Основной фреймворк
- **Apache HTTP Client 5** - HTTP клиент с поддержкой прокси
- **Jackson** - JSON сериализация/десериализация
- **Lombok** - Уменьшение boilerplate кода
- **Telegram Bot API 6.8** - Взаимодействие с Telegram
