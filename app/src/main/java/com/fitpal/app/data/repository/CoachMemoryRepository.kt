package com.fitpal.app.data.repository

import com.fitpal.app.data.local.dao.CoachMemoryDao
import com.fitpal.app.data.local.entity.CoachMemoryEntity
import com.fitpal.app.domain.model.CoachMemoryParser
import com.fitpal.app.domain.model.MemoryDiff
import com.fitpal.app.domain.model.MemoryLine
import com.fitpal.app.domain.model.MemorySection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the AI coach knows about the user (the 17 Nov review, #2). The coach changes it only through
 * [apply] (a diff read off an overview's `MEMORY:` line); the user edits it on the Coach tab's
 * "What your coach knows" page, and every line they touch becomes theirs ([MemoryLine.mine]).
 */
@Singleton
class CoachMemoryRepository @Inject constructor(
    private val dao: CoachMemoryDao,
    private val settings: SettingsRepository
) {
    val lines: Flow<List<MemoryLine>> = dao.observeAll().map { rows -> rows.mapNotNull { it.toLine() } }

    suspend fun current(): List<MemoryLine> = dao.getAll().mapNotNull { it.toLine() }

    /**
     * Apply the coach's changes from one overview, and mark the answers it was shown as taken in
     * ([foldedAt] = when that overview started). Lines the user wrote are never changed (the parser
     * already dropped any such change).
     */
    suspend fun apply(diff: MemoryDiff, foldedAt: Long) {
        val now = System.currentTimeMillis()
        val rows = dao.getAll().associateBy { it.id }
        diff.remove.forEach { id -> if (rows[id]?.mine == false) dao.delete(id) }
        diff.update.forEach { (id, text) ->
            rows[id]?.takeIf { !it.mine }?.let { dao.update(it.copy(text = text, updatedAt = now)) }
        }
        if (diff.add.isNotEmpty()) {
            dao.insertAll(diff.add.map { CoachMemoryEntity(section = it.section.id, text = it.text, sourceDate = it.date, createdAt = now, updatedAt = now) })
        }
        settings.setMemoryFoldedAt(foldedAt)
        // The old one-paragraph habit notes have been folded in; the memory replaces them.
        if (settings.habitSummary.value.isNotBlank()) settings.setHabitSummary("")
    }

    /** "+ Add something yourself": a line of the user's own, dated today. */
    suspend fun add(section: MemorySection, text: String) {
        val clean = text.trim().take(CoachMemoryParser.MAX_LINE)
        if (clean.isEmpty()) return
        dao.insert(CoachMemoryEntity(section = section.id, text = clean, sourceDate = LocalDate.now().toString(), mine = true))
    }

    /** Edit a line's words — it becomes the user's own, so the coach won't rewrite it. */
    suspend fun edit(id: Long, text: String) {
        val clean = text.trim().take(CoachMemoryParser.MAX_LINE)
        if (clean.isEmpty()) return
        val row = dao.getAll().firstOrNull { it.id == id } ?: return
        dao.update(row.copy(text = clean, mine = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun remove(id: Long) = dao.delete(id)

    /** Put a removed line back (Undo). */
    suspend fun restore(line: MemoryLine) {
        dao.insert(CoachMemoryEntity(section = line.section.id, text = line.text, sourceDate = line.sourceDate, mine = line.mine))
    }

    private fun CoachMemoryEntity.toLine(): MemoryLine? =
        MemorySection.of(section)?.let { MemoryLine(id, it, text, sourceDate, mine) }
}
