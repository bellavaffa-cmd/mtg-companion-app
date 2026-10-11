package com.mtgcompanion.app.data

import android.content.Context
import com.mtgcompanion.app.network.NetworkModule
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallCollectionResponse
import com.mtgcompanion.app.network.scryfall.ScryfallIdentifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * every tag below them); [text]: a rules-text match instead, for jobs Tagger has no tag for;
 * [derived]: worked out from the card's type line and rules text by [RoleTags.textTags] (Land,
 * Fixing, Vanilla).
 */
data class RoleTag(
    val id: String,
    val label: String,
    val slugs: List<String> = emptyList(),
    val text: Regex? = null,
    val notText: Regex? = null,
    val derived: Boolean = false
)

/** A card to tag: its name (what screens ask by) and, when known, the printing's Scryfall id. */
data class CardRef(val name: String, val scryfallId: String? = null)

/**
 * What a card's rules text and type line say, kept with its lookup so its tags can be worked out
 * again without Scryfall when Tagger's file changes. [always]: true of the card whatever Tagger says
 * (Token maker, Treasure, Land, Fixing, Vanilla). [fallback]: Mana ramp / Card draw / Removal read
 * from the text — used only when Tagger has nothing for the card, so Tagger stays the authority.
 */
data class TextTags(val always: List<String> = emptyList(), val fallback: List<String> = emptyList())

/**
 * One card's lookup. [tags]: its tag ids as worked out against Tagger's file [file]. [oracle]: the
 * (prefix of the) Oracle id, null when Scryfall didn't know the card. [identity]: colour identity
 * ("WU"; "" colourless), null when not known.
 */
data class TagRecord(
    val tags: List<String>,
    val at: Long,
    val identity: String? = null,
    val oracle: String? = null,
    val text: TextTags = TextTags(),
    val file: String? = null
)

