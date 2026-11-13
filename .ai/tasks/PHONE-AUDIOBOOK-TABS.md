# PHONE-AUDIOBOOK-TABS

AI: Claude
Status: implemented; owner authorized merge to main and release on 2026-11-13. Temporary task ID because GitHub Issues are disabled.
Branch: feat/phone-audiobook-tabs (from main cc955f6c)
Overlap check: no open feature PR touches these files. PR #2 is the documentation records PR. AUTO-AUDIOBOOKS (PR #3) is merged; its car code only gains an optional sort argument on AbsRepository.books, whose default keeps title order.

Request: the owner's layout concept — Library, Downloaded and Listening tabs on the phone Audiobooks screen. Library has a Continue listening card, a "Your library" count and a cover grid with a Sort button. Settings has drag-to-reorder tab order and a Library sort. The owner chose to keep the On this phone / Audiobookshelf source switch, with the tabs under it.

Implemented:
- audiobooks/AudiobookTabs.kt: tab and sort enums, saved order (decode drops unknown or repeated entries and appends missing tabs), the open tab with fallback, sort fallback, and phone-book sorting.
- ui/screens/library/audiobooks/AudiobookTabsUi.kt: tab bar, Library header with Sort, sort dialog, Continue listening card with play/pause, two-column cover grid with progress, downloaded and finished badges, and empty-tab messages.
- On this phone: Library (card for the most recently played unfinished book, which resumes it directly; count; rescan and folder buttons; grid sorted by title or author). Downloaded shows every book, since phone books are always offline. Listening shows started, unfinished books, most recent first.
- Audiobookshelf: the account row and library chips stay above the tabs, and search results still replace the tab content. Library has the card for the server's top in-progress book, the server count, a server-sorted grid (title, author, or recently added newest first) and Load more. Downloaded shows downloaded books; Listening shows in-progress books.
- AudiobookshelfViewModel.setSort reloads the first page; AbsClient sends sort/desc (absSortParams).
- Settings → Features: AudiobookTabSettings, with drag-to-reorder tabs (saved at once) and Library sort.
- No database schema or version changes. Strings are added only to the default English metrolist_strings.xml.

Tests: AudiobookTabsTest (6). It covers default and round-trip order, malformed orders, open-tab fallback, sort fallback, case-insensitive title and author sorting (unknown authors last), and the Audiobookshelf sort parameters.

Limits: books on the phone have no added date, so the phone source offers Title and Author only (a saved Recently added choice sorts them by title). The concept's in-page search box was not added; the existing top-bar search filters both sources. Physical-device testing has not been done.

Verification:
- AudiobookTabsTest (6 tests) passed locally. This workspace has no Android SDK, so the tests ran with a standalone Kotlin 2.1.21 compiler and stand-ins for R, DataStore and JUnit. absSortParams was copied verbatim from AbsClient.kt.
- Every R.string/R.drawable/R.plurals reference in the changed files resolves, and metrolist_strings.xml parses.
- PR #4 (https://github.com/rickevans86/Meld/pull/4), CI run 43411817378 on 26a1cdfa, passed: full :app:testFossDebugUnitTest and :innertube:test, assembleFossDebug, review APK upload, and :app:lintFossDebug.
- Not done: physical phone testing and a live Audiobookshelf check of the server sort orders.
