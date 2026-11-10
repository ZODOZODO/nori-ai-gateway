/*
 * settings.gradle.kts (nori-ai-gateway)
 *
 * build-logic 해석(nori-wm-ui-backend settings 와 같은 규칙, Plan §3.1 2026-11-10 빌드·U2)
 * - mode 는 nori.buildLogic.mode(허용값 composite, published)이고 property 가 없으면 nori.release.repository 가 있을 때
 *   published, 없을 때 composite 로 유도한다. 이 레포의 로컬·CI 빌드는 composite 만 쓴다(U2 고정 결정).
 * - NoriRelease repository 는 nori.release.repository 값이 있을 때만 pluginManagement 첫 항목으로 이 settings 본문이 직접 등록한다.
 * - composite 일 때만 ../nori-common/build-logic 을 이름 nori-common-build-logic 으로 include 하고 published 는 build-logic include 0개다.
 * - noriCatalog 는 두지 않는다. 이 레포는 com.nori.common 좌표를 소비하지 않고 settings-conventions 도 catalog 를 요구하지 않는다.
 * - 의존 저장소는 settings-conventions 가 settings 단위로 소유한다(FAIL_ON_PROJECT_REPOS, gradle.properties 의 gitlabPackageProjectIds).
 * - 모듈 디렉터리 이동과 projectDir 매핑은 U3 에서 한다(현재는 평면 include).
 */

pluginManagement {
    // pluginManagement 블록은 따로 평가되므로 필요한 값과 함수는 이 블록 안에서만 정의한다.
    val noriReleaseRepository: String? = providers.gradleProperty("nori.release.repository").orNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val requestedMode: String? = providers.gradleProperty("nori.buildLogic.mode").orNull
    val noriBuildLogicMode: String = when {
        requestedMode == null -> if (noriReleaseRepository != null) "published" else "composite"
        requestedMode == "composite" || requestedMode == "published" -> requestedMode
        else -> throw GradleException(
            "NORI_BUILD_LOGIC_MODE_INVALID: nori.buildLogic.mode 는 composite 또는 published 여야 합니다: $requestedMode"
        )
    }
    if (noriBuildLogicMode == "published" && noriReleaseRepository == null) {
        throw GradleException(
            "NORI_RELEASE_REPOSITORY_REQUIRED: nori.buildLogic.mode=published 에는 -Pnori.release.repository=<absolute path> 가 필요합니다."
        )
    }

    // local repository URL 은 absolute path 를 file: URI 로 정규화한 값만 허용한다.
    fun noriReleaseRepositoryUri(value: String): java.net.URI {
        val file = try {
            if (value.startsWith("file:", ignoreCase = true)) java.io.File(java.net.URI(value)) else java.io.File(value)
        } catch (e: IllegalArgumentException) {
            throw GradleException("nori.release.repository 는 absolute path 또는 absolute file: URI 여야 합니다: $value", e)
        }
        if (!file.isAbsolute) {
            throw GradleException("nori.release.repository 는 absolute path 또는 absolute file: URI 여야 합니다: $value")
        }
        return file.toURI()
    }

    if (noriBuildLogicMode == "composite") {
        val buildLogicDirectory = java.io.File(settingsDir, "../nori-common/build-logic").canonicalFile
        if (!buildLogicDirectory.isDirectory) {
            throw GradleException(
                "NORI_COMPOSITE_BUILD_MISSING: composite mode 에 필요한 build-logic source build 가 없습니다: ${buildLogicDirectory.absolutePath}"
            )
        }
        includeBuild("../nori-common/build-logic") {
            name = "nori-common-build-logic"
        }
    }

    repositories {
        if (noriReleaseRepository != null) {
            maven {
                name = "NoriRelease"
                url = noriReleaseRepositoryUri(noriReleaseRepository)
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }

    // 이 레포가 요청하는 marker 만 고정한다. version 은 composite 로 include 하는 build-logic source version 과 같다
    // (../nori-common/build-logic/build.gradle.kts). archunit-conventions 는 적용하지 않는다(U2 고정 결정).
    plugins {
        id("com.nori.common.settings-conventions") version "0.0.6"
        id("com.nori.common.root-project-conventions") version "0.0.6"
    }
}

plugins {
    id("com.nori.common.settings-conventions")
}

rootProject.name = "nori-ai-gateway"
include("gateway-eval", "gateway-core", "gateway-persistence", "gateway-integration", "gateway-tc-mock", "gateway-app", "gateway-acceptance")
