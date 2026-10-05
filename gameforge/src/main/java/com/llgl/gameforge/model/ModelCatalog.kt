package com.llgl.gameforge.model

/**
 * A Gemma bundle the app knows how to fetch and run. Every preset carries the SHA-256 of Google's
 * official file, so a download from any source is accepted only if it is byte-identical to it.
 *
 * `contextTokens` is baked into the bundle (its KV cache size); asking the runtime for more fails,
 * so it doubles as the `maxTokens` we load the model with.
 */
data class ModelSpec(
    val id: String,
    val name: String,
    val tagline: String,
    val fileName: String,
    val sizeMb: Int,
    val sha256: String,
    val contextTokens: Int,
    /** Google's own copy. Gated: needs a Hugging Face account that accepted the Gemma terms, plus a token. */
    val officialUrl: String,
    /** A public copy with the same SHA-256, when one exists; downloadable without an account. */
    val mirrorUrl: String?,
    /** 1..3, how good the games tend to be. */
    val quality: Int,
    /** 1..3, how fast it writes on a phone. */
    val speed: Int,
    val minRamGb: Int,
    val note: String,
) {
    val sizeLabel: String
        get() = if (sizeMb >= 1000) "%.1f GB".format(sizeMb / 1000.0) else "$sizeMb MB"

    /** True when the only way to get this file is the gated official repository. */
    val needsToken: Boolean get() = mirrorUrl == null
}

object ModelCatalog {
    private const val HF = "https://huggingface.co"

    val presets: List<ModelSpec> = listOf(
        ModelSpec(
            id = "gemma-3n-e2b-int4",
            name = "Gemma 3n E2B",
            tagline = "가장 똑똑함 · 느림 · 3.1 GB",
            fileName = "gemma-3n-E2B-it-int4.task",
            sizeMb = 3136,
            sha256 = "a7f544cfee68f579fabadb22aa9284faa4020a0f5358d0e15b49fdd4cefe4200",
            contextTokens = 4096,
            officialUrl = "$HF/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.task",
            mirrorUrl = "$HF/An-Upfeat/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.task",
            quality = 3,
            speed = 1,
            minRamGb = 8,
            note = "돌아가는 게임을 가장 잘 짭니다. 램 8GB 폰용. 게임 하나에 몇 분 걸리고, 처음 불러올 때 수십 초가 걸립니다.",
        ),
        ModelSpec(
            id = "gemma-3-1b-q8-4k",
            name = "Gemma 3 1B q8",
            tagline = "균형 · 4k 컨텍스트 · 1.1 GB",
            fileName = "Gemma3-1B-IT_multi-prefill-seq_q8_ekv4096.task",
            sizeMb = 1054,
            sha256 = "9fc939cf525890ea060a815c5cd4395a1496161e3930c3891891be9e255ac09f",
            contextTokens = 4096,
            officialUrl = "$HF/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv4096.task",
            mirrorUrl = null,
            quality = 2,
            speed = 2,
            minRamGb = 4,
            note = "빠르고 수정 요청도 됩니다. Hugging Face에서 Gemma 약관에 동의한 계정의 토큰이 필요합니다.",
        ),
        ModelSpec(
            id = "gemma-3-1b-q4-4k",
            name = "Gemma 3 1B q4 (4k)",
            tagline = "가벼움 · 4k 컨텍스트 · 0.7 GB",
            fileName = "Gemma3-1B-IT_multi-prefill-seq_q4_block128_ekv4096.task",
            sizeMb = 689,
            sha256 = "036e15114d1868fc7be7ccc552fc8da2fe31d64af02b48847ff99f0185d37891",
            contextTokens = 4096,
            officialUrl = "$HF/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_block128_ekv4096.task",
            mirrorUrl = null,
            quality = 2,
            speed = 3,
            minRamGb = 4,
            note = "q8보다 조금 덜 정확하지만 더 빠릅니다. Hugging Face 토큰이 필요합니다.",
        ),
        ModelSpec(
            id = "gemma-3-1b-q4-2k",
            name = "Gemma 3 1B q4 (2k)",
            tagline = "가장 빠름 · 바로 받기 · 0.6 GB",
            fileName = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
            sizeMb = 555,
            sha256 = "ddfaf1210d8b4d1b812b5fadb6652999e852c8be6dd9abe353b9213a25262c10",
            contextTokens = 2048,
            officialUrl = "$HF/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
            mirrorUrl = "$HF/ved9104/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
            quality = 1,
            speed = 3,
            minRamGb = 3,
            note = "계정 없이 바로 받아 써 볼 수 있습니다. 컨텍스트가 2048토큰이라 짧은 게임만 되고, 수정 요청은 대부분 안 들어갑니다.",
        ),
    )

    fun byFile(fileName: String): ModelSpec? = presets.firstOrNull { it.fileName == fileName }

    fun bySha(sha256: String): ModelSpec? = presets.firstOrNull { it.sha256.equals(sha256, ignoreCase = true) }

    /** Picks a sensible default context for a file we did not ship: the preset's if known, else 4k. */
    fun defaultContext(fileName: String): Int = byFile(fileName)?.contextTokens ?: 4096
}
