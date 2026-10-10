package app.naviamp.ui

import app.naviamp.domain.radio.SimilarityDiagnostics

enum class SimilarityTestState { Idle, Running, NoSeed, Failed, Complete }

data class NaviampSimilarityTestUi(
    val state: SimilarityTestState = SimilarityTestState.Idle,
    val sourceId: String? = null,
    val report: SimilarityDiagnostics? = null,
)
