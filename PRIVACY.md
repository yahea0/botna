# Botna Privacy Notice — Draft for Review

**Last updated:** 20 October 2026  
**Developer contact:** يحيى النباهين · Telegram: [@yahea21](https://t.me/yahea21)

> This is a source-based draft for the app publisher to review. Host an approved public URL and verify it against the final APK and the policies of each AI provider before publishing.

## Information stored on the device

Botna stores saved scripts, agent tasks, image templates, agent explanations, diagnostic logs, and optional diagnostic screenshots in the app's private local storage. API keys are stored locally using Android Keystore-backed encryption. Users can delete scripts and logs from within the app; uninstalling the app generally removes its private local data.

## Information sent to third parties

When the user selects an AI API provider and requests analysis, Botna sends the prompt and other information needed for that request to the configured provider. If screenshot context is enabled, a screenshot may be sent to that provider. The selected provider's privacy, retention, and billing terms apply to those requests. Do not include passwords, payment details, or other sensitive information in prompts or screenshots unless absolutely necessary.

Botna may contact an endpoint that the user explicitly configures for API use, internet checks, or URL-related automation. Endpoint responses and internet availability are outside the developer's control.

## Accessibility permission and automation

The user must enable Botna's Accessibility service in Android Settings. While enabled, the service can inspect visible interface elements and perform supported actions that the user or a saved script/agent task requests. The user should review generated steps and personally supervise sensitive actions such as payments, purchases, account changes, message sending, or deletion.

## Diagnostics and screenshots

Diagnostic logs are stored locally. Diagnostic screenshots can be disabled in the app's log settings. When enabled, a screenshot may be stored with a failure record to help the user understand what happened. A screenshot can contain private content from the current screen.

## Data sharing, retention, and contact

The source currently contains no analytics SDK configured by this project. AI request data is sent to the provider the user configures, not to the developer's Telegram account. This statement must be rechecked against the final release build, any added SDKs, and all configured endpoints before publication.

For privacy questions, contact **يحيى النباهين** at **[@yahea21](https://t.me/yahea21)**.
