package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionType
import com.mtgcompanion.app.data.PlaceKind
import com.mtgcompanion.app.data.ServerCard
import com.mtgcompanion.app.data.placedCopies
import com.mtgcompanion.app.data.sameCardName
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlin.random.Random

/*
 * Sharing storage at home: the rules behind the Household screen and a pull list's "ask Alex" cards.
 * A household is 2–6 friends who keep cards in the same storage places; each person's cards stay in
 * their own library, and the household only sees what each of them keeps in the places shared in it
 * (supabase/migrations/20261006050000_households.sql). Nothing here changes anyone else's library.
 *
 * Pure, so it can be tested. Mirrors the web app's src/social/householdLogic.ts rule for rule, with
 * the same tests (HouseholdTest.kt ↔ tests/social/household.test.ts).
 */

const val HOUSEHOLD_UNAVAILABLE = "Household sharing isn't available yet"
const val DEFAULT_HOUSEHOLD_NAME = "Shared shelf"

/** [status] "invited" until they say yes, then "member". */
data class HouseholdMember(val profile: Profile, val status: String) {
    val isMember: Boolean get() = status == "member"
}

/** A place shared in a household: [sharedBy] made it; [users] are the people whose copies there can be seen. */
data class HouseholdPlace(
    val placeId: String,
    val name: String,
    val kind: String,
    val sharedBy: String,
    val users: List<String>
)

data class Household(
    val id: String,
    val name: String,
    val createdAt: Long = 0L,
    val members: List<HouseholdMember> = emptyList(),
    val places: List<HouseholdPlace> = emptyList()
)

data class HouseholdInvite(val id: String, val name: String, val invitedBy: Profile?, val members: Int, val at: Long)

data class MyHouseholds(val households: List<Household> = emptyList(), val invites: List<HouseholdInvite> = emptyList())

/** Copies of one printing someone keeps in a shared place. */
data class ShelfCopy(
    val userId: String,
    val placeId: String,
    val scryfallId: String,
    val name: String,
    val imageUrl: String?,
    val qty: Int,
    val foil: Boolean = false
)

/** Cards borrowed from the shelf and not back yet: an ordinary loan (lender → borrower). */
data class ShelfLoan(
    val id: String,
    val clientId: String,
    val lender: String,
    val borrower: String,
    val cards: List<ServerCard>,
    val note: String? = null,
    val lentAt: Long = 0L
)

data class HouseholdCards(val copies: List<ShelfCopy> = emptyList(), val loans: List<ShelfLoan> = emptyList())

private fun count(n: Int): String = NumberFormat.getIntegerInstance(Locale.UK).format(n)

/** The people in it (not the ones only invited), the user first. */
fun membersOf(h: Household, me: String): List<HouseholdMember> {
    val inIt = h.members.filter { it.isMember }
    return inIt.filter { it.profile.userId == me } + inIt.filter { it.profile.userId != me }
}

/** The people invited who haven't said yet. */
fun invitedOf(h: Household): List<HouseholdMember> = h.members.filter { !it.isMember }

/** A person's name as the screen says it: "You" for the user, else their display name. */
fun personName(h: Household, userId: String, me: String): String =
    if (userId == me) "You" else h.members.firstOrNull { it.profile.userId == userId }?.profile?.displayName ?: "Someone"

private fun others(h: Household, me: String) = membersOf(h, me).filter { it.profile.userId != me }.map { it.profile.displayName }

/** "Alex", "Alex and Sam", "Alex, Sam and Jo". */
fun andList(names: List<String>): String =
    if (names.size <= 1) names.firstOrNull().orEmpty() else names.dropLast(1).joinToString(", ") + " and " + names.last()

/** "You and Alex keep cards on the same shelf. Each of you still owns your own cards." */
fun householdIntro(h: Household, me: String): String {
    val o = others(h, me)
    if (o.isEmpty()) return "Invite someone you live with to keep cards on the same shelf. Each of you still owns your own cards."
    return "${andList(listOf("You") + o)} keep cards on the same shelf. Each of you still owns your own cards."
}

/** "Pull lists can include Alex's cards, marked "ask Alex", and the borrowed cards show under Loans." */
fun deckNote(h: Household, me: String): String {
    val o = others(h, me)
    if (o.isEmpty()) return "Pull lists can include the cards of the people you share with, marked \"ask\", and the borrowed cards show under Loans."
    return "Pull lists can include ${andList(o.map { "$it's" })} cards, marked ${o.joinToString(" or ") { "\"ask $it\"" }}, and the borrowed cards show under Loans."
}

