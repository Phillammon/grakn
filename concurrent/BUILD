#
# Copyright (C) 2021 Grakn Labs
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as
# published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#

load("@graknlabs_dependencies//tool/checkstyle:rules.bzl", "checkstyle_test")
load("@graknlabs_dependencies//builder/java:rules.bzl", "host_compatible_java_test")
package(default_visibility = ["//visibility:public"])


java_library(
    name = "concurrent",
    srcs = glob(["*/*.java", "*/*/*.java"], exclude=["test/*", "*Test.java", "*/*Test.java"]),
    deps = [
        # Internal Repository Dependencies
        "//common:common",

        # External dependencies from Grakn Labs
        "@graknlabs_common//:common",

        # External Maven Dependencies
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:io_netty_netty_all",
        "@maven//:org_slf4j_slf4j_api",
    ],
    tags = ["maven_coordinates=io.grakn.core:grakn-concurrent:{pom_version}"],
)

checkstyle_test(
    name = "checkstyle",
    include = glob(["*", "*/*", "*/*/*"]),
    license_type = "agpl",
)
