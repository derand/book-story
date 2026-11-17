/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.reader.model

import ua.acclorite.book_story.domain.model.statistics.ReadingCoverage
import ua.acclorite.book_story.domain.model.statistics.SessionWords

/**
 * The reader's statistics ledger for the open book: the running session's
 * times, the words it credited, and what of the book has been read.
 *
 * Plain state, no I/O: the reader feeds it what happens and writes what it
 * hands back. The rules it encodes — a preview records nothing (the reader
 * simply never starts it), the note sheet is reading time, overlay time is
 * subtracted only from speed, and the idle cap is measured after the last sign
 * of life — were worked out in device QA and are documented where they live.
 *
 * Not thread-safe: the reader calls it from Main only.
 *
 * @param itemWords words in the text item at an index, null when out of range.
 * @param noteWords words in the note with an id.
 */
class ReadingSessionTracker(
    private val itemWords: (index: Int) -> Int?,
    private val noteWords: (id: String) -> Int,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** Start of the running reading session, or null when none is running. */
    private var startTime: Long? = null

    /**
     * When the reader last showed a sign of life. Only the idle *after* it is
     * capped, so a device left open on a page stops counting while a pause in
     * the middle of a sitting does not.
     */
    var lastActive: Long = 0L
        private set

    /** What this session has credited, de-duplicated; see [SessionWords]. */
    private val sessionWords = SessionWords()

    /**
     * The note the sheet is showing. Credited when it closes rather than when it
     * opens, so a mis-tap that is dismissed at once counts nothing.
     */
    private var openNoteId: String? = null

    /**
     * How long the text has been covered this session by something that earns
     * no words, and when the covering started. The session's interval is left
     * alone — this is subtracted only where a speed is divided.
     *
     * The note sheet does not count: [creditOpenNote] credits its words, so its
     * time belongs in that denominator.
     */
    var overlayMs = 0L
        private set
    private var overlayShownAt: Long? = null

    /**
     * Set while dragging the progress slider or stepping through search
     * matches, whose landings are screens nobody read.
     *
     * Deliberately *not* set for the other ways the reader moves the list
     * itself. Restoring the bookmark, opening a chapter and returning to a
     * checkpoint all land where the reading is about to happen, and that landing
     * is the only sample that screen will ever get: suppressing it left the
     * first screen of every book uncredited, so reading one cover to cover
     * stopped short of 100 % (found in device QA, 2026-08-05).
     */
    private var landingAfterJump = false

    /**
     * Whether this session got to the end of the book. Carried to the book's
     * record when the session is written, rather than set the moment it
     * happens: there may be no record yet to set it on.
     *
     * Only a running session sets it, and only on a screen it credits. A
     * preview scrolled to its end runs no session, and a flag it set used to
     * survive into the next library book read and mark *that* one finished;
     * a slider dropped at the end is a landing nobody read, like the screen
     * [landingAfterJump] refuses.
     */
    var reachedEnd = false
        private set

    /** The last settled sample was a landing [creditVisible] skipped. */
    private var skippedLanding = false

    /**
     * What of the open book has been read — item indices, loaded with the text
     * and saved when the session ends.
     */
    private val coveredItems = mutableSetOf<Int>()
    private var coveredWords = 0
    private var coverageItemCount = 0
    private var coverageBookWords = 0

    /**
     * Words before each item, built by the pass that counts the book. It is
     * what makes "left to read" a forecast: the bookmark's index reads straight
     * out of it, with no second walk over the text.
     */
    private var itemWordPrefix: IntArray? = null

    /**
     * Until the stored coverage has been read back, nothing is credited: the
     * set is about to be replaced by it.
     */
    private var coverageReady = false

    val isRunning: Boolean
        get() = startTime != null

    val wordsRead: Int
        get() = sessionWords.total

    val coveragePercent: Float
        get() = if (coverageItemCount <= 0) 0f
        else coveredItems.size.toFloat() / coverageItemCount

    /**
     * Starts a session unless one is running. [overlayShowing]: the text is
     * still covered — stopped from inside the image viewer, say. The span the
     * stop closed out resumes here, or the whole of it would be counted as
     * reading.
     */
    fun start(overlayShowing: Boolean) {
        if (startTime != null) return

        val now = clock()
        startTime = now
        lastActive = now
        overlayMs = 0
        overlayShownAt = if (overlayShowing) now else null
        reachedEnd = false
    }

    /**
     * Stops the running session and returns when it started, or null when none
     * was running. A note still open was read like any other and is credited;
     * an overlay still up is closed out, so being stopped from inside the image
     * viewer does not lose the time it was holding.
     *
     * The ledger is left for the caller to read; [closeLedger] empties it.
     */
    fun stop(): Long? {
        val start = startTime ?: return null
        creditOpenNote()
        accrueOverlay()
        startTime = null
        return start
    }

    /** Empties the session's words and end flag once it has been written. */
    fun closeLedger() {
        sessionWords.clear()
        reachedEnd = false
    }

    /**
     * Something that is not a scroll but is still the reader at work — opening
     * or closing a note, typing a query. Without it the idle cap would measure
     * from the last scroll, and ten minutes spent in a long note would be cut
     * off the end of the session as if the device had been put down.
     */
    fun markActive() {
        lastActive = clock()
    }

    /** Something that earns no words started covering the text. */
    fun overlayShown() {
        markActive()
        if (overlayShownAt == null) overlayShownAt = clock()
    }

    /**
     * Whatever was covering the text went away. If something else is *still*
     * covering it — the search bar an image was opened over, say — the reader
     * calls [overlayShown] again after updating its state, which is what tells
     * the two apart.
     */
    fun overlayHidden() {
        markActive()
        accrueOverlay()
    }

    /**
     * Banks a running overlay span, without touching [lastActive]. The end of a
     * session goes through here rather than [overlayHidden]: marking the reader
     * active at that moment would move the last sign of life to *now* and so
     * lift the idle cap off every session that ends.
     */
    private fun accrueOverlay() {
        val shownAt = overlayShownAt ?: return
        overlayShownAt = null
        overlayMs += (clock() - shownAt).coerceAtLeast(0)
    }

    /** The note sheet opened on [id]; its words are credited when it closes. */
    fun noteOpened(id: String) {
        openNoteId = id
    }

    /**
     * Credits the note the sheet was showing, if any.
     *
     * Note words are **volume only**: notes are not items, so they enter neither
     * the covered items nor the book's words. Putting them in the book's total
     * would mean no book could ever reach 100 % coverage without opening every
     * note; leaving them out of the session's words leaves the time counted and
     * the words not, which drags every speed figure down.
     */
    fun creditOpenNote() {
        val id = openNoteId ?: return
        openNoteId = null

        if (startTime == null) return
        sessionWords.creditNote(id, noteWords(id))
    }

    /** The reader is about to move the list itself; see [landingAfterJump]. */
    fun jumped() {
        landingAfterJump = true
    }

    /**
     * A settled position with [visible] items on screen. Every settled position
     * is a sign of life, even one that credits nothing: the reader is being
     * scrolled either way.
     */
    fun settled(visible: List<Int>) {
        markActive()
        creditVisible(visible)
    }

    /**
     * The position just [settled] on is [progress] of the way through the
     * book; see [reachedEnd].
     */
    fun progressed(progress: Float) {
        if (startTime == null || skippedLanding) return
        if (progress >= 1f) reachedEnd = true
    }

    /**
     * Marks what is on screen as read. Called only for *settled* positions, so
     * a fling across half the book credits nothing it flew past: the flow never
     * emits those in between.
     */
    fun creditVisible(visible: List<Int>) {
        // Nothing is credited outside a session, the way a note is not: the
        // coverage pass finishes in a job of its own and can land after the
        // session that started it has been banked and its ledger cleared — a
        // book opened and left at once would then pay its last screen into the
        // *next* book's first session.
        if (startTime == null) return

        skippedLanding = landingAfterJump
        if (landingAfterJump) {
            landingAfterJump = false
            return
        }
        if (!coverageReady) return

        for (index in visible) {
            val words = itemWords(index) ?: continue

            sessionWords.creditItem(index, words)
            if (coveredItems.add(index)) coveredWords += words
        }
    }

    /**
     * The coverage read back for a text of [itemCount] items, with the words
     * before each item in [prefix] (its last entry is the whole book). Nothing
     * is credited before this.
     */
    fun loadCoverage(stored: ReadingCoverage, itemCount: Int, prefix: IntArray) {
        coverageItemCount = itemCount
        coverageBookWords = prefix.last()
        itemWordPrefix = prefix
        coveredItems.clear()
        coveredItems.addAll(stored.covered)
        coveredWords = stored.coveredWords
        coverageReady = true
    }

    /**
     * The coverage to store for [bookId] with its bookmark at [bookmarkIndex],
     * or null while it has not been loaded.
     */
    fun coverage(bookId: Int, bookmarkIndex: Int): ReadingCoverage? {
        if (!coverageReady) return null
        return ReadingCoverage(
            bookId = bookId,
            itemCount = coverageItemCount,
            bookWords = coverageBookWords,
            covered = coveredItems.toSet(),
            coveredWords = coveredWords,
            wordsBeforeBookmark = wordsBeforeBookmark(bookmarkIndex)
        )
    }

    /**
     * Words before the bookmark, as the session leaves it. Recorded with the
     * coverage rather than on every settled scroll because the coverage row is
     * written then anyway, and the position at the end of a session is the one
     * being described.
     *
     * The index is clamped only to keep the lookup in range; a text that has
     * been reparsed under the bookmark is caught properly by the coverage row's
     * item count.
     */
    private fun wordsBeforeBookmark(bookmarkIndex: Int): Int? {
        val prefix = itemWordPrefix ?: return null
        return prefix[bookmarkIndex.coerceIn(0, prefix.lastIndex)]
    }

    /** Forgets the book: called once its session has been written. */
    fun reset() {
        coverageReady = false
        coveredItems.clear()
        coveredWords = 0
        coverageItemCount = 0
        coverageBookWords = 0
        itemWordPrefix = null
        landingAfterJump = false
        skippedLanding = false
        reachedEnd = false
        openNoteId = null
        overlayMs = 0
        overlayShownAt = null
    }
}
