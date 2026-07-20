package com.mtgcompanion.app.data.rules

/** A Magic keyword or keyword action and its plain-language reminder text. */
data class Keyword(val name: String, val category: String, val text: String)

/**
 * A bundled glossary of the common gameplay keywords and actions, so keyword lookup works instantly
 * and offline. Not exhaustive of every set-specific mechanic — the most-referenced ones. Card-
 * specific interactions come from the Rulings tab (Scryfall) instead.
 */
object Keywords {

    val all: List<Keyword> = listOf(
        // Evergreen — appear in nearly every set.
        Keyword("Deathtouch", "Evergreen", "Any amount of damage this deals to a creature is enough to destroy it."),
        Keyword("Defender", "Evergreen", "This creature can't attack."),
        Keyword("Double strike", "Evergreen", "This creature deals both first-strike and regular combat damage."),
        Keyword("Enchant", "Evergreen", "This Aura can only be attached to the kind of object or player it specifies."),
        Keyword("Equip", "Evergreen", "Attach this Equipment to target creature you control. Equip only as a sorcery — during your main phase with an empty stack."),
        Keyword("First strike", "Evergreen", "This creature deals its combat damage before creatures without first strike."),
        Keyword("Flash", "Evergreen", "You may cast this spell any time you could cast an instant."),
        Keyword("Flying", "Evergreen", "This creature can't be blocked except by creatures with flying or reach."),
        Keyword("Haste", "Evergreen", "This creature can attack and use its tap abilities the turn it comes under your control."),
        Keyword("Hexproof", "Evergreen", "This permanent can't be the target of spells or abilities your opponents control."),
        Keyword("Indestructible", "Evergreen", "This permanent can't be destroyed by damage or by effects that say \"destroy\"."),
        Keyword("Lifelink", "Evergreen", "Damage dealt by this permanent also causes you to gain that much life."),
        Keyword("Menace", "Evergreen", "This creature can't be blocked except by two or more creatures."),
        Keyword("Protection", "Evergreen", "This can't be Damaged, Enchanted or Equipped, Blocked, or Targeted by anything with the stated quality (remember it as \"DEBT\")."),
        Keyword("Reach", "Evergreen", "This creature can block creatures with flying."),
        Keyword("Trample", "Evergreen", "If this creature would assign enough combat damage to destroy the creatures blocking it, you may assign the rest to the player or planeswalker it's attacking."),
        Keyword("Vigilance", "Evergreen", "Attacking doesn't cause this creature to tap."),
        Keyword("Ward", "Evergreen", "Whenever this becomes the target of a spell or ability an opponent controls, counter it unless that player pays the ward cost."),

        // Common non-evergreen keywords.
        Keyword("Prowess", "Keyword", "Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn."),
        Keyword("Shroud", "Keyword", "This permanent can't be the target of any spells or abilities."),
        Keyword("Fear", "Keyword", "This creature can't be blocked except by artifact creatures and/or black creatures."),
        Keyword("Intimidate", "Keyword", "This creature can't be blocked except by artifact creatures and/or creatures that share a color with it."),
        Keyword("Infect", "Keyword", "This deals damage to creatures as -1/-1 counters and to players as poison counters."),
        Keyword("Cascade", "Keyword", "When you cast this spell, exile cards from the top of your library until you exile a nonland card that costs less. You may cast that card without paying its mana cost."),
        Keyword("Convoke", "Keyword", "You may tap any number of untapped creatures as you cast this spell. Each pays for {1} or one mana of that creature's color."),
        Keyword("Cycling", "Keyword", "Pay the cycling cost and discard this card to draw a card."),
        Keyword("Flashback", "Keyword", "You may cast this card from your graveyard for its flashback cost, then exile it."),
        Keyword("Kicker", "Keyword", "You may pay an additional kicker cost as you cast this spell for an added effect."),
        Keyword("Morph", "Keyword", "You may cast this card face down as a 2/2 creature for {3}. Turn it face up any time by paying its morph cost."),
        Keyword("Persist", "Keyword", "When this creature dies, if it had no -1/-1 counters on it, return it to the battlefield with a -1/-1 counter."),
        Keyword("Undying", "Keyword", "When this creature dies, if it had no +1/+1 counters on it, return it to the battlefield with a +1/+1 counter."),
        Keyword("Storm", "Keyword", "When you cast this spell, copy it for each spell cast before it this turn. You may choose new targets for the copies."),
        Keyword("Suspend", "Keyword", "Exile this card with time counters, remove one at the start of each of your upkeeps, then cast it for free when the last is removed."),
        Keyword("Exalted", "Keyword", "Whenever a creature you control attacks alone, it gets +1/+1 until end of turn for each instance of exalted."),
        Keyword("Delve", "Keyword", "Each card you exile from your graveyard as you cast this spell pays for {1}."),
        Keyword("Dredge", "Keyword", "If you would draw a card, you may instead mill that many cards and return this card from your graveyard to your hand."),
        Keyword("Extort", "Keyword", "Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life."),
        Keyword("Ninjutsu", "Keyword", "Return an unblocked attacker you control to hand, then put this card onto the battlefield tapped and attacking."),
        Keyword("Escape", "Keyword", "You may cast this card from your graveyard for its escape cost, which includes exiling cards from your graveyard."),
        Keyword("Foretell", "Keyword", "During your turn, pay {2} to exile this card face down. Cast it later for its foretell cost."),
        Keyword("Blitz", "Keyword", "Cast this creature for its blitz cost. It gains haste and \"When this dies, draw a card,\" and is sacrificed at the next end step."),
        Keyword("Casualty", "Keyword", "As you cast this spell, you may sacrifice a creature with the stated power or greater to copy the spell."),
        Keyword("Adventure", "Keyword", "You may cast the adventure (an instant or sorcery) first, then exile the card to cast the creature later."),
        Keyword("Riot", "Keyword", "This creature enters the battlefield with your choice of a +1/+1 counter or haste."),
        Keyword("Rebound", "Keyword", "If you cast this from your hand, exile it as it resolves. On your next upkeep you may cast it from exile without paying its cost."),
        Keyword("Phasing", "Keyword", "A phased-out permanent is treated as though it doesn't exist. It phases in during its controller's untap step."),

        // Keyword actions.
        Keyword("Scry", "Action", "Look at the top N cards of your library, then put any number of them on the bottom and the rest back on top in any order."),
        Keyword("Surveil", "Action", "Look at the top N cards of your library, then put any number of them into your graveyard and the rest back on top in any order."),
        Keyword("Fight", "Action", "Two creatures each deal damage equal to their power to the other."),
        Keyword("Mill", "Action", "Put the top N cards of a library into its owner's graveyard."),
        Keyword("Proliferate", "Action", "For each permanent and player that has a counter on it, you may give it another counter of a kind already there."),
        Keyword("Populate", "Action", "Create a token that's a copy of a creature token you control."),
        Keyword("Explore", "Action", "Reveal the top card of your library. If it's a land, put it into your hand; otherwise put a +1/+1 counter on this creature and choose whether to keep the card on top or in your graveyard."),
        Keyword("Connive", "Action", "Draw N cards, then discard N cards. Put a +1/+1 counter on this creature for each nonland card discarded this way."),
        Keyword("Regenerate", "Action", "The next time this permanent would be destroyed this turn, instead tap it, remove it from combat, and remove all damage from it."),
        Keyword("Amass", "Action", "Put N +1/+1 counters on an Army you control, or create a 0/0 black Army creature token first if you have none.")
    ).sortedBy { it.name }

    /** Match against name first, then reminder text (so \"poison\" finds Infect). Blank query returns all. */
    fun search(query: String): List<Keyword> {
        val q = query.trim()
        if (q.isBlank()) return all
        val byName = all.filter { it.name.contains(q, ignoreCase = true) }
        val byText = all.filter { it.text.contains(q, ignoreCase = true) && it !in byName }
        return byName + byText
    }
}
