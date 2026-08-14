@file:Suppress("DEPRECATION")

package me.mhfs.sktyper.elements

import ch.njol.skript.Skript
import ch.njol.skript.expressions.base.PropertyExpression
import ch.njol.skript.lang.Condition.ConditionType
import ch.njol.skript.lang.ExpressionType
import ch.njol.skript.util.Date
import com.typewritermc.core.entries.Entry
import com.typewritermc.core.entries.Page
import me.mhfs.sktyper.elements.conditions.CondCriteriaMet
import me.mhfs.sktyper.elements.conditions.CondEntryExists
import me.mhfs.sktyper.elements.conditions.CondInAudience
import me.mhfs.sktyper.elements.conditions.CondInCinematic
import me.mhfs.sktyper.elements.conditions.CondInDialogue
import me.mhfs.sktyper.elements.conditions.CondQuestStatus
import me.mhfs.sktyper.elements.conditions.CondQuestTracked
import me.mhfs.sktyper.elements.conditions.CondTypewriterLoaded
import me.mhfs.sktyper.elements.effects.EffAudience
import me.mhfs.sktyper.elements.effects.EffCreateCinematic
import me.mhfs.sktyper.elements.effects.EffCreateDefinition
import me.mhfs.sktyper.elements.effects.EffCreateEntityCinematic
import me.mhfs.sktyper.elements.effects.EffSetActivity
import me.mhfs.sktyper.elements.effects.EffSetDisplayName
import me.mhfs.sktyper.elements.effects.EffSetSkin
import me.mhfs.sktyper.elements.effects.EffTeleportEntity
import me.mhfs.sktyper.elements.effects.EffCreateEntity
import me.mhfs.sktyper.elements.effects.EffDeleteEntry
import me.mhfs.sktyper.elements.effects.EffDeletePage
import me.mhfs.sktyper.elements.effects.EffPublishPages
import me.mhfs.sktyper.elements.effects.EffEndInteraction
import me.mhfs.sktyper.elements.effects.EffNextDialogue
import me.mhfs.sktyper.elements.effects.EffRefreshFact
import me.mhfs.sktyper.elements.effects.EffStartCinematic
import me.mhfs.sktyper.elements.effects.EffStopCinematic
import me.mhfs.sktyper.elements.effects.EffTrackQuest
import me.mhfs.sktyper.elements.effects.EffTriggerEntry
import me.mhfs.sktyper.elements.expressions.ExprAllEntities
import me.mhfs.sktyper.elements.expressions.ExprAllEntries
import me.mhfs.sktyper.elements.expressions.ExprSpawn
import me.mhfs.sktyper.elements.expressions.ExprAllPages
import me.mhfs.sktyper.elements.expressions.ExprAudience
import me.mhfs.sktyper.elements.expressions.ExprCinematicFrame
import me.mhfs.sktyper.elements.expressions.ExprCurrentDialogue
import me.mhfs.sktyper.elements.expressions.ExprDialogueSpeakers
import me.mhfs.sktyper.elements.expressions.ExprDisplayName
import me.mhfs.sktyper.elements.expressions.ExprEntry
import me.mhfs.sktyper.elements.expressions.ExprEntryId
import me.mhfs.sktyper.elements.expressions.ExprEntryName
import me.mhfs.sktyper.elements.expressions.ExprEntryPage
import me.mhfs.sktyper.elements.expressions.ExprFact
import me.mhfs.sktyper.elements.expressions.ExprFactLastUpdate
import me.mhfs.sktyper.elements.expressions.ExprInteractedInstance
import me.mhfs.sktyper.elements.expressions.ExprPage
import me.mhfs.sktyper.elements.expressions.ExprPageEntries
import me.mhfs.sktyper.elements.expressions.ExprPageId
import me.mhfs.sktyper.elements.expressions.ExprPageName
import me.mhfs.sktyper.elements.expressions.ExprPagePriority
import me.mhfs.sktyper.elements.expressions.ExprPageType
import me.mhfs.sktyper.elements.expressions.ExprQuestDisplay
import me.mhfs.sktyper.elements.expressions.ExprQuestStatus
import me.mhfs.sktyper.elements.expressions.ExprQuests
import me.mhfs.sktyper.elements.expressions.ExprTrackedQuest
import me.mhfs.sktyper.elements.expressions.ExprTypewriterVersion
import org.bukkit.Location
import org.bukkit.entity.Player

