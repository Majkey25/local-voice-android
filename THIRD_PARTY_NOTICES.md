# Third-party notices

Local Voice uses or downloads the following components. Their original licences apply to them; the project MIT licence does not replace those licences.

- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) Android runtime and Whisper model exports — Apache-2.0 project licence; model metadata remains authoritative.
- [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) Android runtime — Apache-2.0.
- [Qwen3-0.6B LiteRT model](https://huggingface.co/litert-community/Qwen3-0.6B) — use and redistribution remain subject to the model card and included licence.
- AndroidX, Jetpack Compose, and Material 3 — Apache-2.0.
- JUnit 4 — Eclipse Public License 1.0.

Model files are not stored in this repository. The app downloads pinned files from the URLs in `ModelPack.kt`, verifies their exact byte count and SHA-256, and stores them in app-private storage.
