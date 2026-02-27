require("dotenv").config();
const fetch = require("node-fetch");

(async () => {
  try {
    // Step 1: Get CSRF
    const pageRes = await fetch("https://letterboxd.com/sign-in/", {
      headers: {
        "User-Agent":
          "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
      },
      redirect: "manual",
    });
    const pageCookies = pageRes.headers.raw()["set-cookie"] || [];
    // Filter out the clearing cookie (Max-Age=0), keep the real one
    const csrfCookie = pageCookies.find(
      (c) => c.includes("com.xk72.webparts.csrf=") && !c.includes("Max-Age=0")
    );
    if (!csrfCookie) {
      console.log("All cookies:", pageCookies);
      throw new Error("No CSRF cookie found");
    }
    const csrfToken = csrfCookie.split("=")[1].split(";")[0];
    // Only use the valid CSRF cookie, not the clearing one
    const cookieStr = `com.xk72.webparts.csrf=${csrfToken}`;
    console.log("CSRF token:", csrfToken);
    console.log("Cookie string:", cookieStr);

    // Step 2: Login
    const loginRes = await fetch("https://letterboxd.com/user/login.do", {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "User-Agent":
          "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        Cookie: cookieStr,
        Referer: "https://letterboxd.com/sign-in/",
        Origin: "https://letterboxd.com",
      },
      body: new URLSearchParams({
        __csrf: csrfToken,
        username: process.env.LETTERBOXD_USERNAME,
        password: process.env.LETTERBOXD_PASSWORD,
      }),
      redirect: "manual",
    });
    console.log("\nLogin status:", loginRes.status);
    console.log("Location:", loginRes.headers.get("location"));
    const loginCookies = loginRes.headers.raw()["set-cookie"] || [];
    console.log("Login set-cookie count:", loginCookies.length);
    loginCookies.forEach((c, i) =>
      console.log(`  Cookie ${i}: ${c.substring(0, 80)}...`)
    );

    const body = await loginRes.text();
    if (body.length > 0) {
      console.log("\nBody (first 300):", body.substring(0, 300));
    }
  } catch (err) {
    console.error("Error:", err);
  }
})();
