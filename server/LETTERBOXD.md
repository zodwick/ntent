# Letterboxd Watchlist Integration

Adds movies to your Letterboxd watchlist via Puppeteer browser automation (bypasses Cloudflare).

## Setup

1. Log into [letterboxd.com](https://letterboxd.com) in Chrome
2. Open DevTools (`Cmd+Option+I`) → **Network** tab → refresh page
3. Click the first request → Headers → copy the **Cookie** header value
4. Paste into `server/ltrbxd_cookie.txt`

> **Important:** Use the Network tab cookie, NOT `document.cookie` — the Network tab includes HttpOnly session cookies that JS can't access.

## API

```
POST /letterboxd
Content-Type: application/json

{ "movie": "Inception", "year": "2010" }
```

- `movie` (required) — film name to search
- `year` (optional) — disambiguates if multiple results

Returns: `{ "success": true, "title": "Inception", "year": "2010", "slug": "/film/inception/" }`

## How it works

- Puppeteer (non-headless + stealth plugin) launches offscreen with your cookies
- Searches Letterboxd, navigates to film page, clicks the watchlist button
- Fresh page per request to avoid stale frame issues

## Cookie refresh

Cookies expire after a few weeks. Re-paste from Chrome DevTools Network tab and restart the server.

## Dependencies

- `puppeteer` (already installed via whatsapp-web.js)
- `puppeteer-extra` + `puppeteer-extra-plugin-stealth`
