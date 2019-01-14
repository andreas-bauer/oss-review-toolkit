/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.spdx

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue

import io.kotlintest.assertSoftly
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

class SpdxExpressionTest : WordSpec() {
    private val yamlMapper = ObjectMapper(YAMLFactory())

    init {
        "spdxLicenses()" should {
            "contain all valid SPDX licenses" {
                val expression = "MIT OR (invalid1 AND Apache-2.0 WITH exp) AND (BSD-3-Clause OR invalid2 WITH exp)"
                val spdxExpression = SpdxExpression.parse(expression)

                val spdxLicenses = spdxExpression.spdxLicenses()

                spdxLicenses shouldBe enumSetOf(SpdxLicense.APACHE_2_0, SpdxLicense.BSD_3_CLAUSE, SpdxLicense.MIT)
            }
        }

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

        "An SpdxExpression" should {
            val expression = "license1+ AND (license2 WITH exception1 OR license3+) AND license4 WITH exception2"

            "be serializable to a string representation" {
                val spdxExpression = SpdxExpression.parse(expression)

                val serializedExpression = yamlMapper.writeValueAsString(spdxExpression)

                serializedExpression shouldBe "--- \"$expression\"\n"
            }

            "be deserializable from a string representation" {
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

        "The expression parser" should {
            "work for deprecated license identifiers" {
                assertSoftly {
                    SpdxExpression.parse("Nunit") shouldBe SpdxLicenseIdExpression("Nunit")
                    SpdxExpression.parse("StandardML-NJ") shouldBe SpdxLicenseIdExpression("StandardML-NJ")
                    SpdxExpression.parse("wxWindows") shouldBe SpdxLicenseIdExpression("wxWindows")
                }
            }

            "work for deprecated license variants" {
                assertSoftly {
                    SpdxExpression.parse("AGPL-1.0") shouldBe SpdxLicenseIdExpression("AGPL-1.0")
                    SpdxExpression.parse("AGPL-3.0") shouldBe SpdxLicenseIdExpression("AGPL-3.0")
                    SpdxExpression.parse("eCos-2.0") shouldBe SpdxLicenseIdExpression("eCos-2.0")
                    SpdxExpression.parse("GFDL-1.1") shouldBe SpdxLicenseIdExpression("GFDL-1.1")
                    SpdxExpression.parse("GFDL-1.2") shouldBe SpdxLicenseIdExpression("GFDL-1.2")
                    SpdxExpression.parse("GFDL-1.3") shouldBe SpdxLicenseIdExpression("GFDL-1.3")
                    SpdxExpression.parse("GPL-1.0+") shouldBe SpdxLicenseIdExpression("GPL-1.0-or-later")
                    SpdxExpression.parse("GPL-1.0") shouldBe SpdxLicenseIdExpression("GPL-1.0-only")
                    SpdxExpression.parse("GPL-2.0+") shouldBe SpdxLicenseIdExpression("GPL-2.0-or-later")
                    SpdxExpression.parse("GPL-2.0") shouldBe SpdxLicenseIdExpression("GPL-2.0-only")
                    SpdxExpression.parse("GPL-3.0+") shouldBe SpdxLicenseIdExpression("GPL-3.0-or-later")
                    SpdxExpression.parse("GPL-3.0") shouldBe SpdxLicenseIdExpression("GPL-3.0-only")
                    SpdxExpression.parse("LGPL-2.0+") shouldBe SpdxLicenseIdExpression("LGPL-2.0-or-later")
                    SpdxExpression.parse("LGPL-2.0") shouldBe SpdxLicenseIdExpression("LGPL-2.0-only")
                    SpdxExpression.parse("LGPL-2.1+") shouldBe SpdxLicenseIdExpression("LGPL-2.1-or-later")
                    SpdxExpression.parse("LGPL-2.1") shouldBe SpdxLicenseIdExpression("LGPL-2.1-only")
                    SpdxExpression.parse("LGPL-3.0+") shouldBe SpdxLicenseIdExpression("LGPL-3.0-or-later")
                    SpdxExpression.parse("LGPL-3.0") shouldBe SpdxLicenseIdExpression("LGPL-3.0-only")
                }
            }

            "work for deprecated license exceptions" {
                SpdxExpression.parse("GPL-2.0-with-autoconf-exception") shouldBe SpdxLicenseExceptionExpression("GPL-2.0 WITH autoconf-exception")
                SpdxExpression.parse("GPL-2.0-with-bison-exception") shouldBe SpdxLicenseExceptionExpression("GPL-2.0 WITH bison-exception")
                SpdxExpression.parse("GPL-2.0-with-classpath-exception") shouldBe SpdxLicenseExceptionExpression("GPL-2.0 WITH classpath-exception")
                SpdxExpression.parse("GPL-2.0-with-font-exception") shouldBe SpdxLicenseExceptionExpression("GPL-2.0 WITH font-exception")
                SpdxExpression.parse("GPL-2.0-with-GCC-exception") shouldBe SpdxLicenseExceptionExpression("GPL-2.0 WITH GCC-exception")
                SpdxExpression.parse("GPL-3.0-with-autoconf-exception") shouldBe SpdxLicenseExceptionExpression("GPL-3.0 WITH autoconf-exception")
                SpdxExpression.parse("GPL-3.0-with-GCC-exception") shouldBe SpdxLicenseExceptionExpression("GPL-3.0 WITH GCC-exception")
            }
        }
    }
}
