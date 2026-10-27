package com.spiralcore.data

import android.database.sqlite.SQLiteConstraintException
import com.spiralcore.domain.LinkValidation
import com.spiralcore.domain.LinkValidator
import kotlinx.coroutines.flow.Flow
import java.security.SecureRandom

class LinkRepository(private val dao: LinkDao) {
    private val random = SecureRandom()

    fun observeLinks(): Flow<List<LinkWithCategory>> = dao.observeLinks()
    fun observeCategories(): Flow<List<CategoryEntity>> = dao.observeCategories()

    suspend fun ensureStarterCategories() {
        if (dao.categoryCount() == 0) {
            dao.insertCategories(
                listOf(
                    CategoryEntity(name = "شخصي", position = 0),
                    CategoryEntity(name = "قراءة", position = 1),
                    CategoryEntity(name = "عمل", position = 2),
                ),
            )
        }
    }

    suspend fun createLink(rawUrl: String, categoryId: Long): LinkValidation {
        val validation = LinkValidator.validate(rawUrl)
        if (validation !is LinkValidation.Valid) return validation

        // The unique indexes are the source of truth. IGNORE returns -1 for a conflict
        // (including a concurrent duplicate URL insert) without a preflight race.
        val seed = nextUniqueSeed()
        val insertedId = try {
            dao.insertLink(
                LinkEntity(
                    url = validation.url,
                    title = validation.host,
                    categoryId = categoryId,
                    createdAt = System.currentTimeMillis(),
                    shellSeed = seed,
                ),
            )
        } catch (exception: SQLiteConstraintException) {
            // A shellSeed collision is rare but possible. Only translate a confirmed URL
            // conflict to DUPLICATE; keep unrelated FK/constraint failures visible.
            if (dao.urlExists(validation.url)) {
                return LinkValidation.Invalid(LinkValidation.Reason.DUPLICATE)
            }
            throw exception
        }

        if (insertedId == -1L) {
            return LinkValidation.Invalid(LinkValidation.Reason.DUPLICATE)
        }
        return validation
    }

    private suspend fun nextUniqueSeed(): Int {
        repeat(MAX_SEED_ATTEMPTS) {
            val candidate = random.nextInt()
            if (!dao.shellSeedExists(candidate)) return candidate
        }
        throw IllegalStateException("Could not allocate a unique shell seed")
    }

    suspend fun deleteLink(linkId: Long) {
        dao.deleteLink(linkId)
    }

    private companion object {
        const val MAX_SEED_ATTEMPTS = 16
    }
}
