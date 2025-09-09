# Telegram Claude Bot

**Telegram Claude Bot** — это Telegram-бот, использующий Anthropic Claude API для обработки сообщений и предоставления ответов на основе искусственного интеллекта. Бот поддерживает команды для получения информации, тестирования связи и взаимодействия с Claude AI. Проект написан на Java с использованием Spring Boot и библиотеки `telegrambots`.

## Возможности

- **Обработка сообщений**: Отправляйте текстовые сообщения, и бот ответит с помощью Claude AI.
- **Команды**:
  - `/start` — Приветственное сообщение с информацией о боте.
  - `/help` — Список возможностей Claude AI (написание текстов, программирование, перевод и т.д.).
  - `/info` — Информация о боте, включая используемую модель Claude и статус прокси.
  - `/test` — Проверка связи с Claude API.
- **Прокси-поддержка**: Настраиваемый прокси для обхода ограничений сети.
- **Анти-спам**: Ограничение частоты сообщений (3 секунды между сообщениями).

## Требования

- **Java**: 17 или выше
- **Maven**: 3.6.0 или выше
- **Telegram Bot Token**: Получите токен через [BotFather](https://t.me/BotFather)
- **Anthropic API Key**: Получите ключ в [аккаунте Anthropic](https://www.anthropic.com)
- (Опционально) Прокси-сервер для доступа к Anthropic API

## Установка

1. **Клонируйте репозиторий**:
   ```bash
   git clone https://github.com/your-username/your-repo.git
   cd your-repo
   ```
2. **Создайте конфигурационный файл**:
   Скопируйте src/main/resources/application-template.yml в src/main/resources/application.yml.
   Заполните application.yml вашими данными:
   ```yaml
   spring:
     main:
       web-application-type: none
   claude:
     base-url: "${CLAUDE_BASE_URL:https://api.anthropic.com}"
     api-key: "${CLAUDE_API_KEY:your-claude-api-key}"
     model: "${CLAUDE_MODEL:claude-3-haiku-20240307}"
   telegrambot:
     botToken: "${TELEGRAM_BOT_TOKEN:your-telegram-bot-token}"
     botUsername: "${TELEGRAM_BOT_USERNAME:@gptdelbot}"
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

   **(Опционально)** Настройте переменные окружения:
     Вместо application.yml вы можете использовать переменные окружения:
    ```bash
    export TELEGRAM_BOT_TOKEN="your-telegram-bot-token"
    export CLAUDE_API_KEY="your-claude-api-key"
    export PROXY_ENABLED="true"
    export PROXY_HOST="your-proxy-host"
    export PROXY_PORT="your-proxy-port"
    export PROXY_USERNAME="your-proxy-username"
    export PROXY_PASSWORD="your-proxy-password"
    ```

4. **Запустите приложение**:
   ```bash
   mvn spring-boot:run
   ```