/** The user's own copies in [placeIds], from their library on this phone (newer than the last sync). Wishlists aren't counted. */
fun myShelfCopies(collections: List<Collection>, placeIds: Iterable<String>, me: String): List<ShelfCopy> {
    val ids = placeIds.toSet()
    val byKey = LinkedHashMap<String, ShelfCopy>()
    for (c in collections) {
        if (c.kind == CollectionType.WISHLIST) continue
        for (e in c.entries) {
            for (line in placedCopies(e)) {
                if (line.placeId !in ids || line.qty <= 0) continue
                val key = "${line.placeId}|${e.scryfallId}|${line.isFoil}"
                val had = byKey[key]
                byKey[key] = had?.copy(qty = had.qty + line.qty) ?: ShelfCopy(me, line.placeId, e.scryfallId, e.name, e.imageUrl, line.qty, line.isFoil)
            }
        }
    }
    return byKey.values.toList()
}

/** One person's tile: how many copies they keep in the shared places, and what they're worth (null: no prices). */
data class PersonTotal(val userId: String, val label: String, val copies: Int, val usd: Double?)

/**
 * Each person in the household, the user first, with their copies in the shared places. [copies] is
 * everyone's (the user's from myShelfCopies, the others' from the server); [price] is one copy's
 * price in US dollars, null when there's none — [PersonTotal.usd] stays null until any price is known.
 */
fun peopleTotals(h: Household, me: String, copies: List<ShelfCopy>, price: ((ShelfCopy) -> Double?)? = null): List<PersonTotal> {
    val shared = h.places.map { it.placeId }.toSet()
    return membersOf(h, me).map { m ->
        val id = m.profile.userId
        val mine = copies.filter { it.userId == id && it.placeId in shared }
        var usd: Double? = null
        if (price != null) {
            for (c in mine) {
                val p = price(c) ?: continue
                usd = (usd ?: 0.0) + p * c.qty
            }
        }
        PersonTotal(id, personName(h, id, me), mine.sumOf { it.qty }, usd)
    }
}

/** A shared place as the screen lists it. */
data class PlaceLine(
    val place: HouseholdPlace,
    /** "Yours 612 · Alex 0", or "Alex 342 · you can see, not change". */
    val line: String,
    /** The user keeps no cards there: they can see it, not change it. */
    val readOnly: Boolean,
    /** The user shared it. */
    val mine: Boolean,
    /** The user may keep their cards there too (not in it yet, and not a binder — a binder's pockets are its owner's). */
    val canJoin: Boolean
)

/**
 * The shared places, each with how many copies each person keeps there. A place the user keeps
 * cards in counts everyone in the household ("Yours 210 · Alex 188"); one they don't is someone
 * else's, which they can look at but not change ("Alex 342 · you can see, not change").
 */
fun placeLines(h: Household, me: String, copies: List<ShelfCopy>): List<PlaceLine> {
    val people = membersOf(h, me).map { it.profile.userId }
    fun n(placeId: String, userId: String) = copies.filter { it.placeId == placeId && it.userId == userId }.sumOf { it.qty }
    return h.places.map { p ->
        if (me in p.users) {
            val parts = people.map { id -> "${if (id == me) "Yours" else personName(h, id, me)} ${count(n(p.placeId, id))}" }
            PlaceLine(p, parts.joinToString(" · "), readOnly = false, mine = p.sharedBy == me, canJoin = false)
        } else {
            val parts = people.filter { it in p.users }.map { id -> "${personName(h, id, me)} ${count(n(p.placeId, id))}" }
            PlaceLine(p, (parts + "you can see, not change").joinToString(" · "), readOnly = true, mine = false, canJoin = p.kind != PlaceKind.BINDER.name)
        }
    }
}

data class PersonCopies(val userId: String, val name: String, val copies: List<ShelfCopy>)

/** What's in one shared place, person by person (the user first), each person's cards A–Z. */
fun placeContents(h: Household, me: String, placeId: String, copies: List<ShelfCopy>): List<PersonCopies> =
    membersOf(h, me).map { it.profile.userId }.map { id ->
        PersonCopies(
            id, personName(h, id, me),
            copies.filter { it.placeId == placeId && it.userId == id }.sortedWith(compareBy<ShelfCopy, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.foil })
        )
    }.filter { it.copies.isNotEmpty() }

/** The household a place of the user's is shared in, if any. */
fun householdOfPlace(households: List<Household>, placeId: String): Household? =
    households.firstOrNull { h -> h.places.any { it.placeId == placeId } }

/** Cards borrowed from the shelf, as the screen says them: "Sam has 2 of your cards", "You have 1 card of Alex's". */
fun shelfLoanLine(h: Household, me: String, loan: ShelfLoan): String {
    val n = loan.cards.sumOf { it.qty }
    val cards = "$n ${if (n == 1) "card" else "cards"}"
    return when {
        loan.lender == me -> "${personName(h, loan.borrower, me)} has $n of your cards"
        loan.borrower == me -> "You have $cards of ${personName(h, loan.lender, me)}'s"
        else -> "${personName(h, loan.borrower, me)} has $cards of ${personName(h, loan.lender, me)}'s"
    }
}

