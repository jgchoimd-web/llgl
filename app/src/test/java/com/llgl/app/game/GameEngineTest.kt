package com.llgl.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {

    /** A game that has just started. [spawn] = false keeps random waves out of scripted scenarios. */
    private fun newGame(spawn: Boolean = true): GameEngine = GameEngine(Random(42)).apply {
        spawnEnabled = spawn
        tap()
    }

    /** Advances the game frame by frame and returns the events raised along the way. */
    private fun GameEngine.run(seconds: Float, step: Float = 1f / 60f): List<GameEvent> {
        val seen = ArrayList<GameEvent>()
        var t = 0f
        while (t < seconds) {
            update(step)
            seen += events
            t += step
        }
        return seen
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
        val events = engine.run(1f)
        assertTrue("expected a Crashed event", events.any { it is GameEvent.Crashed })
        assertEquals(Phase.GAME_OVER, engine.phase)
    }

    @Test
    fun `collecting an orb adds points and keeps playing`() {
        val engine = newGame()
        engine.addObstacle(Obstacle(d = GameEngine.MARBLE_D + 0.6f, u = 0f, kind = ObstacleKind.ORB))
        val events = engine.run(0.5f)
        assertEquals(1, engine.orbs)
        assertEquals(Phase.PLAYING, engine.phase)
        assertTrue(engine.score >= GameEngine.ORB_POINTS)
        assertEquals(1, events.count { it is GameEvent.Collected })
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
    fun `a boost pad speeds the marble up for a while and fires only once`() {
        val engine = newGame(spawn = false)
        engine.addObstacle(Obstacle(d = GameEngine.MARBLE_D + 0.3f, u = 0f, kind = ObstacleKind.BOOST))
        val events = engine.run(0.3f)
        assertEquals(1, events.count { it is GameEvent.Boosted })
        assertTrue(engine.isBoosting)
        assertTrue(engine.speed > GameEngine.START_SPEED * 1.4f)

        val later = engine.run(GameEngine.BOOST_DURATION + 0.2f)
        assertEquals(0, later.count { it is GameEvent.Boosted })
        assertFalse(engine.isBoosting)
        assertEquals(Phase.PLAYING, engine.phase)
        assertTrue(engine.speed < GameEngine.START_SPEED * 1.4f)
    }

    @Test
    fun `a crash stops the marble`() {
        val engine = newGame(spawn = false)
        engine.addObstacle(Obstacle(d = GameEngine.MARBLE_D, u = 0f, kind = ObstacleKind.BARRIER))
        engine.update(1f / 60f)
        assertEquals(Phase.GAME_OVER, engine.phase)
        assertEquals(0f, engine.speed, 1e-6f)
    }

    @Test
    fun `pause freezes the run until the next tap`() {
        val engine = newGame()
        engine.run(0.5f)
        engine.togglePause()
        assertEquals(Phase.PAUSED, engine.phase)
        val frozen = engine.distance
        engine.run(0.5f)
        assertEquals(frozen, engine.distance, 1e-6f)

        engine.tap()
        assertEquals(Phase.PLAYING, engine.phase)
        engine.run(0.1f)
        assertTrue(engine.distance > frozen)
    }

    @Test
    fun `back to title clears the run but keeps the best score`() {
        val engine = newGame()
        engine.run(1f)
        engine.addObstacle(Obstacle(d = GameEngine.MARBLE_D, u = 0f, kind = ObstacleKind.BARRIER))
        engine.update(1f / 60f)
        val best = engine.best
        assertTrue(best > 0)

        engine.backToTitle()
        assertEquals(Phase.READY, engine.phase)
        assertTrue(engine.obstacles.isEmpty())
        assertEquals(0, engine.score)
        assertEquals(best, engine.best)
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
        assertFalse(engine.isNewBest)
    }

    @Test
    fun `title screen keeps the slab rolling without obstacles`() {
        val engine = GameEngine(Random(3))
        engine.run(3f)
        assertEquals(Phase.READY, engine.phase)
        assertTrue(engine.distance > 0f)
        assertTrue(engine.obstacles.isEmpty())
    }
}
