package com.mtgcompanion.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * League mode: points, the table and its tie-breaks, game nights, and a season ending. The web app
 * has the same checks — see MtgCompanionWeb/tests/decks/league.test.ts.
 */
class LeagueTest {

    private val utc = ZoneOffset.UTC
    private val priya = "11111111-1111-1111-1111-111111111111"
    private val sam = "22222222-2222-2222-2222-222222222222"

    /** October [day] 2026 at [hour] UTC. */
    private fun at(day: Int, hour: Int = 20): Long = LocalDate.of(2026, 10, day).atStartOfDay(utc).toInstant().toEpochMilli() + hour * 3_600_000L

    private fun seat(name: String, result: String, userId: String? = null, deck: String? = null, place: Int? = null, firstBlood: Boolean = false) =
        PodPlayer(userId, name, null, deck, result, place, firstBlood)

    private var n = 0
    private fun game(playedAt: Long, vararg players: PodPlayer): PodGame {
        n++
        return PodGame("g$n", "c$n", priya, playedAt, "COMMANDER", null, null, players.toList())
    }

    private fun season(
        startsOn: String = "2026-10-01",
        endsOn: String? = null,
        maxNights: Int? = null,
        rules: LeagueRules = LeagueRules(),
        endedAt: Long? = null
    ) = Season("s1", "pod", "Season 1", startsOn, endsOn, maxNights, rules, priya, 0L, endedAt, null, null)

    @Test
    fun pointsForAWinSecondPlaceFirstBloodAndADraw() {
        val r = LeagueRules()
        assertEquals(3, gamePoints(seat("A", "WIN"), r))
        assertEquals(0, gamePoints(seat("A", "LOSS"), r))
        assertEquals(1, gamePoints(seat("A", "LOSS", place = 2), r))
        assertEquals(2, gamePoints(seat("A", "LOSS", place = 2, firstBlood = true), r))
        assertEquals(1, gamePoints(seat("A", "DRAW"), r))
        // Second place is for the loser who came second; the winner's place 1 adds nothing.
        assertEquals(3, gamePoints(seat("A", "WIN", place = 1), r))
        val everyone = LEAGUE_PRESETS.first { it.id == PRESET_EVERYONE }.rules
        assertEquals(5, gamePoints(seat("A", "WIN"), everyone, newDeck = true))
        assertEquals(1, gamePoints(seat("A", "LOSS"), everyone))
        val wins = LEAGUE_PRESETS.first { it.id == PRESET_WINS }.rules
        assertEquals(1, gamePoints(seat("A", "WIN", firstBlood = true), wins))
        assertEquals(0, gamePoints(seat("A", "LOSS", place = 2, firstBlood = true), wins))
    }

    @Test
    fun theTableRanksByPointsThenWinsThenWinRate() {
        val games = listOf(
            game(at(2), seat("Priya", "WIN", priya), seat("Sam", "LOSS", sam, place = 2), seat("Dan", "LOSS")),
            game(at(2, 21), seat("Priya", "LOSS", priya), seat("Sam", "WIN", sam), seat("Dan", "LOSS", place = 2, firstBlood = true)),
            game(at(3), seat("Priya", "WIN", priya), seat("Dan", "LOSS"))
        )
        val t = seasonTable(season(), games, utc)
        // Priya 6 (2 wins), Sam 4, Dan 2.
        assertEquals(listOf("Priya" to 6, "Sam" to 4, "Dan" to 2), t.standings.map { it.name to it.points })
        assertEquals(listOf(1, 2, 3), t.standings.map { it.rank })
        val p = t.standings[0]
        assertEquals(listOf(3, 2, 1, 0, 66), listOf(p.games, p.wins, p.losses, p.draws, p.winRate))
        assertEquals("W1", p.streak)
        assertEquals("W1", t.standings[1].streak)
        assertEquals("L3", t.standings[2].streak)
        assertEquals(1, t.standings[2].firstBloods)
        assertEquals(1, t.standings[1].seconds)
        assertEquals(listOf(p), t.champions)
    }

