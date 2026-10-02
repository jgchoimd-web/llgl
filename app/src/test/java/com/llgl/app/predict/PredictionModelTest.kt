package com.llgl.app.predict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionModelTest {

    @Test
    fun `suggestions match at jamo level and rank by frequency`() {
        val model = PredictionModel()
        repeat(3) { model.learn("강아지") }
        model.learn("강남")
        model.learn("거북이")
        assertEquals(listOf("강아지", "강남"), model.suggest("가"))
        assertEquals(listOf("강아지", "강남"), model.suggest("강"))
        assertEquals(listOf("강아지"), model.suggest("강아"))
        assertEquals(emptyList<String>(), model.suggest("강아지"))
        assertEquals(listOf("거북이"), model.suggest("거"))
    }

    @Test
    fun `what usually follows the previous word is boosted`() {
        val model = PredictionModel()
        repeat(3) { model.learn("사람") }
        model.learn("사과", previous = "맛있는")
        assertEquals(listOf("사람", "사과"), model.suggest("사"))
        assertEquals(listOf("사과", "사람"), model.suggest("사", previous = "맛있는"))
        assertEquals(listOf("사과"), model.next("맛있는"))
        assertEquals(listOf("사과"), model.suggest("", previous = "맛있는"))
    }

    @Test
    fun `english is matched case-insensitively`() {
        val model = PredictionModel()
        model.learn("Hello")
        model.learn("help")
        assertEquals(listOf("Hello", "help"), model.suggest("he").sortedBy { it.lowercase() })
    }

    @Test
    fun `only real words are learned`() {
        assertTrue(PredictionModel.isLearnable("나"))
        assertTrue(PredictionModel.isLearnable("don't"))
        assertFalse(PredictionModel.isLearnable("a"))
        assertFalse(PredictionModel.isLearnable("2024년"))
        assertFalse(PredictionModel.isLearnable("http://x"))
        assertFalse(PredictionModel.isLearnable(""))
    }

    @Test
    fun `the model survives a save and load`() {
        val model = PredictionModel()
        model.learn("안녕하세요")
        model.learn("반갑습니다", previous = "안녕하세요")
        val restored = PredictionModel().apply { load(model.save()) }
        assertEquals(2, restored.size)
        assertEquals(listOf("안녕하세요"), restored.suggest("안"))
        assertEquals(listOf("반갑습니다"), restored.next("안녕하세요"))
    }

    @Test
    fun `decay forgets what is no longer used and the size is capped`() {
        val model = PredictionModel(maxWords = 3)
        model.learn("하나")
        model.learn("둘")
        model.learn("셋")
        model.learn("넷")
        assertTrue(model.size <= 3)

        val fading = PredictionModel()
        fading.learn("잠깐")
        repeat(5) { fading.decay() }
        assertEquals(0, fading.size)
    }
}
