# BuhloWar Android App - Push Notification Client

## Структура проекта

```
android/
└── BuhloWarApp/
    ├── build.gradle              # Project level
    ├── settings.gradle
    ├── gradle.properties
    └── app/
        ├── build.gradle         # App level
        ├── google-services.json # Firebase config
        └── src/main/
            ├── AndroidManifest.xml
            ├── java/ru/alcodist/buhlowar/
            │   ├── MainActivity.kt
            │   └── BuhloWarFCMService.kt
            └── res/
                ├── layout/activity_main.xml
                ├── drawable/ic_notification.xml
                └── values/
                    ├── strings.xml
                    ├── colors.xml
                    └── themes.xml
```

## Установка

1. Открыть проект в Android Studio
2. Подключить телефон в режиме разработчика
3. Run → Run 'app'

## Настройка Firebase

1. Firebase Console → Project Settings → Cloud Messaging
2. Скопировать **Server key**
3. Вставить в api.php (константа FCM_SERVER_KEY)

## Как работает

1. Приложение получает FCM токен
2. Отправляет токен на сервер (register_device)
3. Сервер сохраняет токены устройств
4. При аварии сервер отправляет push всем устройствам
