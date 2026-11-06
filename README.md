# Twirlly

Short-video app that runs fully on the phone. Package: com.example.twirlly

- Sign-in page (ready for later: see Auth.kt, set `Auth.provider`), then name and unique username
- Feed, Discover, Inbox, Profile, Settings with log-out confirmation, LIVE screen
- Editor: multi-track timeline, keyframes, 18 filters, effects, transitions, text styles, 7 sticker packs, songs with waveform and beats
- No media is copied: posts keep links to gallery files. Limited storage: newest 40 posts, about 5 MB
- Premium: test switch in Premium.kt until payments are connected

Build: `gradle assembleDebug` (compileSdk 34, minSdk 24)
