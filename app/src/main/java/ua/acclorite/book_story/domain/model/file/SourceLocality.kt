/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.file

/**
 * Whether a book from a given provider is worth offering to keep a copy of.
 *
 * A book kept where its owner put it depends on two things the app does not
 * control: a grant that dies with a reinstall, and a provider that may have to
 * fetch the bytes. For a file on the device itself neither is true, so offering
 * to copy it there would only ask the user to spend storage twice.
 *
 * ## Why an authority and not a better question
 *
 * The platform keeps the real answer — `DocumentsContract.Root.FLAG_LOCAL_ONLY`
 * — in the roots table, and an app holding tree grants **cannot read it**:
 * querying `buildRootsUri` throws `SecurityException` ("requires that you
 * obtain access using ACTION_OPEN_DOCUMENT") for every provider, the device's
 * own included. Measured on a tablet against Google Drive and
 * `externalstorage`, along with three other candidates that all failed:
 *
 * - **document flags** say nothing — `FLAG_VIRTUAL_DOCUMENT` and `FLAG_PARTIAL`
 *   are clear on cloud documents too;
 * - **the file descriptor** does not separate them either: Drive materialises a
 *   document into its own cache and hands back a regular, seekable file, so an
 *   open looks local whatever it cost;
 * - **the reconstructed path** actively misleads — a Drive tree resolves to
 *   `/storage`, which exists on the device.
 *
 * What is left is the authority, which the grant carries and which is neither
 * synthesised nor asked of anyone.
 */
object SourceLocality {

    /**
     * Whether the copy is offered for **every** source, device storage
     * included.
     *
     * The one line to change if the distinction below turns out to be too
     * narrow — a provider that is local but not `externalstorage`, or simply a
     * decision that the choice belongs to the user everywhere. Nothing else in
     * the feature reads the distinction: what the switch *does* is copy
     * whatever was selected, so turning this on widens the offer without
     * touching the copying.
     */
    const val OFFER_FOR_EVERY_SOURCE = false

    /**
     * Providers whose documents are files on this device.
     *
     * Only one, and deliberately: `externalstorage` is the provider whose
     * documents have a durable location the app can return to, and the only one
     * whose document ids the app can turn back into a path. Everything else —
     * Drive, Downloads (whose ids are MediaStore row numbers and name no
     * location at all), MTP, any third-party provider — is a source a copy
     * protects the reader from.
     */
    private val DEVICE_STORAGE_AUTHORITIES = setOf(
        "com.android.externalstorage.documents"
    )

    /** Whether [authority] serves files that already live on this device. */
    fun isDeviceStorage(authority: String?): Boolean =
        authority != null && authority in DEVICE_STORAGE_AUTHORITIES

    /**
     * Whether to offer keeping a local copy of a book served by [authority].
     *
     * A source that could not be named at all — a book row from before document
     * identity was recorded, or a provider that has gone — is offered the copy:
     * not knowing where a book comes from is a reason to hold onto it, not a
     * reason to assume it is safe.
     */
    fun offersLocalCopy(authority: String?): Boolean =
        OFFER_FOR_EVERY_SOURCE || !isDeviceStorage(authority)
}