    @Test
    fun tiesBreakOnWinsThenWinRateAndAFullTieSharesTheTitle() {
        val rules = LeagueRules(PRESET_CUSTOM, win = 2, second = 2, draw = 0, played = 0, firstBlood = 0, newDeckWin = 0)
        val level = seasonTable(
            season(rules = rules),
            listOf(
                game(at(2), seat("Ann", "WIN"), seat("Ben", "LOSS", place = 2)),
                game(at(3), seat("Ben", "LOSS", place = 2), seat("Cat", "WIN"), seat("Ann", "LOSS"))
            ),
            utc
        )
        // Ann 2 (1 win in 2), Cat 2 (1 win in 1), Ben 4 (no wins).
        assertEquals(listOf("Ben", "Cat", "Ann"), level.standings.map { it.name })
        // Cat and Ann: same points and wins, Cat's better win rate first.
        assertEquals(listOf(1, 2, 3), level.standings.map { it.rank })

        val tied = seasonTable(
            season(),
            listOf(
                game(at(2), seat("Zed", "WIN"), seat("Amy", "LOSS")),
                game(at(3), seat("Amy", "WIN"), seat("Zed", "LOSS"))
            ),
            utc
        )
        assertEquals(listOf("Amy", "Zed"), tied.standings.map { it.name })
        assertEquals(listOf(1, 1), tied.standings.map { it.rank })
        assertEquals("Amy & Zed", championNames(tied.champions))
        assertEquals("Season 1 champions: Amy & Zed", championLine("Season 1", championNames(tied.champions)))
        assertEquals("Season 1 champion: Priya", championLine("Season 1", "Priya"))
        assertNull(championLine("Season 1", null))
    }

    @Test
    fun aSharedRankSkipsTheNextPlaces() {
        val t = seasonTable(
            season(),
            listOf(
                game(at(2), seat("A", "WIN"), seat("B", "LOSS"), seat("C", "LOSS")),
                game(at(3), seat("B", "WIN"), seat("A", "LOSS"), seat("C", "LOSS"))
            ),
            utc
        )
        assertEquals(listOf(1, 1, 3), t.standings.map { it.rank })
    }

    @Test
    fun onlyTheSeasonsDaysCount() {
        val games = listOf(
            game(at(1, 10), seat("A", "WIN"), seat("B", "LOSS")),
            game(at(5), seat("A", "WIN"), seat("B", "LOSS")),
            game(at(10), seat("B", "WIN"), seat("A", "LOSS")),
            game(at(12), seat("B", "WIN"), seat("A", "LOSS"))
        )
        assertEquals(4, seasonGames(season(), games, utc).size)
        assertEquals(listOf("g2", "g3"), seasonGames(season(startsOn = "2026-10-02", endsOn = "2026-10-10"), games, utc).map { it.id })
        // Ended: games after it don't count.
        assertEquals(2, seasonGames(season(endedAt = at(5, 23)), games, utc).size)
        // Until 3 game nights: the first three nights only.
        val three = seasonTable(season(maxNights = 3), games, utc)
        assertEquals(listOf("2026-10-01", "2026-10-05", "2026-10-10"), three.nights)
        assertEquals(3, three.games.size)
    }

    @Test
    fun aSeasonIsUpcomingRunningOverOrEnded() {
        val games = listOf(
            game(at(2), seat("A", "WIN"), seat("B", "LOSS")),
            game(at(4), seat("A", "WIN"), seat("B", "LOSS"))
        )
        assertEquals(SeasonStatus.UPCOMING, seasonStatus(season(startsOn = "2026-10-08"), games, "2026-10-07", utc))
        assertEquals(SeasonStatus.RUNNING, seasonStatus(season(endsOn = "2026-10-31"), games, "2026-10-31", utc))
        assertEquals(SeasonStatus.OVER, seasonStatus(season(endsOn = "2026-10-31"), games, "2026-11-01", utc))
        // Two game nights of two: still running on the second night, over the day after.
        assertEquals(SeasonStatus.RUNNING, seasonStatus(season(maxNights = 2), games, "2026-10-04", utc))
        assertEquals(SeasonStatus.OVER, seasonStatus(season(maxNights = 2), games, "2026-10-05", utc))
        assertEquals(SeasonStatus.RUNNING, seasonStatus(season(maxNights = 3), games, "2026-10-20", utc))
        assertEquals(SeasonStatus.ENDED, seasonStatus(season(endedAt = at(5)), games, "2026-10-05", utc))
        assertEquals(SeasonStatus.RUNNING, seasonStatus(season(), games, "2027-01-01", utc))
    }