/** Every pattern SkTyper adds to Skript, in one place. */
object Elements {

    private const val ENTRIES = "%typewriterentries/strings%"
    private const val ENTRY = "%typewriterentry/string%"
    private const val PLAYERS = "%players%"

    fun register() {
        registerExpressions()
        registerEffects()
        registerConditions()
    }

    private fun registerExpressions() {
        Skript.registerExpression(
            ExprEntry::class.java, Entry::class.java, ExpressionType.SIMPLE,
            "[the] typewriter entr(y|ies) [with] [(id|name)] %strings%",
        )
        Skript.registerExpression(
            ExprAllEntries::class.java, Entry::class.java, ExpressionType.SIMPLE,
            "[all [[of] the]] typewriter entries",
        )
        Skript.registerExpression(
            ExprPage::class.java, Page::class.java, ExpressionType.SIMPLE,
            "[the] typewriter page[s] [with] [(id|name)] %strings%",
        )
        Skript.registerExpression(
            ExprAllPages::class.java, Page::class.java, ExpressionType.SIMPLE,
            "[all [[of] the]] typewriter pages",
        )
        Skript.registerExpression(
            ExprTypewriterVersion::class.java, String::class.java, ExpressionType.SIMPLE,
            "[the] typewriter version",
        )
        // Pattern order matters: ExprAllEntities reads instances vs definitions off the match.
        Skript.registerExpression(
            ExprAllEntities::class.java, Entry::class.java, ExpressionType.SIMPLE,
            "[all [[of] the]] typewriter (entities|npcs)",
            "[all [[of] the]] typewriter definitions",
        )
        Skript.registerExpression(
            ExprSpawn::class.java, Location::class.java, ExpressionType.PROPERTY,
            "[the] typewriter spawn [(location|point)] of $ENTRIES",
        )

        PropertyExpression.register(
            ExprEntryId::class.java, String::class.java, "typewriter id", "typewriterentries",
        )
        PropertyExpression.register(
            ExprEntryName::class.java, String::class.java, "typewriter name", "typewriterentries",
        )
        PropertyExpression.register(
            ExprEntryPage::class.java, Page::class.java, "typewriter page", "typewriterentries",
        )
        PropertyExpression.register(
            ExprPageId::class.java, String::class.java, "typewriter id", "typewriterpages",
        )
        PropertyExpression.register(
            ExprPageName::class.java, String::class.java, "typewriter name", "typewriterpages",
        )
        PropertyExpression.register(
            ExprPageType::class.java, String::class.java, "typewriter type", "typewriterpages",
        )
        PropertyExpression.register(
            ExprPagePriority::class.java, Number::class.java, "typewriter priority", "typewriterpages",
        )

        Skript.registerExpression(
            ExprPageEntries::class.java, Entry::class.java, ExpressionType.PROPERTY,
            "[the] [typewriter] entries of %typewriterpages%",
            "%typewriterpages%'[s] [typewriter] entries",
        )

        Skript.registerExpression(
            ExprFact::class.java, Number::class.java, ExpressionType.COMBINED,
            "[the] typewriter fact[s] $ENTRIES (of|for) $PLAYERS",
        )
        Skript.registerExpression(
            ExprFactLastUpdate::class.java, Date::class.java, ExpressionType.COMBINED,
            "[the] typewriter fact last update [time] [of] $ENTRIES (of|for) $PLAYERS",
            "[the] last update [time] of [the] typewriter fact[s] $ENTRIES (of|for) $PLAYERS",
        )

        Skript.registerExpression(
            ExprCinematicFrame::class.java, Number::class.java, ExpressionType.PROPERTY,
            "[the] typewriter cinematic frame of $PLAYERS",
            "$PLAYERS'[s] typewriter cinematic frame",
        )
        Skript.registerExpression(
            ExprCurrentDialogue::class.java, Entry::class.java, ExpressionType.PROPERTY,
            "[the] current typewriter dialogue of $PLAYERS",
            "$PLAYERS'[s] current typewriter dialogue",
        )
        Skript.registerExpression(
            ExprDialogueSpeakers::class.java, Entry::class.java, ExpressionType.PROPERTY,
            "[the] typewriter dialogue speaker[s] of $PLAYERS",
            "$PLAYERS'[s] typewriter dialogue speaker[s]",
        )
        Skript.registerExpression(
            ExprAudience::class.java, Player::class.java, ExpressionType.PROPERTY,
            "[the] typewriter audience of $ENTRIES",
        )
        Skript.registerExpression(
            ExprDisplayName::class.java, String::class.java, ExpressionType.COMBINED,
            "[the] typewriter display name of $ENTRIES (of|for) $PLAYERS",
        )
        Skript.registerExpression(
            ExprInteractedInstance::class.java, Entry::class.java, ExpressionType.EVENT,
            "[the] interacted typewriter entity instance",
            "[the] typewriter entity instance",
        )

        Skript.registerExpression(
            ExprTrackedQuest::class.java, Entry::class.java, ExpressionType.PROPERTY,
            "[the] tracked typewriter quest[s] of $PLAYERS",
            "$PLAYERS'[s] tracked typewriter quest[s]",
        )
        // Order matters, ExprQuests reads the kind off the matched pattern.
        Skript.registerExpression(
            ExprQuests::class.java, Entry::class.java, ExpressionType.PROPERTY,
            "[all [[of] the]] active typewriter quests of $PLAYERS",
            "[all [[of] the]] completed typewriter quests of $PLAYERS",
            "[all [[of] the]] inactive typewriter quests of $PLAYERS",
        )
        Skript.registerExpression(
            ExprQuestStatus::class.java, String::class.java, ExpressionType.COMBINED,
            "[the] typewriter quest status of $ENTRIES (of|for) $PLAYERS",
        )
        Skript.registerExpression(
            ExprQuestDisplay::class.java, String::class.java, ExpressionType.COMBINED,
            "[the] typewriter (quest|objective) display [name] of $ENTRIES (of|for) $PLAYERS",
        )
    }