// ---- A deck's pull list: the cards it's short of, from the people at home ----

/** A card a pull list is short of (its "Not owned" rows). */
data class PullShort(val name: String, val scryfallId: String, val qty: Int)

/** Someone's copies a deck could use: "ask Alex", or already "borrowed from Alex". */
data class AskRow(
    val key: String,
    val name: String,
    /** The printing the owner has. */
    val printingId: String,
    val qty: Int,
    val householdId: String,
    /** Whose they are. */
    val from: String,
    val fromName: String,
    /** Where they keep them: "Blue box". */
    val placeName: String,
    /** Recorded as borrowed already (a loan from them). */
    val borrowed: Boolean,
    val hint: String
)

data class AskGroup(val householdId: String, val userId: String, val name: String, val title: String, val rows: List<AskRow>)

data class PullAsks(val groups: List<AskGroup>, val stillMissing: List<PullShort>)

/** One household and what's on its shelves, as the server answered. */
data class Shelf(val household: Household, val cards: HouseholdCards)

private class Pool(
    val householdId: String, val from: String, val name: String, val printingId: String, val placeName: String,
    var left: Int, val borrowed: Boolean, val order: Int
)

private fun nameKey(name: String) = name.trim().lowercase()

/**
 * The cards a deck is short of ([missing], in its order), covered by the people at home: first the
 * ones the user has borrowed from them already (open loans from the shelf), then their copies in the
 * shared places that nobody has borrowed — "ask Alex". What's left is still to buy. Without any
 * household data (none, or the server doesn't have households yet) everything stays missing.
 */
fun pullAsks(missing: List<PullShort>, shelves: List<Shelf>?, me: String): PullAsks {
    if (shelves.isNullOrEmpty()) return PullAsks(emptyList(), missing.filter { it.qty > 0 })
    val borrowed = mutableListOf<Pool>()
    val free = mutableListOf<Pool>()
    shelves.forEachIndexed { hi, shelf ->
        val h = shelf.household
        val people = membersOf(h, me).map { it.profile.userId }
        fun placeName(id: String) = h.places.firstOrNull { it.placeId == id }?.name ?: "the shelf"
        for (l in shelf.cards.loans) {
            if (l.borrower != me || l.lender == me) continue
            for (c in l.cards) {
                if (c.qty > 0) borrowed += Pool(h.id, l.lender, c.name, c.printingId, "borrowed", c.qty, true, hi * 100 + people.indexOf(l.lender).coerceAtLeast(0))
            }
        }
        // Copies out on a loan from the shelf (to anyone) aren't there to ask for.
        val out = HashMap<String, Int>()
        for (l in shelf.cards.loans) for (c in l.cards) out["${l.lender}|${nameKey(c.name)}"] = (out["${l.lender}|${nameKey(c.name)}"] ?: 0) + c.qty
        val shared = h.places.map { it.placeId }.toSet()
        for (c in shelf.cards.copies) {
            if (c.userId == me || c.userId !in people || c.placeId !in shared || c.qty <= 0) continue
            val k = "${c.userId}|${nameKey(c.name)}"
            val gone = minOf(out[k] ?: 0, c.qty)
            if (gone > 0) out[k] = (out[k] ?: 0) - gone
            if (c.qty - gone > 0) free += Pool(h.id, c.userId, c.name, c.scryfallId, placeName(c.placeId), c.qty - gone, false, hi * 100 + people.indexOf(c.userId))
        }
    }
    val pools = borrowed + free.sortedBy { it.order }
    val names = HashMap<String, String>()
    for (s in shelves) for (m in s.household.members) names[m.profile.userId] = m.profile.displayName
    val rows = mutableListOf<AskRow>()
    val stillMissing = mutableListOf<PullShort>()
    for (need in missing) {
        var wanted = need.qty
        for (p in pools) {
            if (wanted <= 0) break
            if (p.left <= 0 || !sameCardName(p.name, need.name)) continue
            val take = minOf(p.left, wanted)
            p.left -= take
            wanted -= take
            val fromName = names[p.from] ?: "someone"
            val key = "${nameKey(need.name)}|${p.householdId}|${p.from}|${if (p.borrowed) "b" else "a"}"
            val at = rows.indexOfFirst { it.key == key }
            if (at >= 0) { rows[at] = rows[at].copy(qty = rows[at].qty + take); continue }
            rows += AskRow(
                key, need.name, p.printingId.ifEmpty { need.scryfallId }, take, p.householdId, p.from, fromName, p.placeName, p.borrowed,
                if (p.borrowed) "borrowed from $fromName" else "ask $fromName · ${p.placeName}"
            )
        }
        if (wanted > 0) stillMissing += need.copy(qty = wanted)
    }
    val groups = LinkedHashMap<String, MutableList<AskRow>>()
    for (r in rows) groups.getOrPut("${r.householdId}|${r.from}") { mutableListOf() } += r
    return PullAsks(
        groups.values.map { g ->
            val first = g.first()
            AskGroup(
                first.householdId, first.from, first.fromName, "Ask ${first.fromName}",
                g.sortedWith(compareBy<AskRow> { it.borrowed }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            )
        },
        stillMissing
    )
}

/** The cards to record as borrowed from one person: the group's rows not borrowed yet. */
fun borrowCards(g: AskGroup): List<ServerCard> = g.rows.filter { !it.borrowed }.map { ServerCard(it.name, it.qty, it.printingId) }

/** "hh-" and a random id: the loan's id, so a retry stores it once. */
fun shelfLoanId(random: Random = Random.Default): String {
    val abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return "hh-" + (1..20).map { abc[random.nextInt(abc.length)] }.joinToString("")
}

/** The words for a server answer, for the household's screens. */
val HOUSEHOLD_ERRORS = mapOf(
    "not_in_household" to "You're not sharing that shelf any more.",
    "not_invited" to "That invitation has been taken back.",
    "household_full" to "A shared shelf is for up to 6 people.",
    "too_many_households" to "That's 5 shared shelves already.",
    "too_many_places" to "Up to 30 places can be shared.",
    "not_yours" to "Someone else shared that place.",
    "no_such_place" to "That place is not shared any more.",
    "not_on_shelf" to "Those cards aren't on the shelf any more.",
)

/** A server answer in words for the household's screens. */
fun householdError(e: Throwable): String =
    (e as? SocialException)?.let { HOUSEHOLD_ERRORS[it.code] ?: it.message } ?: e.message ?: "Something went wrong."

// ---- Parsing the server's answers ----

private fun JSONObject.str(name: String): String? = if (isNull(name) || !has(name)) null else optString(name)

private fun <T> JSONArray?.objects(transform: (JSONObject) -> T?): List<T> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it)?.let(transform) }

