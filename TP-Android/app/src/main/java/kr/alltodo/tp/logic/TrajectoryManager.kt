package kr.alltodo.tp.logic

import android.content.Context
import kr.alltodo.tp.data.PathEntity
import kr.alltodo.tp.data.TrajectoryDatabase
import kr.alltodo.tp.data.TrajectoryEntity
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TrajectoryManager(private val context: Context) {
    private val db = TrajectoryDatabase.getDatabase(context)
    private val dao = db.trajectoryDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    private var currentTrajectoryId: String? = null
    private var startTime: Long = 0
    private var pathCount: Int = 0

    // T.1. 위경도 정수화 (x100,000)
    fun integerize(value: Double): Int {
        return (value * 100_000).toInt()
    }

    // T.1. 위경도 복호화
    fun decompress(value: Int): Double {
        return value.toDouble() / 100_000.0
    }

    // T.2. 호출 시 신규 궤적 생성
    fun startRecording(beginPower: Int) {
        val id = UUID.randomUUID().toString()
        currentTrajectoryId = id
        startTime = System.currentTimeMillis()
        pathCount = 0

        scope.launch {
            val entity = TrajectoryEntity(
                todo_id = id,
                begin_time = startTime,
                end_time = null,
                no_of_path = 0,
                created_at = startTime,
                begin_power = beginPower,
                end_power = 0,
                usage = 0
            )
            dao.insertTrajectory(entity)
        }
    }

    // T.3. 호출 시 경로 포인트 저장
    fun addPoint(lat: Double, lng: Double) {
        val id = currentTrajectoryId ?: return
        val time = System.currentTimeMillis()
        pathCount++

        scope.launch {
            val pathPoint = PathEntity(
                todo_id = id,
                time = time,
                int_lat = integerize(lat),
                int_long = integerize(lng)
            )
            dao.insertPathPoint(pathPoint)
        }
    }

    // D.3. 저장 및 정지
    fun stopAndSave(endPower: Int, onComplete: () -> Unit = {}) {
        val id = currentTrajectoryId ?: return
        val endTime = System.currentTimeMillis()

        scope.launch {
            val existing = dao.getAllTrajectories().find { it.todo_id == id }
            if (existing != null) {
                val usage = (existing.begin_power - endPower).coerceAtLeast(0)
                val updated = existing.copy(
                    end_time = endTime,
                    no_of_path = pathCount,
                    end_power = endPower,
                    usage = usage
                )
                dao.updateTrajectory(updated)
            }
            currentTrajectoryId = null
            onComplete()
        }
    }

    fun isRecording(): Boolean = currentTrajectoryId != null

    suspend fun getPaths(todoId: String): List<PathEntity> {
        return dao.getPathsForTrajectory(todoId)
    }
}
