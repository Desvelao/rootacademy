package app.root_academy.data

import android.content.Context
import app.root_academy.core.content.Catalog
import app.root_academy.core.content.ContentSource
import app.root_academy.core.content.PackLoader
import java.util.Locale

/** Reads packs from the APK's `content/` assets. */
class AssetSource(private val context: Context) : ContentSource {
    override fun list(dir: String) = context.assets.list("content/$dir")?.toList() ?: emptyList()
    override fun read(path: String) = try {
        context.assets.open("content/$path").bufferedReader().use { it.readText() }
    } catch (_: java.io.FileNotFoundException) {
        null
    }
}

/** Loads the content packs from assets once and keeps them in memory (a few hundred KB of JSON). */
class ContentRepository(private val source: ContentSource) {
    constructor(context: Context) : this(AssetSource(context))

    val catalog: Catalog by lazy { PackLoader(source).loadAll() }

    /** Language for pack text: the app locale if shipped, else English. */
    fun language(): String = Locale.getDefault().language.takeIf { it == "es" } ?: "en"
}
