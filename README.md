# Notification Spy

[Available on Google Play](https://play.google.com/store/apps/details?id=com.darkapps.notificationspy)

An Android app that forwards push notifications to a webhook URL, turning your phone into an automation trigger.

## What it does

When a notification arrives from any app you choose, Notification Spy captures it and HTTP POSTs a JSON payload to a webhook endpoint you configure. You control which apps are monitored and where events are sent — no data ever goes to a third-party server.

Typical uses:
- Trigger a backend workflow when a banking app notifies you of a transaction
- Bridge phone events into tools like n8n, Make, Zapier, or a custom server
- Log notifications to a database for auditing or analytics

## Key principles

**User-controlled.** You explicitly select which apps' notifications are forwarded. Nothing is captured automatically.

**Your endpoint, your data.** Payloads go to a URL you provide. The app has no backend of its own and retains no data beyond local delivery history.

**Reliable delivery.** Failed webhook calls are retried with exponential backoff (up to 5 attempts) using WorkManager, so events survive network outages.

**Simple setup.** Enter a webhook URL or scan a QR code. A deep-link scheme (`notificationspy://webhook?url=...`) lets external tools configure the app in one tap.

## Webhook payload

```json
{
  "eventId": "uuid",
  "pkgName": "com.example.app",
  "title": "Notification title",
  "text": "Notification body",
  "timestamp": 1700000000000,
  "details": { "...": "raw Android extras" }
}
```

## Tech stack

- **Kotlin + Jetpack Compose** — UI
- **NotificationListenerService** — notification access
- **WorkManager** — reliable background delivery with retry
- **Room** — local event history
- **DataStore** — settings persistence (webhook URL, allowed packages)

## Permissions

- `BIND_NOTIFICATION_LISTENER_SERVICE` — required to read notifications; granted explicitly by the user in system settings
- `INTERNET` — to POST payloads to the webhook

## QR code format

Encode a plain `https://` URL to scan it in-app, or use the deep-link format to configure the app directly from a QR scan:

```
notificationspy://webhook?url=https://your-endpoint.example.com/hook
```