/**
 * What a card does in a deck — mana ramp, card draw, removal… — from Scryfall Tagger, where the
 * community tags every card by its job. Far more reliable than reading rules text.
 *
 * Scryfall allows only 2 card searches a second, so nothing here searches per tag. Instead:
 *  - Tagger's oracle tags come as one daily file from Scryfall's file host (no rate limit),
 *    fetched about weekly; only the cards under the tags below are kept.
 *  - Cards are looked up 75 a request — by the printing's Scryfall id where it's known (so tokens,
 *    proxies and renamed cards resolve), by name otherwise — for their Oracle ids and rules text.
 *  - The Oracle id is kept with each card, so a new Tagger file re-tags every card on the phone
 *    without asking Scryfall again.
 * A card's tags are remembered for a month (one with none, a week), keyed by name. TagPass.kt tags
 * every card owned, wished for or in a deck in the background. The web app does the same
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
        RoleTag("wheel", "Wheel", listOf("wheel")),
        // From the type line and rules text (textTags): jobs Tagger doesn't tag a card with.
        RoleTag("land", "Land", derived = true),
        RoleTag("fixing", "Fixing", derived = true),
        RoleTag("vanilla", "Vanilla", derived = true)
    )

    private val byId = TAGS.associateBy { it.id }
    fun tag(id: String): RoleTag? = byId[id]
    fun label(id: String): String = byId[id]?.label ?: id
    fun byLabel(label: String): RoleTag? = TAGS.firstOrNull { it.label.equals(label, ignoreCase = true) }

    /** Per-card tags (and colour identity), by name. 5: Oracle id, text tags and Tagger file kept with each. */
    private const val CARDS_VERSION = 5
    /** The tag → cards lists from Tagger's file. 2: the file's date kept with them. */
    private const val SETS_VERSION = 2
    /** Tags hardly change; a card is looked up again after this long. */
    const val FRESH_MS = 30L * 24 * 60 * 60 * 1000
    /** A card found with no tags (or not found at all) is looked up again sooner. */
    const val EMPTY_FRESH_MS = 7L * 24 * 60 * 60 * 1000
    /** Tagger's file is refreshed daily; fetching it weekly is plenty. */
    private const val SETS_FRESH_MS = 7L * 24 * 60 * 60 * 1000
    /** Identifiers per /cards/collection request (Scryfall's maximum). */
    const val CHUNK = 75
    /** Oracle ids are kept by their first 13 characters: unique enough, a third the size. */
    private const val ID_PREFIX = 13
    /** Scryfall unreachable mid-lookup: waits before trying the same request again. Then it stops (and is resumed later). */
    val RETRY_MS = listOf(2_000L, 8_000L, 30_000L)

    private var dir: File? = null
    private val cards = HashMap<String, TagRecord>()
    /** Card name (key) → the Scryfall id of a printing of it, from the cards the user has (TagPass). */
    private val idHints = HashMap<String, String>()
    /** Tag id → the (prefixes of) Oracle ids of the cards with it. */
    private var sets: Map<String, Set<String>> = emptyMap()
    private var setsAt = 0L
    /** Which Tagger file [sets] came from (its date, from Scryfall). */
    private var setsFile: String? = null
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
            val read = readCards(f.readText())
            synchronized(cards) { cards.putAll(read) }
        }
        _version.value += 1
    }

    /**
     * The cards in a role_tags.json. One written before Oracle ids were kept (version 4) still shows
     * its tags, but every card in it counts as due to be looked up again.
     */
    fun readCards(json: String): Map<String, TagRecord> {
        val o = JSONObject(json)
        val v = o.optInt("v")
        if (v != CARDS_VERSION && v != 4) return emptyMap()
        val c = o.getJSONObject("cards")
        val out = HashMap<String, TagRecord>()
        for (name in c.keys()) {
            val e = c.getJSONObject(name)
            val identity = if (e.has("c")) e.getString("c") else null
            out[name] = if (v == 4) TagRecord(strings(e.getJSONArray("t")), 0L, identity)
            else TagRecord(
                tags = strings(e.getJSONArray("t")),
                at = e.getLong("at"),
                identity = identity,
                oracle = e.optString("o").ifEmpty { null },
                text = TextTags(e.optJSONArray("r")?.let(::strings).orEmpty(), e.optJSONArray("x")?.let(::strings).orEmpty()),
                file = e.optString("f").ifEmpty { null }
            )
        }
        return out
    }

    /** [cards] as role_tags.json holds them. */
    fun writeCards(cards: Map<String, TagRecord>): String {
        val c = JSONObject()
        for ((name, r) in cards) {
            val e = JSONObject().put("t", JSONArray(r.tags)).put("at", r.at)
            if (r.identity != null) e.put("c", r.identity)
            if (r.oracle != null) e.put("o", r.oracle)
            if (r.text.always.isNotEmpty()) e.put("r", JSONArray(r.text.always))
            if (r.text.fallback.isNotEmpty()) e.put("x", JSONArray(r.text.fallback))
            if (r.file != null) e.put("f", r.file)
            c.put(name, e)
        }
        return JSONObject().put("v", CARDS_VERSION).put("cards", c).toString()
    }

    private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }

    /** A card's tag ids, if it has been looked up. */
    fun tagsOf(name: String): List<String>? = synchronized(cards) { cards[key(name)]?.tags }

    /** A card's colour identity ("WU"; "" for colourless), if it has been looked up. */
    fun identityOf(name: String): String? = synchronized(cards) { cards[key(name)]?.identity }

    /** Looked up, and no tag fits it — the Cards filter's "Untagged". A card not looked up yet isn't. */
    fun untagged(name: String): Boolean = tagsOf(name)?.isEmpty() == true

    /** Of [names], how many have at least one tag, and how many there are (each name once): Data and speed's "N of M tagged". */
    fun coverage(names: Iterable<String>): Pair<Int, Int> {
        val keys = names.map(::key).filter { it.isNotEmpty() }.toSet()
        val tagged = synchronized(cards) { keys.count { cards[it]?.tags?.isNotEmpty() == true } }
        return tagged to keys.size
    }

    /** Remembers which printing each of [refs] is, so a lookup by name alone can go by id. */
    fun hint(refs: Iterable<CardRef>) {
        synchronized(idHints) {
            for (r in refs) {
                val id = r.scryfallId?.takeIf(::isScryfallId) ?: continue
                idHints.putIfAbsent(key(r.name), id)
            }
        }
    }

    /**
     * Makes [names] due, so the next pass looks them up afresh (Data and speed's "Retag now"); their
     * tags still show until then.
     */
    suspend fun forget(names: Iterable<String>) {
        lock.withLock {
            synchronized(cards) { names.forEach { n -> cards[key(n)]?.let { cards[key(n)] = it.copy(at = 0L) } } }
            saveCards()
        }
        _version.value += 1
    }

    /** Whether any of [refs] is due to be looked up. */
    fun anyDue(refs: Iterable<CardRef>, now: Long = System.currentTimeMillis()): Boolean =
        synchronized(cards) { refs.any { key(it.name).isNotEmpty() && isDue(cards[key(it.name)], now, setsFile) } }

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

    // ---- What the rules text and type line say ----

    private val LAND = Regex("""\bLand\b""")
    private val CREATURE = Regex("""\bCreature\b""")
    private val ARTIFACT = Regex("""\bArtifact\b""")
    private val ANY_COLOR = Regex("""add [^.]*mana of any (one )?(color|colour|type)""", RegexOption.IGNORE_CASE)
    private val FETCH = Regex("""search your library for an? [^.]*\b(land|plains|island|swamp|mountain|forest)\b[^.]*cards?""", RegexOption.IGNORE_CASE)
    private val MANA_SYMBOL = Regex("""\{([WUBRG])\}""")
    private val RAMP = Regex("""\badd \{[WUBRGC]\}|\badd (one|two|three|x) mana\b|search your library for [^.]*land cards?[^.]*onto the battlefield""", RegexOption.IGNORE_CASE)
    private val DRAW = Regex("""\bdraws? (a|an|two|three|four|five|seven|x|that many|cards equal)\b[^.]*\bcards?\b""", RegexOption.IGNORE_CASE)
    private val REMOVAL = Regex("""\b(destroy|exile) (target|each|all) [^.]*\b(creature|artifact|enchantment|planeswalker|permanent)s?\b|deals? (\d+|x) damage to (any target|target creature)""", RegexOption.IGNORE_CASE)

    /**
     * What a card's rules text ([oracleText], every face) and type line say it does — see [TextTags].
     * [producedMana]: Scryfall's produced_mana, the colours it can make (when known).
     */
    fun textTags(oracleText: String?, typeLine: String?, producedMana: List<String>? = null): TextTags {
        val text = oracleText.orEmpty()
        val type = typeLine.orEmpty()
        // A modal double-faced spell with a land back is cast as the spell (as DeckBuilding's isLand).
        val front = type.substringBefore(" // ")
        val always = mutableSetOf<String>()
        for (t in TAGS) if (t.text != null && t.text.containsMatchIn(text) && t.notText?.containsMatchIn(text) != true) always += t.id
        val land = LAND.containsMatchIn(front)
        if (land) always += "land"
        if (land || ARTIFACT.containsMatchIn(front)) {
            val colours = producedMana.orEmpty().filter { it in setOf("W", "U", "B", "R", "G") }.toSet() +
                text.lines().filter { it.contains("add ", ignoreCase = true) }.flatMap { line -> MANA_SYMBOL.findAll(line).map { it.groupValues[1] } }
            if (colours.size >= 2 || ANY_COLOR.containsMatchIn(text) || FETCH.containsMatchIn(text)) always += "fixing"
        }
        if (CREATURE.containsMatchIn(front) && text.isBlank()) always += "vanilla"
        val fallback = mutableListOf<String>()
        if (!land && RAMP.containsMatchIn(text)) fallback += "ramp"
        if (DRAW.containsMatchIn(text)) fallback += "draw"
        if (REMOVAL.containsMatchIn(text)) fallback += "removal"
        return TextTags(TAGS.map { it.id }.filter { it in always }, fallback)
    }

    /**
     * A card's tag ids: Tagger's for its Oracle id [oracle] (a prefix is fine) in [tagSets], what its
     * text always says, and — only when Tagger has none of its tags for it — the text's fallbacks.
     */
    fun combine(oracle: String?, text: TextTags, tagSets: Map<String, Set<String>>): List<String> {
        val prefix = oracle?.take(ID_PREFIX)
        val tagger = if (prefix == null) emptySet() else TAGS.filter { it.slugs.isNotEmpty() && tagSets[it.id]?.contains(prefix) == true }.map { it.id }.toSet()
        val ids = tagger + text.always + (if (tagger.isEmpty()) text.fallback else emptyList())
        return TAGS.map { it.id }.filter { it in ids }
    }

    /** Which of [TAGS] a card has, from its Oracle id (against [tagSets]), rules text and type line. */
    fun tagsFor(
        oracleId: String?,
        oracleText: String?,
        tagSets: Map<String, Set<String>> = sets,
        typeLine: String? = null,
        producedMana: List<String>? = null
    ): List<String> = combine(oracleId, textTags(oracleText, typeLine, producedMana), tagSets)

    /**
     * Whether [r] is due to be looked up (again): never looked up; a month old; a week old with no
     * tags; or not found by Scryfall under an older Tagger file than [file].
     */
    fun isDue(r: TagRecord?, now: Long, file: String?): Boolean {
        if (r == null) return true
        val age = now - r.at
        if (age < 0 || age > FRESH_MS) return true
        if (r.tags.isEmpty() && age > EMPTY_FRESH_MS) return true
        if (r.oracle == null && file != null && r.file != file) return true
        return false
    }

    /**
     * [records] worked out again against Tagger's file [file] ([tagSets]): every card whose Oracle id
     * is known gets its tags from the new file, without asking Scryfall. Null when none needed it.
     */
    fun retagged(records: Map<String, TagRecord>, tagSets: Map<String, Set<String>>, file: String): Map<String, TagRecord>? {
        var changed = false
        val out = HashMap<String, TagRecord>(records.size)
        for ((k, r) in records) {
            out[k] = if (r.oracle != null && r.file != file) {
                changed = true
                r.copy(tags = combine(r.oracle, r.text, tagSets), file = file)
            } else r
        }
        return if (changed) out else null
    }

    private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    /** A Scryfall printing id (a UUID) — not a sample's or a hand-made card's made-up id. */
    fun isScryfallId(id: String) = UUID.matches(id)

    // ---- Looking cards up ----

    /**
     * Looks up the tags of whichever [names] aren't known (or are due again), by the printing's id
     * where one is known ([hint]). False if Scryfall couldn't be reached (what was found before that
     * is kept, and the rest is looked up next time).
     */
    suspend fun ensure(names: Iterable<String>, cardRepository: CardRepository): Boolean {
        val refs = synchronized(idHints) { names.map { CardRef(it, idHints[key(it)]) } }
        return ensureRefs(refs, cardRepository)
    }

    /**
     * Looks up the tags of whichever [refs] aren't known (or are due again). Each 75 found are saved at
     * once, so a lookup cut short (offline, the app closed) carries on where it stopped. A failed
     * request is tried again after [retries]; then this gives up, answering false. [pauseMs]: a wait
     * between requests, for the background pass to go gently.
     */
    suspend fun ensureRefs(
        refs: Iterable<CardRef>,
        cardRepository: CardRepository,
        retries: List<Long> = RETRY_MS,
        pauseMs: Long = 0L
    ): Boolean = lock.withLock {
        val now = System.currentTimeMillis()
        val due = LinkedHashMap<String, CardRef>()
        synchronized(cards) {
            for (r in refs) {
                val k = key(r.name)
                if (k.isEmpty()) continue
                val known = due[k]
                if (known != null) {
                    if (known.scryfallId == null && r.scryfallId != null) due[k] = r
                    continue
                }
                if (isDue(cards[k], now, setsFile)) due[k] = r
            }
        }
        if (due.isEmpty()) return@withLock true
        // Without Tagger's file the cards still get their text tags; they're re-tagged when it comes.
        loadSets()
        val tagSets = sets
        val file = setsFile
        try {
            _progress.value = 0 to due.size
            lookUp(
                due.values.toList(),
                fetch = { cardRepository.getCollection(it) },
                tagSets = tagSets,
                file = file,
                retries = retries,
                pauseMs = pauseMs,
                onFound = { found, done ->
                    synchronized(cards) {
                        for ((k, r) in found) {
                            // A card's other name (its Scryfall name, a front face) doesn't replace a fresh lookup of its own.
                            if (k in due || isDue(cards[k], now, file)) cards[k] = r
                        }
                    }
                    saveCards()
                    _version.value += 1
                    _progress.value = done to due.size
                }
            )
        } finally {
            _progress.value = null
        }
    }

    /**
     * Looks [refs] up through [fetch] (Scryfall's /cards/collection), 75 a request: by id where the
     * ref has a Scryfall id, by name otherwise — and by name after all for an id Scryfall doesn't
     * know. Each request's cards go to [onFound] (name key → record, with the done count) as they
     * come, every ref answered: one Scryfall doesn't know gets an empty record. A failed request is
     * tried again after each of [retries]; then this stops, answering false.
     */
    suspend fun lookUp(
        refs: List<CardRef>,
        fetch: suspend (List<ScryfallIdentifier>) -> ScryfallCollectionResponse,
        tagSets: Map<String, Set<String>>,
        file: String?,
        retries: List<Long> = RETRY_MS,
        pauseMs: Long = 0L,
        now: () -> Long = System::currentTimeMillis,
        sleep: suspend (Long) -> Unit = { delay(it) },
        onFound: suspend (Map<String, TagRecord>, Int) -> Unit
    ): Boolean {
        val queue = ArrayDeque(refs.map { r -> r.copy(scryfallId = r.scryfallId?.takeIf(::isScryfallId)) })
        var done = 0
        var first = true
        while (queue.isNotEmpty()) {
            val chunk = List(minOf(CHUNK, queue.size)) { queue.removeFirst() }
            if (!first && pauseMs > 0) sleep(pauseMs)
            first = false
            val ids = chunk.map { r -> r.scryfallId?.let { ScryfallIdentifier(id = it) } ?: ScryfallIdentifier(name = r.name.trim()) }
            // Stopped: what's left is looked up next time, from here.
            val response = withRetries(retries, sleep) { fetch(ids) } ?: return false
            val byId = HashMap<String, ScryfallCard>()
            val byName = HashMap<String, ScryfallCard>()
            for (card in response.data) {
                byId[card.id] = card
                byName.putIfAbsent(key(card.name), card)
                byName.putIfAbsent(key(card.name.substringBefore(" // ")), card)
            }
            val at = now()
            val found = LinkedHashMap<String, TagRecord>()
            for (r in chunk) {
                val k = key(r.name)
                val card = if (r.scryfallId != null) byId[r.scryfallId] else byName[k]
                if (card == null && r.scryfallId != null) {
                    // Not a printing Scryfall knows (any more): try the name.
                    queue.addLast(r.copy(scryfallId = null))
                    continue
                }
                done++
                found[k] = card?.let { recordOf(it, tagSets, file, at) } ?: TagRecord(emptyList(), at, file = file)
                // Its own name too, for screens asking by it (a renamed card, a double-faced card's front).
                if (card != null) {
                    for (alias in listOf(key(card.name), key(card.name.substringBefore(" // ")))) found.putIfAbsent(alias, found.getValue(k))
                }
            }
            if (found.isNotEmpty()) onFound(found, done)
        }
        return true
    }

    /** [block]'s answer, trying again after each of [retries] when it fails; null if it never worked. */
    suspend fun <T : Any> withRetries(retries: List<Long>, sleep: suspend (Long) -> Unit, block: suspend () -> T): T? {
        for (attempt in 0..retries.size) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt < retries.size) sleep(retries[attempt])
            }
        }
        return null
    }

    /** What [card]'s lookup found, against [tagSets] from Tagger's file [file]. */
    fun recordOf(card: ScryfallCard, tagSets: Map<String, Set<String>>, file: String?, at: Long): TagRecord {
        val oracle = (card.oracleId ?: card.cardFaces?.firstNotNullOfOrNull { it.oracleId })?.take(ID_PREFIX)
        val typeLine = card.typeLine ?: card.cardFaces?.mapNotNull { it.typeLine }?.joinToString(" // ")
        val text = textTags(textOf(card), typeLine, card.producedMana)
        return TagRecord(combine(oracle, text, tagSets), at, card.colorIdentity.orEmpty().joinToString(""), oracle, text, file)
    }

    private fun textOf(card: ScryfallCard): String =
        (listOfNotNull(card.oracleText) + card.cardFaces.orEmpty().mapNotNull { it.oracleText }).joinToString("\n")

    /**
     * Tagger's tag lists: from the phone, or fetched again when a week old (or missing). A new file
     * re-tags every card already looked up (see [retagged]).
     */
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
                    setsFile = o.optString("file").ifEmpty { setsAt.toString() }
                }
            }
        }
        if (sets.isEmpty() || System.currentTimeMillis() - setsAt >= SETS_FRESH_MS) {
            val fresh = runCatching { downloadSets(folder) }.getOrNull()
            if (fresh != null) {
                sets = fresh.first
                setsAt = System.currentTimeMillis()
                setsFile = fresh.second
                runCatching {
                    val s = JSONObject()
                    fresh.first.forEach { (id, ids) -> s.put(id, JSONArray(ids.toList())) }
                    file.writeText(JSONObject().put("v", SETS_VERSION).put("at", setsAt).put("file", fresh.second).put("sets", s).toString())
                }
            }
        }
        // Cards tagged under an older file (or none) are worked out again from their Oracle ids.
        val current = setsFile
        if (sets.isNotEmpty() && current != null) {
            val again = synchronized(cards) { retagged(cards, sets, current) }
            if (again != null) {
                synchronized(cards) { cards.putAll(again) }
                saveCards()
                _version.value += 1
            }
        }
        // An old list is better than none while offline.
        sets.isNotEmpty()
    }

    /**
     * Fetches Tagger's oracle tags file (about 6 MB, from Scryfall's file host) and keeps, for each
     * of [TAGS], the cards tagged with it or with any tag under it — with the file's date, which
     * says which file a card was tagged from. Read twice from a temporary file, so only what's needed
     * is ever held in memory.
     */
    private fun downloadSets(folder: File): Pair<Map<String, Set<String>>, String> {
        val client = NetworkModule.noCacheOkHttpClient
        val meta = client.newCall(Request.Builder().url("https://api.scryfall.com/bulk-data/oracle-tags").build()).execute().use { r ->
            check(r.isSuccessful) { "bulk-data ${r.code}" }
            JSONObject(r.body!!.string())
        }
        val uri = meta.optString("jsonl_download_uri").ifEmpty { meta.getString("download_uri") }
        val version = meta.optString("updated_at").ifEmpty { uri }
        val gz = File(folder, "oracle-tags.jsonl.gz")
        try {
            client.newCall(Request.Builder().url(uri).build()).execute().use { r ->
                check(r.isSuccessful) { "oracle-tags ${r.code}" }
                gz.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
            }
            return parseTagFile { block ->
                GZIPInputStream(gz.inputStream()).bufferedReader().useLines { seq -> seq.filter { it.isNotBlank() }.forEach(block) }
            } to version
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
        val json = synchronized(cards) { writeCards(cards) }
        runCatching { File(folder, "role_tags.json").writeText(json) }
    }
}