private fun profileOrNull(o: JSONObject?): Profile? = o?.let { runCatching { parseProfile(it) }.getOrNull() }

internal fun parseHousehold(o: JSONObject): Household = Household(
    id = o.getString("id"),
    name = o.optString("name"),
    createdAt = o.optLong("createdAt"),
    members = o.optJSONArray("members").objects { m -> profileOrNull(m.optJSONObject("profile"))?.let { HouseholdMember(it, m.optString("status", "member")) } },
    places = o.optJSONArray("places").objects { p ->
        val users = p.optJSONArray("users") ?: JSONArray()
        HouseholdPlace(
            placeId = p.getString("placeId"),
            name = p.optString("name"),
            kind = PlaceKind.fromName(p.str("kind")).name,
            sharedBy = p.optString("sharedBy"),
            users = (0 until users.length()).map { users.optString(it) }
        )
    }
)

/** my_households' answer. */
fun parseMyHouseholds(text: String): MyHouseholds {
    val o = text.trim().takeIf { it.startsWith("{") }?.let(::JSONObject) ?: return MyHouseholds()
    return MyHouseholds(
        households = o.optJSONArray("households").objects { runCatching { parseHousehold(it) }.getOrNull() },
        invites = o.optJSONArray("invites").objects { i ->
            HouseholdInvite(i.getString("id"), i.optString("name"), profileOrNull(i.optJSONObject("invitedBy")), i.optInt("members"), i.optLong("at"))
        }
    )
}

/** household_cards' answer. */
fun parseHouseholdCards(text: String): HouseholdCards {
    val o = text.trim().takeIf { it.startsWith("{") }?.let(::JSONObject) ?: return HouseholdCards()
    return HouseholdCards(
        copies = o.optJSONArray("copies").objects { c ->
            ShelfCopy(
                userId = c.optString("userId"), placeId = c.optString("placeId"), scryfallId = c.optString("scryfallId"),
                name = c.optString("name"), imageUrl = c.str("imageUrl"), qty = c.optInt("qty"), foil = c.optBoolean("foil")
            )
        },
        loans = o.optJSONArray("loans").objects { l ->
            ShelfLoan(
                id = l.getString("id"), clientId = l.optString("clientId"), lender = l.optString("lender"), borrower = l.optString("borrower"),
                cards = l.optJSONArray("cards").objects { c -> ServerCard(c.optString("name"), c.optInt("qty", 1), c.str("printingId").orEmpty()) },
                note = l.str("note"), lentAt = l.optLong("lentAt")
            )
        }
    )
}
