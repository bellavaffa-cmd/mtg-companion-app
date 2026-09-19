package com.mtgcompanion.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One job a card can do in a deck, and the Scryfall search that finds every card doing it. */
data class RoleTag(val id: String, val label: String, val query: String)

/**
 * What a card does in a deck — mana ramp, card draw, removal… — from Scryfall Tagger, where the
 * community tags every card by its job (searchable as `otag:ramp`). Far more reliable than reading
 * rules text; a few jobs Tagger has no tag for are a rules-text search instead.
 *
 * Tags are looked up by card name, a few names per search, one search per tag, and remembered on
 * the phone for a month — so a deck or collection is tagged once and opens instantly after. The
 * web app does the same (src/tags/roleTags.ts) with the same list.
 */
object RoleTags {
    /** The default tags, most useful first. Changing it re-tags every card (see CACHE_VERSION). */
    val TAGS = listOf(
        RoleTag("ramp", "Mana ramp", "otag:ramp"),
        RoleTag("mana-rock", "Mana rock", "otag:mana-rock"),
        RoleTag("mana-dork", "Mana dork", "otag:mana-dork"),
        RoleTag("land-ramp", "Land ramp", "otag:land-ramp"),
        RoleTag("mana-engine", "Mana engine", "(otag:mana-doubler or otag:cost-reducer)"),
        RoleTag("draw", "Card draw", "otag:draw"),
        RoleTag("tutor", "Tutor", "otag:tutor"),
        RoleTag("removal", "Removal", "otag:removal"),
        RoleTag("board-wipe", "Board wipe", "otag:board-wipe"),
        RoleTag("counterspell", "Counterspell", "otag:counterspell"),
        RoleTag("protection", "Protection", "otag:protection"),
        RoleTag("recursion", "Recursion", "otag:recursion"),
        RoleTag("reanimate", "Reanimation", "otag:reanimate"),
        RoleTag("sacrifice-outlet", "Sacrifice outlet", "otag:sacrifice-outlet"),
        RoleTag("tokens", "Token maker", "(o:/create[^.]*creature tokens?/ -o:\"would create\")"),
        RoleTag("treasure", "Treasure", "o:/create[^.]*treasure/"),
        RoleTag("tax", "Tax", "otag:tax"),
        RoleTag("lifegain", "Lifegain", "otag:lifegain"),
        RoleTag("burn", "Burn", "otag:burn"),
        RoleTag("graveyard-hate", "Graveyard hate", "otag:graveyard-hate"),
        RoleTag("extra-turn", "Extra turn", "otag:extra-turn"),
        RoleTag("wheel", "Wheel", "otag:wheel")
    )

    private val byId = TAGS.associateBy { it.id }
    fun tag(id: String): RoleTag? = byId[id]
    fun label(id: String): String = byId[id]?.label ?: id
    fun byLabel(label: String): RoleTag? = TAGS.firstOrNull { it.label.equals(label, ignoreCase = true) }

    private const val CACHE_VERSION = 2
    /** Tags hardly change; a card is looked up again after this long. */
    private const val FRESH_MS = 30L * 24 * 60 * 60 * 1000
    /** Names per search: keeps the address well under Scryfall's limit. */
    private const val CHUNK = 40
    /** Scryfall asks for 50–100 ms between requests. */
    private const val SPACING_MS = 100L

    private class Known(val tags: List<String>, val at: Long)

    private var file: File? = null
    private val cards = HashMap<String, Known>()
    private val lock = Mutex()

    private val _version = MutableStateFlow(0)
    /** Bumped whenever more cards' tags become known, for screens to re-read [tagsOf]. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)
    /** (done, total) while a lookup runs. */
    val progress: StateFlow<Pair<Int, Int>?> = _progress.asStateFlow()

    fun key(name: String) = name.trim().lowercase()

    /** Loads what's been looked up before. Called once, from the Application. */
    fun init(context: Context) {
        val f = File(context.filesDir, "role_tags.json")
        file = f
        runCatching {
            if (!f.exists()) return
            val o = JSONObject(f.readText())
            if (o.optInt("v") != CACHE_VERSION) return
            val c = o.getJSONObject("cards")
            synchronized(cards) {
                for (name in c.keys()) {
                    val e = c.getJSONObject(name)
                    val t = e.getJSONArray("t")
                    cards[name] = Known((0 until t.length()).map { t.getString(it) }, e.getLong("at"))
                }
            }
        }
        _version.value += 1
    }

    /** A card's tag ids, if it has been looked up. */
    fun tagsOf(name: String): List<String>? = synchronized(cards) { cards[key(name)]?.tags }

    /** Whether [query] finds a card by its name or one of its tags' labels. */
    fun matches(name: String, tagIds: List<String>, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return name.contains(q, ignoreCase = true) || tagIds.any { label(it).contains(q, ignoreCase = true) }
    }

    /** The tags among [tagIds] that [query] matched, for "tag: Mana ramp" under a search. */
    fun matched(tagIds: List<String>, query: String): List<String> {
        val q = query.trim()
        return if (q.isEmpty()) emptyList() else tagIds.filter { label(it).contains(q, ignoreCase = true) }
    }

    /**
     * Looks up the tags of whichever [names] aren't known (or are stale), one request at a time.
     * Cards found by no search get no tags — that's remembered too. False if Scryfall couldn't be
     * reached (what was found before that is kept).
     */
    suspend fun ensure(names: Iterable<String>, cardRepository: CardRepository): Boolean = lock.withLock {
        val now = System.currentTimeMillis()
        val wanted = names.map(::key).distinct().filter { n ->
            n.isNotEmpty() && '"' !in n && synchronized(cards) { cards[n] }.let { it == null || now - it.at > FRESH_MS }
        }
        if (wanted.isEmpty()) return@withLock true
        var ok = true
        try {
            _progress.value = 0 to wanted.size
            for ((index, chunk) in wanted.chunked(CHUNK).withIndex()) {
                val found = chunk.associateWith { mutableSetOf<String>() }
                val names = chunk.joinToString(" or ") { "!\"$it\"" }
                try {
                    for (tag in TAGS) {
                        cardRepository.search("${tag.query} ($names)").cards.forEach { card ->
                            // A double-faced card answers to its full name; its front face's is enough to match.
                            found[key(card.name)]?.add(tag.id)
                            found[key(card.name.substringBefore(" // "))]?.add(tag.id)
                        }
                        delay(SPACING_MS)
                    }
                } catch (e: Exception) {
                    ok = false
                    break
                }
                val at = System.currentTimeMillis()
                synchronized(cards) {
                    for (n in chunk) cards[n] = Known(TAGS.filter { it.id in found.getValue(n) }.map { it.id }, at)
                }
                save()
                _version.value += 1
                _progress.value = ((index + 1) * CHUNK).coerceAtMost(wanted.size) to wanted.size
            }
        } finally {
            _progress.value = null
        }
        ok
    }

    private suspend fun save() = withContext(Dispatchers.IO) {
        val f = file ?: return@withContext
        val c = JSONObject()
        synchronized(cards) {
            for ((name, known) in cards) c.put(name, JSONObject().put("t", JSONArray(known.tags)).put("at", known.at))
        }
        runCatching { f.writeText(JSONObject().put("v", CACHE_VERSION).put("cards", c).toString()) }
    }
}
