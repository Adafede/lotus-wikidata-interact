/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2021
 */

package net.nprod.lotus.wikidata.download.modes.mirror

data class TaxonData(
    val name: String,
    val wikidataId: String,
    val algaeId: String?,
    val irmngId: String?,
    val gbifId: String?,
    val ncbiId: String?,
    val otlId: String?,
    val wfoId: String?,
    val wormsId: String?
)
