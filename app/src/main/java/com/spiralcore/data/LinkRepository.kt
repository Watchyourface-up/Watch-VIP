package com.spiralcore.data

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

        var seed: Int
        do {
            seed = random.nextInt()
        } while (dao.shellSeedExists(seed))

        dao.insertLink(
            LinkEntity(
                url = validation.url,
                title = validation.host,
                categoryId = categoryId,
                createdAt = System.currentTimeMillis(),
                shellSeed = seed,
            ),
        )
        return validation
    }
}
