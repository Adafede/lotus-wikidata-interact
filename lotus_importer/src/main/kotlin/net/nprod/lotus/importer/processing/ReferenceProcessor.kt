/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2020 Jonathan Bisson
 *
 */

package net.nprod.lotus.importer.processing

import io.ktor.util.KtorExperimentalAPI
import net.nprod.lotus.importer.input.DataTotal
import net.nprod.lotus.importer.input.Reference
import net.nprod.lotus.wdimport.wd.InstanceItems
import net.nprod.lotus.wdimport.wd.WDFinder
import net.nprod.lotus.wdimport.wd.models.entries.WDArticle
import net.nprod.lotus.wdimport.wd.publishing.IPublisher
import org.apache.logging.log4j.LogManager
import kotlin.time.ExperimentalTime

class ReferenceProcessor(
    val dataTotal: DataTotal,
    val publisher: IPublisher,
    val wdFinder: WDFinder,
    val instanceItems: InstanceItems
) {
    private val logger = LogManager.getLogger(ReferenceProcessor::class)

    @ExperimentalTime
    private val articlesCache: MutableMap<Reference, WDArticle> = mutableMapOf()

    @ExperimentalTime
    @KtorExperimentalAPI
    private fun articleFromReference(reference: Reference): WDArticle {
        val article = WDArticle(
            label = reference.title ?: reference.doi,
            title = reference.title,
            doi = reference.doi.toUpperCase(), // DOIs are always uppercase but in reality we see both
        ).tryToFind(wdFinder, instanceItems)

        // Get the article info on crossref if needed
        val hasAuthorsAlready = wdFinder.sparql.askQuery(
            """
                ASK {
                  <${article.id.iri}> <${instanceItems.author.iri}> ?o.
                }
            """.trimIndent()
        )

        if (!hasAuthorsAlready) {
            article.populateFromCrossREF(wdFinder, instanceItems)
        }

        publisher.publish(article, "upserting article")
        return article
    }

    /**
     * Generate a WikiData article from that reference
     */
    @ExperimentalTime
    @KtorExperimentalAPI
    fun get(key: Reference): WDArticle = articlesCache.getOrPut(key) { articleFromReference(key) }
}
