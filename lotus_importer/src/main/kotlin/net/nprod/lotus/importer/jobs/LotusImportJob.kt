/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2020 Jonathan Bisson
 *
 */

package net.nprod.lotus.importer.jobs

import io.ktor.util.KtorExperimentalAPI
import net.nprod.lotus.importer.input.DataTotal
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemWriter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.time.ExperimentalTime

@KtorExperimentalAPI
@ExperimentalTime
@Configuration
class LotusImportJob(val jobs: JobBuilderFactory, val steps: StepBuilderFactory) {
    @Bean
    fun itemReader(): UnivocityBasedReader<LotusRawTSV> = UnivocityBasedReader {
        LotusRawTSV.fromRecord(it)
    }

    @Bean
    fun itemProcessor(): ItemProcessor<List<LotusRawTSV>, DataTotal> = LotusProcessRaw()

    @Bean
    fun itemWriter(): ItemWriter<DataTotal> = WikiDataWriter()

    @Bean
    protected fun step1(itemReader: UnivocityBasedReader<LotusRawTSV>): Step {
        return steps["step1"].chunk<List<LotusRawTSV>, DataTotal>(1)
            .reader(itemReader).processor(itemProcessor()).writer(itemWriter()).build()
    }

    @Bean(name = ["newJob"])
    fun newJob(step1: Step): Job {
        return jobs["importJob"].start(step1).build()
    }
}

val TaxonomyDatabaseExclusionList = listOf("IPNI", "IRMNG (old)")
val RequiredTaxonRanks = listOf("variety", "genus", "subgenus", "species", "subspecies", "family")
