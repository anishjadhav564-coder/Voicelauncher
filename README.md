# Voice Launcher (Android)

Runs quietly in the background (foreground service with a notification). When it hears
your wake word (default: "hey assistant"), it opens your phone's **default digital
assistant**, the same one the power button opens. Set that to ChatGPT in
Settings > Apps > Default apps > Digital assistant app.

While the assistant is open, this app pauses its own listening so the microphone is free.
Tap **Listen again** in the notification to resume wake-word listening.

## Setup on the phone

1. Install the ChatGPT app and set it as the digital assistant.
2. Open this app, tap **Start assistant**, and allow: microphone, notifications,
   and **Display over other apps** (needed to open the assistant from the background).
3. Tap Start again. Say the wake word.
4. Settings > Apps > Voice Assistant > Battery > **Unrestricted**.

## Limits

- This app only opens the assistant. It cannot keep ChatGPT's voice session alive or
  control it. Whether the session continues with the screen locked depends on ChatGPT
  and your phone.
- On a locked phone, Android may ask you to unlock before an assistant can open.
- Wake-word detection uses Android's built-in recognizer, so it can miss the phrase and
  drains battery. A dedicated engine such as Picovoice Porcupine is more reliable.
- `OpenAiClient.kt` is left over from the earlier version and is not used.