    @Test
    fun anOverSeasonEndsAtTheEndOfItsLastDay() {
        val games = listOf(game(at(2), seat("A", "WIN"), seat("B", "LOSS")), game(at(4), seat("A", "WIN"), seat("B", "LOSS")))
        val byDate = season(endsOn = "2026-10-31")
        val endOfOct31 = LocalDate.of(2026, 11, 1).atStartOfDay(utc).toInstant().toEpochMilli() - 1
        assertEquals(endOfOct31, seasonEndedAt(byDate, seasonTable(byDate, games, utc), at(20, 0) + 40L * 86_400_000L, utc))
        val byNights = season(maxNights = 2)
        val endOfOct4 = LocalDate.of(2026, 10, 5).atStartOfDay(utc).toInstant().toEpochMilli() - 1
        assertEquals(endOfOct4, seasonEndedAt(byNights, seasonTable(byNights, games, utc), at(9), utc))
        // Never later than now.
        assertEquals(at(3), seasonEndedAt(byNights, seasonTable(byNights, games, utc), at(3), utc))
    }

    @Test
    fun streaksCountTheNewestRun() {
        assertEquals("", streakOf(emptyList()))
        assertEquals("W3", streakOf(listOf("LOSS", "WIN", "WIN", "WIN")))
        assertEquals("L2", streakOf(listOf("WIN", "LOSS", "LOSS")))
        assertEquals("D1", streakOf(listOf("WIN", "DRAW")))
    }

    @Test
    fun aWinWithANewDeckEarnsItsBonus() {
        val rules = LeagueRules(PRESET_CUSTOM, win = 3, second = 0, draw = 0, played = 0, firstBlood = 0, newDeckWin = 2)
        val games = listOf(
            // Before the season: Priya played Krenko.
            game(LocalDate.of(2026, 9, 20).atStartOfDay(utc).toInstant().toEpochMilli(), seat("Priya", "LOSS", priya, deck = "Krenko"), seat("Sam", "WIN", sam, deck = "Edgar")),
            game(at(2), seat("Priya", "WIN", priya, deck = "krenko "), seat("Sam", "LOSS", sam, deck = "Edgar")),
            game(at(3), seat("Priya", "WIN", priya, deck = "Atraxa"), seat("Sam", "LOSS", sam, deck = "Edgar")),
            // No deck recorded: no bonus.
            game(at(4), seat("Sam", "WIN", sam), seat("Priya", "LOSS", priya, deck = "Atraxa"))
        )
        val t = seasonTable(season(rules = rules), games, utc)
        val p = t.standings.first { it.userId == priya }
        assertEquals(1, p.newDeckWins)
        assertEquals(8, p.points)
        assertEquals(3, t.standings.first { it.userId == sam }.points)
    }

    @Test
    fun pointsPerGameNightNewestFirst() {
        val games = listOf(
            game(at(2), seat("A", "WIN"), seat("B", "LOSS", place = 2)),
            game(at(2, 22), seat("B", "WIN"), seat("A", "LOSS")),
            game(at(9), seat("A", "WIN"), seat("C", "LOSS"))
        )
        val t = seasonTable(season(), games, utc)
        assertEquals(listOf("2026-10-09", "2026-10-02"), t.perNight.map { it.night })
        assertEquals(listOf(1, 2), t.perNight.map { it.games })
        assertEquals(listOf("B" to 4, "A" to 3), t.perNight[1].scores.map { it.name to it.points })
    }

    @Test
    fun aMemberIsOnePlayerUnderTheirNewestNameAndAGuestByName() {
        val t = seasonTable(
            season(),
            listOf(
                game(at(2), seat("Pri", "WIN", priya), seat("carol", "LOSS")),
                game(at(3), seat("Priya", "WIN", priya), seat(" Carol ", "LOSS"))
            ),
            utc
        )
        assertEquals(listOf("Priya" to 2, "Carol" to 2), t.standings.map { it.name to it.games })
    }

    @Test
    fun aSeasonWithNoGamesHasNoChampion() {
        val t = seasonTable(season(), emptyList(), utc)
        assertEquals(emptyList<LeagueStanding>(), t.champions)
        assertNull(championNames(t.champions))
    }

