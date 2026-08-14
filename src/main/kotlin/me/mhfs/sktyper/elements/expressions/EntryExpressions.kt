package me.mhfs.sktyper.elements.expressions

import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.RequiredPlugins
import ch.njol.skript.doc.Since
import ch.njol.skript.expressions.base.SimplePropertyExpression
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser.ParseResult
import ch.njol.skript.lang.util.SimpleExpression
import ch.njol.util.Kleenean
import com.typewritermc.core.entries.Entry
import com.typewritermc.core.entries.Page
import me.mhfs.sktyper.tw.Tw
import org.bukkit.event.Event

@Name("Typewriter Entry")
@Description(
    "Looks up a Typewriter entry by its id, falling back to its name.",
    "Ids are stable and are what the Typewriter panel shows; names are what you called the entry.",
)
@Examples(
    "set {_dialogue} to typewriter entry \"welcome_dialogue\"",
    "trigger typewriter entries \"first_quest\" and \"second_quest\" for player",
)
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprEntry : SimpleExpression<Entry>() {

    private lateinit var query: Expression<String>

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        query = exprs[0] as Expression<String>
        return true
    }

    override fun get(event: Event): Array<Entry> =
        query.getArray(event).mapNotNull { Tw.entry(it) }.toTypedArray()

    override fun isSingle(): Boolean = query.isSingle

    override fun getReturnType(): Class<out Entry> = Entry::class.java

    override fun toString(event: Event?, debug: Boolean): String =
        "typewriter entry ${query.toString(event, debug)}"
}

@Name("All Typewriter Entries")
@Description("Every entry Typewriter currently has loaded, across all pages.")
@Examples("send \"%size of all typewriter entries% entries loaded\"")
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprAllEntries : SimpleExpression<Entry>() {

    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean = true

    override fun get(event: Event): Array<Entry> = Tw.allEntries().toTypedArray()

    override fun isSingle(): Boolean = false

    override fun getReturnType(): Class<out Entry> = Entry::class.java

    override fun toString(event: Event?, debug: Boolean): String = "all typewriter entries"
}

@Name("Typewriter Entry Id")
@Description("The id of a Typewriter entry - the stable identifier shown in the Typewriter panel.")
@Examples("send \"%the typewriter id of {_quest}%\"")
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprEntryId : SimplePropertyExpression<Entry, String>() {

    override fun convert(from: Entry): String = from.id

    override fun getPropertyName(): String = "typewriter id"

    override fun getReturnType(): Class<out String> = String::class.java
}

@Name("Typewriter Entry Name")
@Description("The name of a Typewriter entry, as typed into the Typewriter panel.")
@Examples("send \"%the typewriter name of {_quest}%\"")
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprEntryName : SimplePropertyExpression<Entry, String>() {

    override fun convert(from: Entry): String = from.name

    override fun getPropertyName(): String = "typewriter name"

    override fun getReturnType(): Class<out String> = String::class.java
}

@Name("Typewriter Entry Page")
@Description("The page a Typewriter entry lives on.")
@Examples("send \"%the typewriter page of {_dialogue}%\"")
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprEntryPage : SimplePropertyExpression<Entry, Page>() {

    override fun convert(from: Entry): Page? = Tw.pageOf(from)

    override fun getPropertyName(): String = "typewriter page"

    override fun getReturnType(): Class<out Page> = Page::class.java
}

@Name("Typewriter Page")
@Description("Looks up a Typewriter page by its id, falling back to its name.")
@Examples("set {_page} to typewriter page \"intro_cutscene\"")
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprPage : SimpleExpression<Page>() {

    private lateinit var query: Expression<String>

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        query = exprs[0] as Expression<String>
        return true
    }

    override fun get(event: Event): Array<Page> =
        query.getArray(event).mapNotNull { Tw.page(it) }.toTypedArray()

    override fun isSingle(): Boolean = query.isSingle

    override fun getReturnType(): Class<out Page> = Page::class.java

    override fun toString(event: Event?, debug: Boolean): String =
        "typewriter page ${query.toString(event, debug)}"
}

@Name("All Typewriter Pages")
@Description("Every page Typewriter currently has loaded.")
@Examples("loop all typewriter pages:", "\tsend \"%the typewriter name of loop-value%\"")
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprAllPages : SimpleExpression<Page>() {

    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean = true

    override fun get(event: Event): Array<Page> = Tw.allPages().toTypedArray()

    override fun isSingle(): Boolean = false

    override fun getReturnType(): Class<out Page> = Page::class.java

    override fun toString(event: Event?, debug: Boolean): String = "all typewriter pages"
}

@Name("Typewriter Page Id")
@Description("The id of a Typewriter page. This is what cinematics are started by.")
@Examples("start typewriter cinematic (the typewriter id of {_page}) for player")
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprPageId : SimplePropertyExpression<Page, String>() {

    override fun convert(from: Page): String = from.id

    override fun getPropertyName(): String = "typewriter id"

    override fun getReturnType(): Class<out String> = String::class.java
}

@Name("Typewriter Page Name")
@Description("The name of a Typewriter page.")
@Examples("send \"%the typewriter name of {_page}%\"")
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprPageName : SimplePropertyExpression<Page, String>() {

    override fun convert(from: Page): String = from.name

    override fun getPropertyName(): String = "typewriter name"

    override fun getReturnType(): Class<out String> = String::class.java
}

@Name("Typewriter Page Type")
@Description("The type of a Typewriter page: `sequence`, `static`, `cinematic` or `manifest`.")
@Examples("if the typewriter type of {_page} is \"cinematic\":")
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprPageType : SimplePropertyExpression<Page, String>() {

    override fun convert(from: Page): String = from.type.id

    override fun getPropertyName(): String = "typewriter type"

    override fun getReturnType(): Class<out String> = String::class.java
}

@Name("Typewriter Page Priority")
@Description("The priority of a Typewriter page. Higher priority pages win when several could run.")
@Examples("send \"%the typewriter priority of {_page}%\"")
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprPagePriority : SimplePropertyExpression<Page, Number>() {

    override fun convert(from: Page): Number = from.priority

    override fun getPropertyName(): String = "typewriter priority"

    override fun getReturnType(): Class<out Number> = Number::class.java
}

@Name("Typewriter Page Entries")
@Description("All entries that live on the given Typewriter pages.")
@Examples("loop the typewriter entries of typewriter page \"main_story\":", "\tsend \"%loop-value%\"")
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprPageEntries : SimpleExpression<Entry>() {

    private lateinit var pages: Expression<*>

    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        pages = exprs[0]
        return true
    }

    override fun get(event: Event): Array<Entry> =
        pages.getArray(event).mapNotNull { Tw.resolvePage(it) }.flatMap { it.entries }.toTypedArray()

    override fun isSingle(): Boolean = false

    override fun getReturnType(): Class<out Entry> = Entry::class.java

    override fun toString(event: Event?, debug: Boolean): String =
        "typewriter entries of ${pages.toString(event, debug)}"
}

@Name("Typewriter Version")
@Description("The version of the Typewriter plugin running on this server.")
@Examples("send \"Running Typewriter %the typewriter version%\"")
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprTypewriterVersion : SimpleExpression<String>() {

    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean = true

    override fun get(event: Event): Array<String> = arrayOf(Tw.version ?: "unknown")

    override fun isSingle(): Boolean = true

    override fun getReturnType(): Class<out String> = String::class.java

    override fun toString(event: Event?, debug: Boolean): String = "the typewriter version"
}
