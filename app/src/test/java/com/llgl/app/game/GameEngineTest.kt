package com.llgl.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {

    private fun newGame(): GameEngine = GameEngine(Random(42)).apply { tap() }

    private fun GameEngine.run(seconds: Float, step: Float = 1f / 60f) {
        var t = 0f
        while (t < seconds) {
            update(step)
            t += step
        }
    }

    @Test
    fun `tap starts the game from the title screen`() {
        val engine = GameEngine(Random(1))
        assertEquals(Phase.READY, engine.phase)
        engine.tap()
        assertEquals(Phase.PLAYING, engine.phase)
    }

    @Test
    fun `steering is clamped to the road`() {
        val engine = newGame()
        engine.steer(5f)
        assertEquals(GameEngine.MAX_U, engine.marbleU, 1e-6f)
        engine.steer(-10f)
        assertEquals(-GameEngine.MAX_U, engine.marbleU, 1e-6f)
    }

    @Test
    fun `hitting a barrier ends the game and raises a crash event`() {
        val engine = newGame()
        engine.addObstacle(Obstacle(d = GameEngine.MARBLE_D + 0.6f, u = 0f, kind = ObstacleKind.BARRIER))
        var crashed = false
        repeat(60) {
            engine.update(1f / 60f)
            if (engine.events.any { it is GameEvent.Crashed }) crashed = true
        }
        assertTrue("expected a Crashed event", crashed)
        assertEquals(Phase.GAME_OVER, engine.phase)
    }

    @Test
    fun `collecting an orb adds points and keeps playing`() {
        val engine = newGame()
        engine.addObstacle(Obstacle(d = GameEngine.MARBLE_D + 0.6f, u = 0f, kind = ObstacleKind.ORB))
        engine.run(0.5f)
        assertEquals(1, engine.orbs)
        assertEquals(Phase.PLAYING, engine.phase)
        assertTrue(engine.score >= GameEngine.ORB_POINTS)
    }

    @Test
    fun `obstacles in another lane are missed`() {
        val engine = newGame()
        engine.addObstacle(Obstacle(d = GameEngine.MARBLE_D + 0.6f, u = GameEngine.LANES[2], kind = ObstacleKind.BARRIER))
        engine.run(0.5f)
        assertEquals(Phase.PLAYING, engine.phase)
    }

    @Test
    fun `speed ramps up with distance`() {
        val engine = newGame()
        engine.run(2f)
        assertEquals(Phase.PLAYING, engine.phase)
        assertTrue(engine.speed > GameEngine.START_SPEED)
        assertTrue(engine.speed <= GameEngine.MAX_SPEED)
    }

    @Test
    fun `restart is ignored right after a crash and allowed after the delay`() {
        val engine = newGame()
        engine.addObstacle(Obstacle(d = GameEngine.MARBLE_D, u = 0f, kind = ObstacleKind.BARRIER))
        engine.update(1f / 60f)
        assertEquals(Phase.GAME_OVER, engine.phase)

        engine.tap()
        assertEquals(Phase.GAME_OVER, engine.phase)

        engine.run(GameEngine.RESTART_DELAY + 0.1f)
        engine.tap()
        assertEquals(Phase.PLAYING, engine.phase)
        assertEquals(0, engine.score)
    }

    @Test
    fun `best score survives a restart`() {
        val engine = newGame()
        engine.run(1f)
        engine.addObstacle(Obstacle(d = GameEngine.MARBLE_D, u = 0f, kind = ObstacleKind.BARRIER))
        engine.update(1f / 60f)
        val finalScore = engine.score
        assertTrue(finalScore > 0)
        assertEquals(finalScore, engine.best)
        assertTrue(engine.isNewBest)

        engine.run(GameEngine.RESTART_DELAY + 0.1f)
        engine.tap()
        assertEquals(finalScore, engine.best)
        assertEquals(false, engine.isNewBest)
    }

    @Test
    fun `title screen keeps the city scrolling without obstacles`() {
        val engine = GameEngine(Random(3))
        engine.run(3f)
        assertEquals(Phase.READY, engine.phase)
        assertTrue(engine.distance > 0f)
        assertTrue(engine.obstacles.isEmpty())
        assertTrue(engine.pillars.isNotEmpty())
    }
}
