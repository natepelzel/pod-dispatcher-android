package dev.poddispatcher

import android.content.Context
import dev.poddispatcher.engine.Dispatcher
import dev.poddispatcher.engine.SchemaRepository
import dev.poddispatcher.engine.resolve.ItunesApiResolver
import dev.poddispatcher.engine.resolve.PodcastIndexResolver
import dev.poddispatcher.engine.resolve.Resolver
import dev.poddispatcher.engine.resolve.ScrapeResolver
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** Tiny service locator — the app is small enough not to need a DI framework. */
object Graph {
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var repository: SchemaRepository? = null

    fun repository(context: Context): SchemaRepository =
        repository ?: synchronized(this) {
            repository ?: SchemaRepository(context.applicationContext, httpClient)
                .also { repository = it }
        }

    private fun resolvers(): List<Resolver> = listOf(
        ItunesApiResolver(httpClient),
        ScrapeResolver(httpClient),
        PodcastIndexResolver(),
    )

    fun dispatcher(context: Context): Dispatcher =
        Dispatcher(repository(context), resolvers(), Prefs(context))
}
