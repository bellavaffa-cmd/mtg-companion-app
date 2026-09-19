package com.mtgcompanion.app.data

import android.content.Context
import com.mtgcompanion.app.network.NetworkModule
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallIdentifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * One job a card can do in a deck. [slugs]: the Scryfall Tagger oracle tags that mean it (with
 * every tag below them); [text]: a rules-text match instead, for jobs Tagger has no tag for.
 */
data class RoleTag(val id: String, val label: String, val slugs: List<String> = emptyList(), val text: Regex? = null, val notText: Regex? = null)

/**
 * What a card does in a deck — mana ramp, card draw, removal… — from Scryfall Tagger, where the
 * community tags every card by its job. Far more reliable than reading rules text.
 *
 * Scryfall allows only 2 card searches a second, so nothing here searches per tag. Instead:
 *  - Tagger's oracle tags come as one daily file from Scryfall's file host (no rate limit),
 *    fetched about weekly; only the cards under the tags below are kept.
 *  - Cards are looked up by name, 75 a request, for their Oracle ids (and rules text).
 * Each card's tags are then remembered for a month, keyed by name. The web app does the same
 * (src/tags/roleTags.ts) with the same list.
 */
object RoleTags {
    /** The default tags, most useful first. Changing it re-tags every card (see the VERSIONs). */
    val TAGS = listOf(
        RoleTag("ramp", "Mana ramp", listOf("ramp")),
        RoleTag("mana-rock", "Mana rock", listOf("mana-rock")),
        RoleTag("mana-dork", "Mana dork", listOf("mana-dork")),
        RoleTag("land-ramp", "Land ramp", listOf("land-ramp")),
        RoleTag("mana-engine", "Mana engine", listOf("mana-increaser", "cost-reducer")),
        RoleTag("draw", "Card draw", listOf("draw")),
        RoleTag("tutor", "Tutor", listOf("tutor")),
        RoleTag("removal", "Removal", listOf("removal")),
        RoleTag("board-wipe", "Board wipe", listOf("sweeper")),
        RoleTag("counterspell", "Counterspell", listOf("counterspell")),
        RoleTag("protection", "Protection", listOf("protection")),
        RoleTag("recursion", "Recursion", listOf("recursion")),
        RoleTag("reanimate", "Reanimation", listOf("reanimate")),
        RoleTag("sacrifice-outlet", "Sacrifice outlet", listOf("sacrifice-outlet")),
        RoleTag("tokens", "Token maker", text = Regex("create[^.]*creature tokens?", RegexOption.IGNORE_CASE), notText = Regex("would create", RegexOption.IGNORE_CASE)),
        RoleTag("treasure", "Treasure", text = Regex("create[^.]*treasure", RegexOption.IGNORE_CASE)),
        RoleTag("tax", "Tax", listOf("tax")),
        RoleTag("lifegain", "Lifegain", listOf("lifegain")),
        RoleTag("burn", "Burn", listOf("burn")),
        RoleTag("graveyard-hate", "Graveyard hate", listOf("hate-graveyard")),
        RoleTag("extra-turn", "Extra turn", listOf("extra-turn")),
        RoleTag("wheel", "Wheel", listOf("wheel"))
    )

    private val byId = TAGS.associateBy { it.id }
    fun tag(id: String): RoleTag? = byId[id]
    fun label(id: String): String = byId[id]?.label ?: id
    fun byLabel(label: String): RoleTag? = TAGS.firstOrNull { it.label.equals(label, ignoreCase = true) }

    /** Per-card tags, by name. */
    private const val CARDS_VERSION = 3
    /** The tag → cards lists from Tagger's file. */
    private const val SETS_VERSION = 1
    /** Tags hardly change; a card is looked up again after this long. */
    private const val FRESH_MS = 30L * 24 * 60 * 60 * 1000
    /** Tagger's file is refreshed daily; fetching it weekly is plenty. */
    private const val SETS_FRESH_MS = 7L * 24 * 60 * 60 * 1000
    /** Names per /cards/collection request (Scryfall's maximum). */
    private const val CHUNK = 75
    /** Oracle ids are kept by their first 13 characters: unique enough, a third the size. */
    private const val ID_PREFIX = 13

