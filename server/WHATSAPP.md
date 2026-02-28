# WhatsApp Messaging Integration

Sends WhatsApp messages via `whatsapp-web.js` (Puppeteer-based WhatsApp Web automation).

## Setup

1. Clear any stale session: `rm -rf server/.wwebjs_auth`
2. Start the server: `node index.js`
3. Scan the QR code in terminal with your phone: WhatsApp → **Linked Devices** → **Link a Device**
4. Wait for "WhatsApp client ready" in the logs

> Session persists via `LocalAuth` in `.wwebjs_auth/` — you only scan once. If it breaks, delete that folder and re-scan.

## API

```
POST /whatsapp
Content-Type: application/json

{ "message": "Check this out!", "contacts": ["919876543210", "919876543211"] }
```

- `message` (required) — text to send
- `contacts` (required) — array of phone numbers with country code (no `+`)

Returns:
```json
{ "results": [{ "number": "919876543210", "status": "sent" }] }
```

## How it works

- `whatsapp-web.js` launches a headless Puppeteer browser running WhatsApp Web
- First run requires QR scan, subsequent runs use saved session
- Messages sent via WhatsApp Web protocol (not official API)

## Troubleshooting

- **No QR code appearing:** Delete `.wwebjs_auth/` and restart
- **"Browser already running" error:** Kill stale chrome processes or delete `.wwebjs_auth/session/SingletonLock`
- **Disconnected after a while:** Restart server, may need to re-scan QR

## Dependencies

- `whatsapp-web.js` (includes puppeteer)
- `qrcode-terminal`
