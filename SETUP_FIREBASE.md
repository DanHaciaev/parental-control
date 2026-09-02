# Настройка Firebase (сделать один раз перед первым запуском)

Проект уже собирается с "заглушечными" `google-services.json` (специально невалидными — только чтобы Gradle не падал). Реальную синхронизацию между телефонами они не дадут — их нужно заменить на настоящие.

## 1. Создать проект Firebase

1. Открыть https://console.firebase.google.com, войти под любым Google-аккаунтом (можно тем же, что будет использоваться как email родителя, но это необязательно — это разные вещи).
2. **Add project** → назвать, например, `parental-control` → отключить Google Analytics (не нужен, лишний расход бесплатной квоты) → Create.
3. Важно: не подключать биллинг-аккаунт — план **Spark (бесплатный)** используется по умолчанию, ничего дополнительно включать не нужно.

## 2. Зарегистрировать два Android-приложения в этом проекте

Project settings → **Add app** → Android, дважды (для мамы и для сына):

| Приложение | Package name (обязательно указать точно) |
|---|---|
| Мама | `com.teo.parent` |
| Сын | `com.teo.child` |

Nickname — любой, SHA-1 на этом этапе не нужен.

После регистрации каждого приложения Firebase предложит скачать `google-services.json` — скачать и заменить им:
- заглушку в `C:\Proj_Teo\app-parent\google-services.json` (для `com.teo.parent`)
- заглушку в `C:\Proj_Teo\app-child\google-services.json` (для `com.teo.child`)

## 3. Включить Authentication

Build → Authentication → Get started → вкладка **Sign-in method** → включить два провайдера:
- **Email/Password** (для входа мамы)
- **Anonymous** (для сына — без экрана логина на его телефоне)

## 4. Включить Firestore

Build → Firestore Database → Create database → выбрать любой регион (ближе к Москве — `eur3 (europe-west)`) → режим **Production mode** (правила уже заготовлены в `firestore.rules`, стартовать в production безопаснее, чем в открытом test mode).

## 5. Загрузить правила безопасности Firestore

Проще всего через Firebase CLI (один раз локально):

```
npm install -g firebase-tools
firebase login
firebase init firestore   # выбрать существующий проект, оставить путь firestore.rules как есть
firebase deploy --only firestore:rules
```

Либо вручную: Firestore Database → Rules → вставить содержимое `C:\Proj_Teo\firestore.rules` → Publish.

## 6. Проверка

После замены обоих `google-services.json` пересобрать:

```
./gradlew :app-parent:assembleDebug :app-child:assembleDebug
```

APK появятся в `app-parent/build/outputs/apk/debug/` и `app-child/build/outputs/apk/debug/`.

---

Это разовая настройка — дальше вся работа идёт в коде (Фазы 1–6 из плана), Firebase-проект трогать больше не придётся, кроме как посмотреть логи/квоты при необходимости.