    private class Known(val tags: List<String>, val at: Long)

    private var dir: File? = null
    private val cards = HashMap<String, Known>()
    /** Tag id → the (prefixes of) Oracle ids of the cards with it. */
    private var sets: Map<String, Set<String>> = emptyMap()
    private var setsAt = 0L
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
        dir = context.filesDir
        runCatching {
            val f = File(context.filesDir, "role_tags.json")
            if (!f.exists()) return@runCatching
            val o = JSONObject(f.readText())
            if (o.optInt("v") != CARDS_VERSION) return@runCatching
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

    /** Which of [TAGS] a card has, from its Oracle id (against [tagSets]) and rules text. */
    fun tagsFor(oracleId: String?, oracleText: String?, tagSets: Map<String, Set<String>> = sets): List<String> {
        val prefix = oracleId?.take(ID_PREFIX)
        val text = oracleText.orEmpty()
        return TAGS.filter { tag ->
            (prefix != null && tagSets[tag.id]?.contains(prefix) == true) ||
                (tag.text != null && tag.text.containsMatchIn(text) && tag.notText?.containsMatchIn(text) != true)
        }.map { it.id }
    }

    /**
     * Looks up the tags of whichever [names] aren't known (or are stale). Cards Scryfall doesn't
     * know get no tags — that's remembered too. False if Scryfall couldn't be reached (what was
     * found before that is kept).
     */
    suspend fun ensure(names: Iterable<String>, cardRepository: CardRepository): Boolean = lock.withLock {
        val now = System.currentTimeMillis()
        val originals = LinkedHashMap<String, String>()
        for (n in names) {
            val k = key(n)
            if (k.isEmpty() || k in originals) continue
            val known = synchronized(cards) { cards[k] }
            if (known == null || now - known.at > FRESH_MS) originals[k] = n.trim()
        }
        if (originals.isEmpty()) return@withLock true
        if (!loadSets()) return@withLock false
        var ok = true
        try {
            _progress.value = 0 to originals.size
            for ((index, chunk) in originals.entries.toList().chunked(CHUNK).withIndex()) {
                // Scryfall's own pace (network/ScryfallPacer) keeps these within its limits.
                val response = try {
                    cardRepository.getCollection(chunk.map { ScryfallIdentifier(name = it.value) })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ok = false
                    break
                }
                val found = HashMap<String, List<String>>()
                for (card in response.data) {
                    val tags = tagsFor(card.oracleId, textOf(card))
                    // A double-faced card answers to its full name; its front face's is enough too.
                    found[key(card.name)] = tags
                    found[key(card.name.substringBefore(" // "))] = tags
                }
                val at = System.currentTimeMillis()
                synchronized(cards) { for ((k, _) in chunk) cards[k] = Known(found[k].orEmpty(), at) }
                saveCards()
                _version.value += 1
                _progress.value = ((index + 1) * CHUNK).coerceAtMost(originals.size) to originals.size
            }
        } finally {
            _progress.value = null
        }
        ok
    }

    private fun textOf(card: ScryfallCard): String =
        (listOfNotNull(card.oracleText) + card.cardFaces.orEmpty().mapNotNull { it.oracleText }).joinToString("\n")

    /** Tagger's tag lists: from the phone, or fetched again when a week old (or missing). */
    private suspend fun loadSets(): Boolean = withContext(Dispatchers.IO) {
        val folder = dir ?: return@withContext false
        val file = File(folder, "role_tag_sets.json")
        if (sets.isEmpty() && file.exists()) {
            runCatching {
                val o = JSONObject(file.readText())
                if (o.optInt("v") == SETS_VERSION) {
                    val s = o.getJSONObject("sets")
                    sets = TAGS.associate { t -> t.id to (s.optJSONArray(t.id)?.let { a -> (0 until a.length()).map { a.getString(it) }.toHashSet() } ?: emptySet()) }
                    setsAt = o.getLong("at")
                }
            }
        }
        if (sets.isNotEmpty() && System.currentTimeMillis() - setsAt < SETS_FRESH_MS) return@withContext true
        val fresh = runCatching { downloadSets(folder) }.getOrNull()
        if (fresh != null) {
            sets = fresh
            setsAt = System.currentTimeMillis()
            runCatching {
                val s = JSONObject()
                fresh.forEach { (id, ids) -> s.put(id, JSONArray(ids.toList())) }
                file.writeText(JSONObject().put("v", SETS_VERSION).put("at", setsAt).put("sets", s).toString())
            }
        }
        // An old list is better than none while offline.
        sets.isNotEmpty()
    }

    /**
     * Fetches Tagger's oracle tags file (about 6 MB, from Scryfall's file host) and keeps, for each
     * of [TAGS], the cards tagged with it or with any tag under it. Read twice from a temporary
     * file, so only what's needed is ever held in memory.
     */
    private fun downloadSets(folder: File): Map<String, Set<String>> {
        val client = NetworkModule.noCacheOkHttpClient
        val meta = client.newCall(Request.Builder().url("https://api.scryfall.com/bulk-data/oracle-tags").build()).execute().use { r ->
            check(r.isSuccessful) { "bulk-data ${r.code}" }
            JSONObject(r.body!!.string())
        }
        val uri = meta.optString("jsonl_download_uri").ifEmpty { meta.getString("download_uri") }
        val gz = File(folder, "oracle-tags.jsonl.gz")
        try {
            client.newCall(Request.Builder().url(uri).build()).execute().use { r ->
                check(r.isSuccessful) { "oracle-tags ${r.code}" }
                gz.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
            }
            return parseTagFile { block ->
                GZIPInputStream(gz.inputStream()).bufferedReader().useLines { seq -> seq.filter { it.isNotBlank() }.forEach(block) }
            }
        } finally {
            gz.delete()
        }
    }

    /**
     * From Tagger's oracle tags file (one JSON tag per line, read through [readLines] — twice), the
     * Oracle id prefixes of the cards under each of [TAGS]: tagged with it, one of its other names,
     * or any tag below it.
     */
    fun parseTagFile(readLines: ((String) -> Unit) -> Unit): Map<String, Set<String>> {
        // First pass: the tree — every tag's name, other names and children.
        val children = HashMap<String, List<String>>()
        val bySlug = HashMap<String, String>()
        readLines { line ->
            val o = JSONObject(line)
            val id = o.getString("id")
            bySlug[o.getString("slug")] = id
            o.optJSONArray("aliases")?.let { a -> for (i in 0 until a.length()) bySlug.putIfAbsent(a.getString(i).lowercase().replace(' ', '-'), id) }
            children[id] = o.optJSONArray("child_ids")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        }
        // Each of our tags stands for its Tagger tags and everything under them.
        val wanted = HashMap<String, MutableSet<String>>() // Tagger tag id -> our tag ids
        for (tag in TAGS) {
            val stack = ArrayDeque(tag.slugs.mapNotNull { bySlug[it] })
            val seen = HashSet<String>()
            while (stack.isNotEmpty()) {
                val id = stack.removeLast()
                if (!seen.add(id)) continue
                wanted.getOrPut(id) { mutableSetOf() } += tag.id
                children[id]?.let { stack.addAll(it) }
            }
        }
        // Second pass: the cards under those tags.
        val out = TAGS.associate { it.id to HashSet<String>() }
        readLines { line ->
            val o = JSONObject(line)
            val ours = wanted[o.getString("id")] ?: return@readLines
            val taggings = o.optJSONArray("taggings") ?: return@readLines
            for (i in 0 until taggings.length()) {
                val oracle = taggings.getJSONObject(i).optString("oracle_id").takeIf { it.isNotEmpty() } ?: continue
                for (t in ours) out.getValue(t) += oracle.take(ID_PREFIX)
            }
        }
        check(out.values.any { it.isNotEmpty() }) { "no tags found" }
        return out
    }

    private suspend fun saveCards() = withContext(Dispatchers.IO) {
        val folder = dir ?: return@withContext
        val c = JSONObject()
        synchronized(cards) {
            for ((name, known) in cards) c.put(name, JSONObject().put("t", JSONArray(known.tags)).put("at", known.at))
        }
        runCatching { File(folder, "role_tags.json").writeText(JSONObject().put("v", CARDS_VERSION).put("cards", c).toString()) }
    }
}
