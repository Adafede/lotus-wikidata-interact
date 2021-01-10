/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2020 Jonathan Bisson
 *
 */

package net.nprod.lotus.importer.oldprocessor

import net.nprod.lotus.helpers.GZIPReader
import net.nprod.lotus.importer.input.Compound
import net.nprod.lotus.importer.input.DataTotal
import net.nprod.lotus.importer.input.Database
import net.nprod.lotus.importer.input.Organism
import net.nprod.lotus.importer.input.Quad
import net.nprod.lotus.importer.input.Reference
import net.nprod.lotus.importer.input.ifEqualReplace
import net.nprod.lotus.importer.parseTSVFile
import org.apache.logging.log4j.LogManager
import java.io.BufferedReader
import java.io.File

fun tryGzipThenNormal(fileName: String): BufferedReader = try {
    GZIPReader(fileName).bufferedReader
} catch (e: java.util.zip.ZipException) {
    File(fileName).bufferedReader()
}

class InvalidEntryDataException(override val message: String) : RuntimeException()

val TaxonomyDatabaseExclusionList = listOf("IPNI", "IRMNG (old)")
val RequiredTaxonRanks = listOf("variety", "genus", "subgenus", "species", "subspecies", "family")

fun loadData(fileName: String, skip: Int = 0, limit: Int? = null): DataTotal {
    val logger = LogManager.getLogger("net.nprod.lotus.chemistry.net.nprod.lotus.tools.wdpropcreator.main")
    val dataTotal = DataTotal()

    logger.info("Started")
    val file = tryGzipThenNormal(fileName).use {
        parseTSVFile(it, limit, skip) ?: throw FileSystemException(File(fileName))
    }

    file.map {
        val database = it.getString("database")
        val organismCleaned = it.getString("organismCleaned")
        val organismDb = it.getString("organismCleaned_dbTaxo")
        val organismID = it.getString("organismCleaned_id")
        val organismRanks = it.getString("organismCleaned_dbTaxoTaxonRanks")
        val organismNames = it.getString("organismCleaned_dbTaxoTaxonomy")
        val unspecifiedCenters = it.getInt("structureCleaned_stereocenters_unspecified")
        val totalCenters = it.getInt("structureCleaned_stereocenters_total")

        val smiles = it.getString("structureCleanedSmiles")
        val doi = it.getString("referenceCleanedDoi")

        if (RequiredTaxonRanks.any { organismRanks.contains("it") } ||
            organismDb !in TaxonomyDatabaseExclusionList
        ) {

            val databaseObj = dataTotal.databaseCache.getOrNew(database) { Database(name = database) }

            val organismObj = dataTotal.organismCache.getOrNew(organismCleaned) { Organism(name = organismCleaned) }

            organismObj.finalIds[organismDb] = organismID
            organismObj.textRanks[organismDb] = organismRanks
            organismObj.textNames[organismDb] = organismNames
            val inchiKey = it.getString("structureCleanedInchikey3D").validateInChIKey()
            try {
                val compoundObj = dataTotal.compoundCache.getOrNew(smiles) {
                    Compound(
                        name = it.getString("structureCleaned_nameTraditional"),
                        smiles = smiles,
                        inchi = it.getString("structureCleanedInchi"),
                        inchikey = inchiKey,
                        iupac = it.getString("structureCleaned_nameIupac"),
                        unspecifiedStereocenters = it.getInt("structureCleaned_stereocenters_unspecified"),
                        atLeastSomeStereoDefined = unspecifiedCenters != totalCenters
                    )
                }

                val referenceObj = dataTotal.referenceCache.getOrNew(doi) {
                    Reference(
                        doi = doi,
                        title = it.getString("referenceCleanedTitle")
                            .ifEqualReplace("NA", ""),
                        pmcid = it.getString("referenceCleanedPmcid")
                            .ifEqualReplace("NA", ""),
                        pmid = it.getString("referenceCleanedPmid")
                            .ifEqualReplace("NA", "")
                    )
                }

                dataTotal.quads.add(Quad(databaseObj, organismObj, compoundObj, referenceObj))
            } catch (e: InvalidEntryDataException) {
                logger.error(e)
                throw RuntimeException("It works")
            }

        } else {
            logger.error("Invalid entry: $it")
        }
    }
    logger.info("Done importing")
    logger.info("Resolving the taxo DB")
    dataTotal.organismCache.store.values.forEach { it.resolve(dataTotal.taxonomyDatabaseCache) }

    return dataTotal
}

val InChIKeyRegexp: Regex = "[A-Z]{14}-[A-Z]{10}-[A-Z]".toRegex()
private fun String.validateInChIKey(): String {
    if (!this.matches(InChIKeyRegexp)) throw InvalidEntryDataException("InChIKey $this invalid")
    return this
}
