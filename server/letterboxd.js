// Letterboxd watchlist integration via Puppeteer browser automation.
// Uses cookies from ltrbxd_cookie.txt (grab from Chrome DevTools Network tab).
// See LETTERBOXD.md for setup & cookie refresh instructions.
const puppeteer = require("puppeteer-extra");
const StealthPlugin = require("puppeteer-extra-plugin-stealth");
puppeteer.use(StealthPlugin());

const BASE_URL = "https://letterboxd.com";

let browser = null;
let status = "logged_out";
let parsedCookies = [];

async function getPage() {
  // Always create a fresh page to avoid detached frame issues
  const page = await browser.newPage();
  await page.setUserAgent(
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
  );
  await page.setCookie(...parsedCookies);
  return page;
}

async function gotoWithCF(page, url) {
  // Navigate and wait for Cloudflare challenge to clear
  await page.goto(url, { waitUntil: "networkidle2", timeout: 30000 });
  // If Cloudflare challenge page, wait for it to resolve
  const title = await page.title();
  if (title.includes("Just a moment")) {
    console.log("Cloudflare challenge detected, waiting...");
    await page.waitForFunction(
      () => !document.title.includes("Just a moment"),
      { timeout: 15000 }
    );
    await new Promise((r) => setTimeout(r, 2000));
  }
}

async function login() {
  try {
    browser = await puppeteer.launch({
      headless: false, // non-headless to pass Cloudflare
      args: [
        "--no-sandbox",
        "--disable-setuid-sandbox",
        "--window-size=1280,800",
        "--window-position=-2000,-2000", // offscreen so it doesn't bother user
      ],
    });

    // Load cookies from file
    const fs = require("fs");
    const path = require("path");
    const cookieFile = path.join(__dirname, "ltrbxd_cookie.txt");
    let rawCookies;
    try {
      rawCookies = fs.readFileSync(cookieFile, "utf-8").trim();
    } catch {
      console.log("Letterboxd: no cookie file at", cookieFile);
      return;
    }

    // Parse cookie string into puppeteer format
    parsedCookies = rawCookies.split("; ").map((c) => {
      const [name, ...rest] = c.split("=");
      return {
        name: name.trim(),
        value: rest.join("="),
        domain: ".letterboxd.com",
      };
    });

    // Verify login
    const page = await getPage();
    await gotoWithCF(page, BASE_URL);
    const loggedIn = await page.evaluate(() =>
      document.body.innerHTML.includes("zodwick")
    );
    await page.close();

    if (loggedIn) {
      status = "logged_in";
      console.log("Letterboxd logged in via browser cookies + puppeteer");
    } else {
      console.log("Letterboxd cookies may be expired - update ltrbxd_cookie.txt");
    }
  } catch (err) {
    console.error("Letterboxd init failed:", err.message);
  }
}

async function addToWatchlist(movie, year) {
  if (status !== "logged_in") {
    throw new Error("Not logged in to Letterboxd");
  }

  const page = await getPage();
  try {
    // Search for the film
    const searchUrl = `${BASE_URL}/search/${encodeURIComponent(movie)}/`;
    await gotoWithCF(page, searchUrl);

    // Get search results
    const results = await page.evaluate(() => {
      const items = [];
      document.querySelectorAll('a[href*="/film/"]').forEach((a) => {
        const href = a.getAttribute("href");
        const text = a.textContent.trim();
        if (
          href.match(/^\/film\/[^/]+\/$/) &&
          text.length > 0 &&
          !items.find((i) => i.slug === href)
        ) {
          const yearMatch = text.match(/\((\d{4})\)/);
          items.push({
            title: text.replace(/\(\d{4}\).*/, "").trim(),
            year: yearMatch ? yearMatch[1] : "",
            slug: href,
          });
        }
      });
      return items;
    });

    if (results.length === 0) {
      throw new Error(`No results found for "${movie}"`);
    }

    // Match by year if provided
    let match = results[0];
    if (year) {
      const yearMatch = results.find((r) => r.year === String(year));
      if (yearMatch) match = yearMatch;
    }

    // Navigate to film page
    await gotoWithCF(page, `${BASE_URL}${match.slug}`);

    // Check if already on watchlist
    const alreadyOnWatchlist = await page.evaluate(() => {
      const btn = document.querySelector("a.-watchlist");
      return btn?.classList.contains("remove-from-watchlist") || false;
    });

    if (alreadyOnWatchlist) {
      await page.close();
      return {
        title: match.title,
        year: match.year,
        slug: match.slug,
        note: "already on watchlist",
      };
    }

    // Click the watchlist button
    await page.evaluate(() => {
      const btn = document.querySelector("a.add-to-watchlist, a.-watchlist");
      if (btn) btn.click();
    });

    await new Promise((r) => setTimeout(r, 2000));

    // Verify
    const added = await page.evaluate(() => {
      const btn = document.querySelector("a.-watchlist");
      return btn?.classList.contains("remove-from-watchlist") || false;
    });

    await page.close();

    if (!added) {
      throw new Error("Watchlist click did not register");
    }

    console.log(`Added "${match.title}" (${match.year}) to watchlist`);
    return { title: match.title, year: match.year, slug: match.slug };
  } catch (err) {
    await page.close().catch(() => {});
    throw err;
  }
}

function getStatus() {
  return status;
}

module.exports = { login, addToWatchlist, getStatus };
