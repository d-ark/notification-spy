# Privacy Policy

**Notification Spy**
Last updated: June 21, 2026

## Overview

Notification Spy is a utility app that forwards Android notifications to a webhook URL you configure. This policy explains what data the app handles and how.

## Data the app accesses

The app uses Android's Notification Listener permission to read notifications from apps you explicitly select. This includes the notification title, body text, and source app name.

## Where your data goes

Notification content is sent exclusively to the webhook URL you provide in the app settings. The app has no backend server of its own — your data never passes through any infrastructure controlled by the developer.

## Local storage

The app stores a local history of forwarded notifications on your device (title, body, delivery status, timestamp). This data does not leave your device except via the webhook you configured.

## What the app does not do

- It does not send data to the developer or any third party
- It does not include analytics, crash reporting, or advertising SDKs
- It does not access notifications from apps you have not explicitly enabled
- It does not store notification content in any cloud service

## Your control

You can revoke notification access at any time in Android system settings (Settings → Notifications → Notification access). Removing the app deletes all locally stored history.

## Contact

If you have any questions about this policy, open an issue on the project repository.
