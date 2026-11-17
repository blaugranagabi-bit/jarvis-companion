# JARVIS Hybrid Companion

A mobile-first JARVIS-inspired interface combining a cinematic blue holographic core with a clean, modern assistant dashboard.

## Included
- Animated blue core and HUD styling
- Local time and system status
- Browser speech synthesis with an English (UK) preference
- Optional browser speech recognition where supported
- Command handoff to ChatGPT via a URL

## Important limitations
This is an interface prototype, not an embedded ChatGPT client. Commands open ChatGPT in the browser/app; the page cannot access your ChatGPT account, chat history, or native voice engine. Browser voice uses Android-installed voices. Speech recognition depends on Chrome and device support.

## Hosting
Upload `index.html`, `manifest.webmanifest`, and `sw.js` to the root of a public GitHub repository. In Settings > Pages, deploy from `main` and `/ (root)`. Open the published HTTPS URL in Chrome and choose Install app.
