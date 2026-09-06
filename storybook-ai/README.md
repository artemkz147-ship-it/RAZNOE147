# Моя сказка — AI StoryBook

Android-прототип персональных иллюстрированных детских книг.

## Уже работает
- пошаговый мастер: имя, возраст, фото, интересы, семья, пожелания;
- выбор темы, художественного стиля и объёма;
- безопасный выбор фото через Android system picker;
- демо-режим без сервера;
- реальный режим через защищённый backend;
- последовательная генерация сюжета, единого образа героя и иллюстраций;
- перелистывание книги и редактирование текста страницы;
- повторная генерация текущей иллюстрации;
- экспорт/печать через Android Print Framework (можно сохранить PDF).

## Архитектура
APK не содержит ключ OpenAI. WebView-приложение обращается к Cloudflare Worker (`backend/worker.js`), а Worker использует секрет `OPENAI_API_KEY`.

Перед локальной сборкой распакуйте `app/src/main/assets/index.html.gz` в `index.html`. Для backend распакуйте `backend/worker.js.gz` в `worker.js`. GitHub Actions делает это автоматически.

## Backend
```bash
gzip -dc backend/worker.js.gz > backend/worker.js
cd backend
npx wrangler secret put OPENAI_API_KEY
npx wrangler deploy
```

После деплоя в Android-приложении откройте шестерёнку и вставьте HTTPS URL Worker.

## Сборка Android
```bash
gzip -dc app/src/main/assets/index.html.gz > app/src/main/assets/index.html
gradle :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
