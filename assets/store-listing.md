# Play Store Listing

## Short description (80 chars max)

Any notification, forwarded to your webhook. Automate workflows from your phone.

## Full description

Notification Spy turns your Android phone into an automation trigger. Whenever a notification arrives from an app you choose, it immediately sends the notification content to a webhook URL you configure — no code required on the phone.

**How it works**
1. Grant notification access once in system settings
2. Enter your webhook URL (or scan a QR code)
3. Choose which apps to monitor
4. Every matching notification is POSTed as JSON to your endpoint

**Example uses**
• E-commerce app ships your order → log it to a spreadsheet or Notion database
• Smart home app sends an alert → trigger an automation in Home Assistant
• Any app sends a push → fire an n8n, Make, or Zapier workflow

**Reliable delivery**
Failed webhook calls are automatically retried with exponential backoff (up to 5 attempts), so events survive temporary network outages. A built-in history screen shows the delivery status of every event.

**Your data stays yours**
Notifications are sent only to the webhook URL you provide. The app has no backend of its own and shares nothing with third parties.

**Setup via QR code**
Configure the webhook URL by scanning a QR code — handy for deploying to multiple devices or sharing a setup with a team.

**Payload format**
Each delivery is a JSON POST containing the notification title, body text, source app package name, timestamp, and a unique event ID — everything you need to act on it downstream.

Notification Spy is built for developers, power users, and anyone who wants to bridge their Android phone with external systems.
