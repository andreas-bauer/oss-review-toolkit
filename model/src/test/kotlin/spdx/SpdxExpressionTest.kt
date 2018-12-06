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

package com.here.ort.model.spdx

import com.fasterxml.jackson.module.kotlin.readValue

import com.here.ort.model.yamlMapper

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

class SpdxExpressionTest : WordSpec() {
    init {
        "toString()" should {
            "return the textual SPDX expression" {
                val expression = "license1+ AND (license2 WITH exception1 OR license3+) AND license4 WITH exception2"
                val spdxExpression = SpdxExpression.parse(expression)

                val spdxString = spdxExpression.toString()

                spdxString shouldBe expression
            }

            "not include unnecessary parenthesis" {
                val expression = "(license1 AND (license2 AND license3) AND (license4 OR (license5 WITH exception)))"
                val spdxExpression = SpdxExpression.parse(expression)

                val spdxString = spdxExpression.toString()

                spdxString shouldBe "license1 AND license2 AND license3 AND (license4 OR license5 WITH exception)"
            }
        }

        "Jackson" should {
            val expression = "license1+ AND (license2 WITH exception1 OR license3+) AND license4 WITH exception2"

            "serialize SpdxExpression to its string representation" {
                val spdxExpression = SpdxExpression.parse(expression)

                val serializedExpression = yamlMapper.writeValueAsString(spdxExpression)

                serializedExpression shouldBe "--- \"$expression\"\n"
            }

            "be able to deserialize an SpdxExpression from its string representation" {
                val serializedExpression = "--- \"$expression\"\n"

                val deserializedExpression = yamlMapper.readValue<SpdxExpression>(serializedExpression)

                deserializedExpression shouldBe SpdxCompoundExpression(
                        SpdxCompoundExpression(
                                SpdxLicenseIdExpression("license1", true),
                                SpdxOperator.AND,
                                SpdxCompoundExpression(
                                        SpdxCompoundExpression(
                                                SpdxLicenseIdExpression("license2"),
                                                SpdxOperator.WITH,
                                                SpdxLicenseExceptionExpression("exception1")
                                        ),
                                        SpdxOperator.OR,
                                        SpdxLicenseIdExpression("license3", true)
                                )
                        ),
                        SpdxOperator.AND,
                        SpdxCompoundExpression(
                                SpdxLicenseIdExpression("license4"),
                                SpdxOperator.WITH,
                                SpdxLicenseExceptionExpression("exception2")
                        )
                )
            }
        }
    }
}