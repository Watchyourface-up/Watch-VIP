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

        // Unique indexes are authoritative for URL uniqueness. Do not pre-check URL existence:
        // two concurrent callers must race safely at the SQLite constraint instead.
        repeat(MAX_INSERT_ATTEMPTS) { attempt ->
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
                // RESTRICT/FK errors and non-URL constraints must not be mislabeled as duplicates.
                if (dao.urlExists(validation.url)) {
                    return LinkValidation.Invalid(LinkValidation.Reason.DUPLICATE)
                }
                if (attempt < MAX_INSERT_ATTEMPTS - 1 && dao.shellSeedExists(seed)) {
                    return@repeat
                }
                throw exception
            }

            if (insertedId != -1L) return validation

            // INSERT OR IGNORE can return -1 for a URL conflict or a shellSeed collision.
            if (dao.urlExists(validation.url)) {
                return LinkValidation.Invalid(LinkValidation.Reason.DUPLICATE)
            }
            if (!dao.shellSeedExists(seed)) {
                throw IllegalStateException("Link insert was ignored without a URL or seed conflict")
            }
        }
        throw IllegalStateException("Could not allocate a unique shell seed for link insertion")
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
        const val MAX_INSERT_ATTEMPTS = 3
    }
}
