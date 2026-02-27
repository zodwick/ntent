require("dotenv").config();

const express = require("express");
const cors = require("cors");
const whatsapp = require("./whatsapp");
const letterboxd = require("./letterboxd");

const app = express();
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 3000;

// Health check
app.get("/health", (req, res) => {
  res.json({
    status: "ok",
    whatsapp: whatsapp.getStatus(),
    letterboxd: letterboxd.getStatus(),
  });
});

// Send WhatsApp message
app.post("/whatsapp", async (req, res) => {
  const { message, contacts } = req.body;

  if (!message || !contacts || !Array.isArray(contacts)) {
    return res.status(400).json({
      error: "Required: { message: string, contacts: string[] }",
    });
  }

  try {
    const results = [];
    for (const number of contacts) {
      try {
        await whatsapp.sendMessage(number, message);
        results.push({ number, status: "sent" });
      } catch (err) {
        results.push({ number, status: "failed", error: err.message });
      }
    }
    res.json({ results });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Add movie to Letterboxd watchlist
app.post("/letterboxd", async (req, res) => {
  const { movie, year } = req.body;

  if (!movie) {
    return res.status(400).json({ error: "Required: { movie: string, year?: string }" });
  }

  try {
    const result = await letterboxd.addToWatchlist(movie, year);
    res.json({ success: true, ...result });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Start server and initialize clients
async function start() {
  // Start listening first so health check works immediately
  app.listen(PORT, () => {
    console.log(`ScrnStr server running on port ${PORT}`);
  });

  // Initialize clients in background (don't block server start)
  whatsapp.init();
  letterboxd.login().catch((err) => {
    console.error("Letterboxd login error:", err.message);
  });
}

start();
