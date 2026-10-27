package com.spiralcore.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spiralcore.domain.LinkValidation
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkRepositoryConcurrencyTest {
    private lateinit var db: SpiralDatabase
    private lateinit var repository: LinkRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SpiralDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LinkRepository(db.linkDao())
        runBlocking { repository.ensureStarterCategories() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun concurrentSameUrlInsertHasOneWinnerAndOneDuplicate() = runBlocking {
        val categoryId = db.linkDao().observeCategories().let { flow ->
            kotlinx.coroutines.flow.first(flow).first().id
        }

        val results = (1..2).map {
            async {
                repository.createLink("https://example.com/concurrent", categoryId)
            }
        }.awaitAll()

        assertEquals(1, results.count { it is LinkValidation.Valid })
        assertEquals(
            1,
            results.count { it == LinkValidation.Invalid(LinkValidation.Reason.DUPLICATE) },
        )

        db.query(
            "SELECT COUNT(*) FROM links WHERE url = 'https://example.com/concurrent/'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
    }
}
