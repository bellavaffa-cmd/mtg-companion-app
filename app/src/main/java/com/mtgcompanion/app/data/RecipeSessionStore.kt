package com.mtgcompanion.app.data

import android.content.Context

/** A card the scanner saw but couldn't read: what it made of it. */
data class RecipeMiss(val at: Long, val seen: String)

/** Checking one pile by scanning it again: the names that belonged, and the cards that didn't. */
data class FlaggedCard(val name: String, val line: String)
data class PileChecking(val pile: Int, val checked: List<String> = emptyList(), val flagged: List<FlaggedCard> = emptyList())

/** A sort with a recipe under way (SortRecipes.kt): the recipe, the cards sorted, the ones unread. The web app's RecipeSessionState. */
data class RecipeSessionState(
    val recipe: SortRecipe,
    val scans: List<RecipeScan> = emptyList(),
    val misses: List<RecipeMiss> = emptyList(),
    val startedAt: Long = 0L,
    val checking: PileChecking? = null
)

/** How a sort is heard: the pile said out loud, and cards taken without a tap. */
data class RecipeVoice(val speak: Boolean = true, val auto: Boolean = true)

/** A new sort with [recipe]. */
fun startRecipeSession(recipe: SortRecipe, now: Long = System.currentTimeMillis()): RecipeSessionState =
    RecipeSessionState(sortRecipe(recipe), startedAt = now)

/**
 * Sorting with a recipe: the sort under way, kept on this phone so leaving the scanner or restarting
 * the phone doesn't lose it, how it's heard, and the recipe used last. The web app keeps the same in
 * the browser (src/collection/recipeSession.ts).
 */
class RecipeSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sort_recipes", Context.MODE_PRIVATE)
    private val sessionAdapter by lazy { localMoshi.adapter(RecipeSessionState::class.java) }
    private val voiceAdapter by lazy { localMoshi.adapter(RecipeVoice::class.java) }

    fun session(): RecipeSessionState? = prefs.getString(SESSION, null)
        ?.let { runCatching { sessionAdapter.fromJson(it) }.getOrNull() }
        ?.let { it.copy(recipe = sortRecipe(it.recipe)) }

    fun saveSession(session: RecipeSessionState?) {
        prefs.edit().apply { if (session == null) remove(SESSION) else putString(SESSION, sessionAdapter.toJson(session)) }.apply()
    }

    fun voice(): RecipeVoice = prefs.getString(VOICE, null)?.let { runCatching { voiceAdapter.fromJson(it) }.getOrNull() } ?: RecipeVoice()

    fun saveVoice(voice: RecipeVoice) {
        prefs.edit().putString(VOICE, voiceAdapter.toJson(voice)).apply()
    }

    fun lastRecipeId(): String? = prefs.getString(LAST, null)

    fun setLastRecipeId(id: String) {
        prefs.edit().putString(LAST, id).apply()
    }

    private companion object {
        const val SESSION = "session"
        const val VOICE = "voice"
        const val LAST = "last"
    }
}