    private fun registerEffects() {
        Skript.registerEffect(
            EffTriggerEntry::class.java,
            "trigger [the] [typewriter] entr(y|ies) $ENTRIES (for|to) $PLAYERS",
        )
        // The "without blocking messages" variant has to stay pattern 1.
        Skript.registerEffect(
            EffStartCinematic::class.java,
            "(start|play) [the] [typewriter] cinematic [page] %typewriterpage/string% (for|to) $PLAYERS",
            "(start|play) [the] [typewriter] cinematic [page] %typewriterpage/string% (for|to) $PLAYERS " +
                "without blocking (chat|messages)",
        )
        Skript.registerEffect(
            EffStopCinematic::class.java,
            "stop [the] [typewriter] cinematic (of|for) $PLAYERS",
        )
        Skript.registerEffect(
            EffNextDialogue::class.java,
            "(continue|advance) [the] typewriter dialogue (of|for) $PLAYERS",
            "force [the] next typewriter dialogue (of|for) $PLAYERS",
        )
        Skript.registerEffect(
            EffEndInteraction::class.java,
            "(end|close) [the] typewriter (interaction|dialogue) (of|for) $PLAYERS",
        )
        Skript.registerEffect(
            EffTrackQuest::class.java,
            "track [the] [typewriter] quest $ENTRY (of|for) $PLAYERS",
            "untrack [the] [current] [typewriter] quest (of|for) $PLAYERS",
        )
        Skript.registerEffect(
            EffAudience::class.java,
            "add $PLAYERS to [the] typewriter audience [of] $ENTRIES",
            "remove $PLAYERS from [the] typewriter audience [of] $ENTRIES",
        )
        Skript.registerEffect(
            EffRefreshFact::class.java,
            "refresh [the] typewriter fact[s] $ENTRIES (of|for) $PLAYERS",
        )
        Skript.registerEffect(
            EffCreateCinematic::class.java,
            "(create|make|record) [a] [new] typewriter cinematic [page] [named] %string% " +
                "(along|from|with|through) %locations% " +
                "[(over|lasting) %-timespan/number% [(second[s]|:tick[s])]]",
        )
        Skript.registerEffect(
            EffCreateEntity::class.java,
            "(create|spawn) [a] [new] typewriter (entity|npc) [instance] [named] %string% " +
                "of [(definition|type|kind)] $ENTRY at %location% [on [page] %-string%]",
        )
        Skript.registerEffect(
            EffPublishPages::class.java,
            "publish [the] [staged] typewriter (pages|changes)",
        )
        Skript.registerEffect(
            EffDeletePage::class.java,
            "delete [the] typewriter (page|cinematic)[s] %strings%",
        )
        Skript.registerEffect(
            EffCreateDefinition::class.java,
            "(create|make) [a] [new] typewriter (definition|npc definition) [named] %string% " +
                "with skin [of] %string% [display name %-string%] [on [page] %-string%]",
        )
        Skript.registerEffect(
            EffSetSkin::class.java,
            "set [the] typewriter skin of %string% to %string%",
        )
        Skript.registerEffect(
            EffSetDisplayName::class.java,
            "set [the] typewriter display name of %string% to %string%",
        )
        Skript.registerEffect(
            EffTeleportEntity::class.java,
            "teleport [the] typewriter (entity|npc) %string% to %location%",
        )
        Skript.registerEffect(
            EffSetActivity::class.java,
            "set [the] typewriter activity of %string% to patrol (along|through|between) %locations%",
        )
        Skript.registerEffect(
            EffCreateEntityCinematic::class.java,
            "(create|make) [a] [new] typewriter (entity|npc) cinematic [named] %string% " +
                "(for|of|with) $ENTRY (along|from|through) %locations% " +
                "[(over|lasting) %-timespan/number% [(second[s]|:tick[s])]]",
        )
        Skript.registerEffect(
            EffDeleteEntry::class.java,
            "(remove|delete) [the] typewriter (entity|npc) [instance] [named] %strings%",
            "(remove|delete) [the] typewriter entr(y|ies) [named] %strings% from [the] page[s]",
        )
    }