    @Test
    fun rulesInWordsAndPresets() {
        assertEquals("Win 3 · Second 1 · Draw 1 · First blood +1", rulesSummary(LEAGUE_PRESETS[0].rules))
        assertEquals("Win 1", rulesSummary(LEAGUE_PRESETS[1].rules))
        assertEquals(
            "Win 3 · Second 1 · Draw 1 · Playing 1 · First blood +1 · Win with a new deck +1",
            rulesSummary(LEAGUE_PRESETS[2].rules)
        )
    }

    @Test
    fun checksBeforeSaving() {
        assertEquals("Give the season a name.", seasonProblem(" ", "2026-10-01", null, null))
        assertEquals("It has to end after it starts.", seasonProblem("S", "2026-10-05", "2026-10-01", null))
        assertEquals("A season runs for 1 to 100 game nights.", seasonProblem("S", "2026-10-05", null, 0))
        assertEquals("Pick the day it starts.", seasonProblem("S", "", null, null))
        assertNull(seasonProblem("Season 1", "2026-10-01", "2026-10-01", null))
        assertEquals("1 Oct – 31 Dec", seasonDays(season(endsOn = "2026-12-31")))
        assertEquals("From 1 Oct, for 8 game nights", seasonDays(season(maxNights = 8)))
        assertEquals("From 1 Oct", seasonDays(season()))
        assertEquals("Season 3", nextSeasonName(listOf(season(), season())))
    }

    @Test
    fun theLeagueForAGameNightIsThePodWithMostOfTonightsPlayers() {
        val pods = listOf("a" to listOf(priya), "b" to listOf(priya, sam))
        assertEquals("b", leaguePodFor(pods, setOf(priya, sam)))
        assertEquals("a", leaguePodFor(pods, setOf(priya)))
        assertNull(leaguePodFor(emptyList(), setOf(priya)))
    }

    @Test
    fun seasonsAndTheirFinalTablesReadBackAsSent() {
        val t = seasonTable(season(), listOf(game(at(2), seat("Priya", "WIN", priya), seat("Dan", "LOSS"))), utc)
        val json = JSONArray().put(
            JSONObject().put("id", "s9").put("podId", "pod").put("name", "Season 2").put("startsOn", "2026-10-01")
                .put("endsOn", JSONObject.NULL).put("maxNights", 6).put("rules", rulesJson(LEAGUE_PRESETS[2].rules))
                .put("createdBy", priya).put("createdAt", 5L).put("endedAt", 99L).put("champion", "Priya")
                .put("standings", standingsJson(t.standings))
        )
        val s = parseSeasons(json.toString()).single()
        assertEquals(LEAGUE_PRESETS[2].rules, s.rules)
        assertEquals(6, s.maxNights)
        assertNull(s.endsOn)
        assertEquals(99L, s.endedAt)
        assertEquals(t.standings.map { it.copy(firstBloods = 0, seconds = 0, newDeckWins = 0) }, s.standings)
        assertEquals(emptyList<Season>(), parseSeasons("[]"))
        // Rules missing a value take Standard's; out-of-range ones are held to 0–10.
        assertEquals(LeagueRules(PRESET_CUSTOM, win = 10), parseRules(JSONObject().put("win", 50)))
    }

    @Test
    fun aSeasonIsChangedByWhoeverStartedItOrThePodsOwner() {
        assertEquals(true, canManageSeason(season(), priya, sam))
        assertEquals(true, canManageSeason(season(), sam, sam))
        assertEquals(false, canManageSeason(season(), sam, priya.reversed()))
    }

    @Test
    fun placeAndFirstBloodTravelWithAPodGame() {
        val sent = podPlayersJson(listOf(seat("A", "WIN", place = 1), seat("B", "LOSS", place = 2, firstBlood = true), seat("C", "LOSS")))
        assertEquals(1, sent.getJSONObject(0).getInt("place"))
        assertEquals(true, sent.getJSONObject(1).getBoolean("firstBlood"))
        assertEquals(false, sent.getJSONObject(2).has("place"))
        assertEquals(false, sent.getJSONObject(2).has("firstBlood"))
        val back = parsePodGames(JSONArray().put(JSONObject().put("id", "g").put("players", sent)).toString()).single().players
        assertEquals(listOf(1, 2, null), back.map { it.place })
        assertEquals(listOf(false, true, false), back.map { it.firstBlood })
        assertEquals("Only one player can draw first blood.", podGameProblem(listOf(seat("A", "WIN", firstBlood = true), seat("B", "LOSS", firstBlood = true))))
    }
}
