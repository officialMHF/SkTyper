package me.mhfs.sktyper.types

import ch.njol.skript.classes.ClassInfo
import ch.njol.skript.classes.Parser
import ch.njol.skript.lang.ParseContext
import ch.njol.skript.registrations.Classes
import com.typewritermc.core.entries.Entry
import com.typewritermc.core.entries.Page
import me.mhfs.sktyper.tw.Tw

/**
 * Everything in Typewriter is an entry - dialogue, facts, quests, cinematics, NPC definitions - so
 * one type covers the lot and the individual syntax checks at runtime what it was handed.
 */
object TypewriterTypes {

    fun register() {
        if (Classes.getClassInfoNoError("typewriterentry") == null) {
            Classes.registerClass(
                ClassInfo(Entry::class.java, "typewriterentry")
                    .user("typewriter ?entr(y|ies)")
                    .name("Typewriter Entry")
                    .description(
                        "An entry from a Typewriter page: a dialogue, a fact, a quest, an objective, " +
                            "a cinematic, an NPC definition, an audience, and so on.",
                        "Entries are looked up by their id (the value shown in the Typewriter panel) " +
                            "or by their name. Anywhere SkTyper accepts an entry it also accepts the " +
                            "plain text id or name.",
                    )
                    .usage("typewriter entry \"<id or name>\"")
                    .examples(
                        "set {_quest} to typewriter entry \"villager_intro\"",
                        "trigger typewriter entry \"welcome_dialogue\" for player",
                    )
                    .since("1.0.0")
                    .parser(object : Parser<Entry>() {
                        override fun parse(input: String, context: ParseContext): Entry? = Tw.entry(input)

                        // Config values are read before Typewriter loads its pages, so a lookup
                        // there would always miss.
                        override fun canParse(context: ParseContext): Boolean = context != ParseContext.CONFIG

                        override fun toString(entry: Entry, flags: Int): String = Tw.displayString(entry)

                        override fun toVariableNameString(entry: Entry): String = "typewriterentry:${entry.id}"
                    }),
            )
        }

        if (Classes.getClassInfoNoError("typewriterpage") == null) {
            Classes.registerClass(
                ClassInfo(Page::class.java, "typewriterpage")
                    .user("typewriter ?pages?")
                    .name("Typewriter Page")
                    .description(
                        "A page from the Typewriter panel. Pages group entries and come in four " +
                            "flavours: sequence, static, cinematic and manifest.",
                        "Cinematics are started by page, which is the most common reason to touch " +
                            "this type from a script.",
                    )
                    .usage("typewriter page \"<id or name>\"")
                    .examples(
                        "set {_page} to typewriter page \"intro_cutscene\"",
                        "start typewriter cinematic \"intro_cutscene\" for player",
                    )
                    .since("1.0.0")
                    .parser(object : Parser<Page>() {
                        override fun parse(input: String, context: ParseContext): Page? = Tw.page(input)

                        override fun canParse(context: ParseContext): Boolean = context != ParseContext.CONFIG

                        override fun toString(page: Page, flags: Int): String = page.name

                        override fun toVariableNameString(page: Page): String = "typewriterpage:${page.id}"
                    }),
            )
        }
    }
}