    private fun registerConditions() {
        // Pattern 0 is positive, pattern 1 is negated, everywhere.
        Skript.registerCondition(
            CondInDialogue::class.java, ConditionType.PROPERTY,
            "$PLAYERS (is|are) [current(ly)] in [a] typewriter dialogue",
            "$PLAYERS (is not|isn't|are not|aren't) [current(ly)] in [a] typewriter dialogue",
        )
        Skript.registerCondition(
            CondInCinematic::class.java, ConditionType.PROPERTY,
            "$PLAYERS (is|are) [current(ly)] (playing|watching|in) [a] typewriter cinematic [%-string%]",
            "$PLAYERS (is not|isn't|are not|aren't) [current(ly)] (playing|watching|in) [a] typewriter cinematic [%-string%]",
        )
        Skript.registerCondition(
            CondInAudience::class.java, ConditionType.PROPERTY,
            "$PLAYERS (is|are) in [the] typewriter audience [of] $ENTRY",
            "$PLAYERS (is not|isn't|are not|aren't) in [the] typewriter audience [of] $ENTRY",
        )
        Skript.registerCondition(
            CondQuestStatus::class.java, ConditionType.COMBINED,
            "[the] [typewriter] quest $ENTRY (is|are) (:active|:completed|:inactive) (of|for) $PLAYERS",
            "[the] [typewriter] quest $ENTRY (is not|isn't|are not|aren't) (:active|:completed|:inactive) (of|for) $PLAYERS",
        )
        Skript.registerCondition(
            CondQuestTracked::class.java, ConditionType.PROPERTY,
            "$PLAYERS (is|are) tracking [the] [typewriter] quest [%-typewriterentry/string%]",
            "$PLAYERS (is not|isn't|are not|aren't) tracking [the] [typewriter] quest [%-typewriterentry/string%]",
        )
        Skript.registerCondition(
            CondEntryExists::class.java, ConditionType.COMBINED,
            "[the] typewriter entr(y|ies) %strings% exist[s]",
            "[the] typewriter entr(y|ies) %strings% (does not|doesn't|do not|don't) exist",
        )
        Skript.registerCondition(
            CondCriteriaMet::class.java, ConditionType.COMBINED,
            "[the] typewriter criteria of $ENTRY (is|are) met (of|for) $PLAYERS",
            "[the] typewriter criteria of $ENTRY (is not|isn't|are not|aren't) met (of|for) $PLAYERS",
        )
        // 0/1 ask about Typewriter, 2/3 about the Quest extension.
        Skript.registerCondition(
            CondTypewriterLoaded::class.java, ConditionType.COMBINED,
            "typewriter (is|are) (loaded|enabled|available)",
            "typewriter (is not|isn't|are not|aren't) (loaded|enabled|available)",
            "typewriter quests (is|are) (loaded|enabled|available|supported)",
            "typewriter quests (is not|isn't|are not|aren't) (loaded|enabled|available|supported)",
        )
    }
}
