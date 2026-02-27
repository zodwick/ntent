const { Client, LocalAuth } = require("whatsapp-web.js");
const qrcode = require("qrcode-terminal");

let status = "disconnected";

const client = new Client({
  authStrategy: new LocalAuth(),
  puppeteer: { headless: true },
});

client.on("qr", (qr) => {
  console.log("Scan this QR code to log in to WhatsApp:");
  qrcode.generate(qr, { small: true });
});

client.on("ready", () => {
  status = "connected";
  console.log("WhatsApp client ready");
});

client.on("disconnected", (reason) => {
  status = "disconnected";
  console.log("WhatsApp disconnected:", reason);
});

client.on("auth_failure", () => {
  status = "disconnected";
  console.log("WhatsApp auth failed");
});

async function sendMessage(number, message) {
  if (status !== "connected") {
    throw new Error("WhatsApp client is not connected");
  }
  const chatId = `${number}@c.us`;
  await client.sendMessage(chatId, message);
}

function getStatus() {
  return status;
}

function init() {
  client.initialize().catch((err) => {
    console.error("WhatsApp init failed:", err.message);
    console.log("WhatsApp will be unavailable. Restart server to retry.");
  });
}

module.exports = { client, sendMessage, getStatus, init };
