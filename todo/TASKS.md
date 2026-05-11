# TChat Optimization Todo - 2026-05-10

Source document: `C:/Users/Administrator/AndroidStudioProjects/TChat/项目优化建议-2026-05-10.md`

If context is compacted, reread this file first, then continue from the first `in_progress` or `pending` item.

## Execution Rules

- Do not copy backend-only source or secrets into this Android repository.
- Official TChat NAAPI requests must remain on `https://t.naapi.cc`.
- Prefer small verified changes over broad unverified refactors.
- Update this file after each task is completed or blocked.

## Current Round

- [x] Read optimization proposal and repository status.
- [x] Apply Gradle build-performance settings.
- [x] Move remaining hard-coded dependencies into `gradle/libs.versions.toml`.
- [x] Fix embedding provider cancellation semantics.
- [x] Move group chat TTS calls onto shared `TtsService`.
- [x] Clean high-value lint warnings that are narrow and safe.
- [x] Add focused tests for changed cancellation behavior where feasible.
- [x] Run Gradle verification: `testDebugUnitTest`, `lintDebug`, `:app:assembleDebug`.
- [x] Update `项目优化建议-2026-05-10.md` with completed changes and verification results.

## Verification Results

- 2026-05-11: `.\gradlew.bat :network:testDebugUnitTest --no-daemon` passed.
- 2026-05-11: `.\gradlew.bat testDebugUnitTest --no-daemon` passed.
- 2026-05-11: `.\gradlew.bat lintDebug --no-daemon` passed.
- 2026-05-11: `.\gradlew.bat :app:assembleDebug --no-daemon` passed.
- 2026-05-11: Debug APK generated at `app/build/outputs/apk/debug/app-debug.apk`, size about `70.5MB`.

## Deferred Large Work

These are intentionally not first-round tasks because they need larger product and regression review:

- Split very large UI and repository files.
- Move OCR, JungleHelper, and Mermaid into flavors or optional delivery.
- Enable release R8 shrink plus `-dontobfuscate` after full manual path testing.
- Add full Room migration instrumentation coverage across old schema versions.
- Migrate persisted JSON formats to `kotlinx.serialization`.

---

# Global Chat Search Todo - 2026-05-11

Source documents:

- `C:/Users/Administrator/Downloads/taskaa.md`
- `C:/Users/Administrator/AndroidStudioProjects/TChat/TChat_新功能制作建议-2026-05-11.md`

If context is compacted, reread `C:/Users/Administrator/Downloads/taskaa.md`, this file, and the feature proposal before continuing.

## Current Round

- [x] Read task file, repository instructions, and feature proposal.
- [x] Select the recommended next task: global chat content search MVP.
- [x] Add Room-level message search over chat title, message parts, variants, model, provider, and group assistant metadata.
- [x] Add data/repository mapping with search snippets.
- [x] Upgrade drawer search to show message-level results and route clicks into the matching single chat or group chat.
- [x] Add focused tests for search behavior.
- [x] Run Gradle verification.
- [x] Record completion and verification results.

## Verification Results

- 2026-05-11: `.\gradlew.bat :data:testDebugUnitTest --no-daemon` passed.
- 2026-05-11: `.\gradlew.bat :app:assembleDebug --no-daemon` passed.
- 2026-05-11: `.\gradlew.bat testDebugUnitTest --no-daemon` passed.
- 2026-05-11: `.\gradlew.bat lintDebug --no-daemon` passed after rerunning with a longer timeout. The first lint attempt hit the 3-minute tool timeout and did not report a lint failure.

## Completed Scope

- Drawer search now still filters chat and group titles, and also searches message content.
- Message search covers serialized message parts, tool calls/results, variants, model/provider metadata, chat titles, and group assistant names.
- Search result clicks route to the matching single chat or the group chat associated with the message.

---

# Performance Optimization Todo - 2026-05-11

Source document: `C:/Users/Administrator/AndroidStudioProjects/TChat/TChat_性能优化建议-2026-05-11.md`

If context is compacted, reread this file and the source document before continuing from the first `in_progress` or `pending` item.

## Current Round

- [x] Read the performance proposal and inspect the current repository state.
- [x] Reduce streaming Markdown/Markwon work while messages are still generating.
- [x] Add `LazyColumn` content types for chat list rows.
- [x] Move Room entity-to-model conversion off the UI collector path where safe.
- [x] Optimize knowledge-base vector search without a schema migration.
- [x] Limit historical media Base64 encoding in AI context construction.
- [x] Check release shrink configuration and add narrow keep rules if needed.
- [x] Run focused Gradle verification.
- [x] Record completed scope, verification results, and remaining larger work.

## Verification Results

- 2026-05-11: `.\gradlew.bat :data:compileDebugKotlin --no-daemon --console=plain` passed.
- 2026-05-11: `.\gradlew.bat :data:testDebugUnitTest --no-daemon --console=plain` passed.
- 2026-05-11: `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` passed.
- 2026-05-11: `.\gradlew.bat :app:assembleRelease --no-daemon --console=plain` passed with R8 and resource shrinking enabled.
- 2026-05-11: `.\gradlew.bat :data:compileDebugKotlin :data:testDebugUnitTest testDebugUnitTest lintDebug --no-daemon --console=plain` passed.
- 2026-05-11: Final `.\gradlew.bat :app:assembleRelease --no-daemon --console=plain` passed after the KnowledgeService warning cleanup.

## Completed Scope

- Streaming assistant messages render as selectable plain Compose text while generating; completed messages still use Markwon/Mermaid/Thinking rendering.
- Streaming update throttle changed from 48ms to 96ms.
- Chat `LazyColumn` rows now declare content types for loading rows, normal messages, and streaming messages.
- Chat/Message Room Flow JSON conversion now runs behind `flowOn(Dispatchers.Default)`.
- Global message search now uses a `message_search_index` table and Room schema version 31, moving searchable text generation to write time for new messages and migration time for old messages.
- Knowledge-base vector search now maintains a bounded top-K heap and batch-loads matched items.
- Historical image/video context is sent as text placeholders; only the current user message keeps media Base64 payloads.
- Release builds now enable R8 and `shrinkResources` with conservative keep rules for Room, model JSON, WebView JS bridges, native methods, Markwon/JLatexMath, and ML Kit.

## Deferred Large Work

- Replace the ordinary message search index table with FTS5 for better tokenized search and ranking.
- Store knowledge embeddings as normalized Float32 BLOBs or introduce an approximate vector index.
- Add Baseline Profile coverage for cold start, chat open, drawer search, send message, and settings.
- Add instrumentation migration tests across older schema versions before broad release rollout.
