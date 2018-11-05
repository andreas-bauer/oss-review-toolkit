/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.ort.scanner.scanners

import ch.frankel.slf4k.*

import com.fasterxml.jackson.databind.JsonNode

import com.here.ort.model.EMPTY_JSON_NODE
import com.here.ort.model.LicenseFinding
import com.here.ort.model.Provenance
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanSummary
import com.here.ort.model.ScannerDetails
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.model.jsonMapper
import com.here.ort.scanner.LocalScanner
import com.here.ort.scanner.ScanException
import com.here.ort.scanner.AbstractScannerFactory
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getPathFromEnvironment
import com.here.ort.utils.log

import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException
import java.time.Instant

class LicenseeCommand : CommandLineTool("Licensee") {
    override val executable = if (OS.isWindows) "licensee.bat" else "licensee"
    override val versionArguments = "version"
    override val preferredVersion = Semver("9.10.1")

    override val canBootstrap = true

    override fun bootstrap(): File {
        val gem = if (OS.isWindows) "gem.cmd" else "gem"

        // Work around Travis CI not being able to handle gem user installs, see
        // https://github.com/travis-ci/travis-ci/issues/9412.
        // TODO: Use toBoolean() here once https://github.com/JetBrains/kotlin/pull/1644 is merged.
        val isTravisCi = listOf("TRAVIS", "CI").all { java.lang.Boolean.parseBoolean(System.getenv(it)) }

        val version = preferredVersion.originalValue

        return if (isTravisCi) {
            ProcessCapture(gem, "install", "licensee", "-v", version).requireSuccess()
            getPathFromEnvironment(executable)?.parentFile
                    ?: throw IOException("Install directory for licensee not found.")
        } else {
            ProcessCapture(gem, "install", "--user-install", "licensee", "-v", version).requireSuccess()

            val ruby = ProcessCapture("ruby", "-r", "rubygems", "-e", "puts Gem.user_dir").requireSuccess()
            val userDir = ruby.stdout.trimEnd()

            File(userDir, "bin")
        }
    }
}

class Licensee(config: ScannerConfiguration) : LocalScanner(config) {
    class Factory : AbstractScannerFactory<Licensee>() {
        override fun create(config: ScannerConfiguration) = Licensee(config)
    }

    val CONFIGURATION_OPTIONS = listOf("--json")

    private val scanner = LicenseeCommand()

    override val name = scanner.name
    override val version = scanner.getVersion().originalValue!!
    override val configuration = CONFIGURATION_OPTIONS.joinToString(" ")

    override val resultFileExt = "json"

    override fun scanPath(scannerDetails: ScannerDetails, path: File, provenance: Provenance, resultsFile: File)
            : ScanResult {
        // Licensee has issues with absolute Windows paths passed as an argument. Work around that by using the path to
        // scan as the working directory.
        val (parentPath, relativePath) = if (path.isDirectory) {
            Pair(path, ".")
        } else {
            Pair(path.parentFile, path.name)
        }

        val startTime = Instant.now()

        val process = scanner.run(
                parentPath,
                "detect",
                *CONFIGURATION_OPTIONS.toTypedArray(),
                relativePath
        ).requireSuccess()

        val endTime = Instant.now()

        if (process.stderr.isNotBlank()) {
            log.debug { process.stderr }
        }

        with(process) {
            if (isSuccess) {
                stdoutFile.copyTo(resultsFile)
                val result = getResult(resultsFile)
                val summary = generateSummary(startTime, endTime, result)
                return ScanResult(provenance, scannerDetails, summary, result)
            } else {
                throw ScanException(errorMessage)
            }
        }
    }

    override fun getResult(resultsFile: File): JsonNode {
        return if (resultsFile.isFile && resultsFile.length() > 0L) {
            jsonMapper.readTree(resultsFile)
        } else {
            EMPTY_JSON_NODE
        }
    }

    override fun generateSummary(startTime: Instant, endTime: Instant, result: JsonNode): ScanSummary {
        val matchedFiles = result["matched_files"]

        val findings = matchedFiles.map {
            LicenseFinding(it["matched_license"].textValue())
        }.toSortedSet()

        return ScanSummary(startTime, endTime, matchedFiles.count(), findings, errors = mutableListOf())
    }
}
