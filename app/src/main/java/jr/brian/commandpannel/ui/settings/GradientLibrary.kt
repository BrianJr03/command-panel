package jr.brian.commandpannel.ui.settings

import jr.brian.commandpannel.system.GradientRules
import jr.brian.commandpannel.system.GradientSpec

/** The gradient library's rules (#67), pure so they're unit-tested. The screen is [GradientLibraryScreen]. */
internal object GradientLibrary {

    /** What the panel shows right now, for the library's header and the Appearance card. */
    enum class Showing { GRADIENT, IMAGE, BACKDROP }

    /**
     * [Showing] for the active gradient [activeId] (null: the source isn't a gradient), the saved [gradients] and
     * whether an image is stored. Same fallback as [jr.brian.commandpannel.system.BackgroundSetting.resolve]: a
     * gradient id that no longer exists shows the image, or the backdrop.
     */
    fun showing(activeId: String?, gradients: List<GradientSpec>, hasImage: Boolean): Showing = when {
        activeId != null && gradients.any { it.id == activeId } -> Showing.GRADIENT
        hasImage -> Showing.IMAGE
        else -> Showing.BACKDROP
    }

    /** "Use my image instead" is offered only while a saved gradient is showing and an image is stored to go back to. */
    fun canUseImage(activeId: String?, gradients: List<GradientSpec>, hasImage: Boolean): Boolean =
        hasImage && showing(activeId, gradients, hasImage) == Showing.GRADIENT

    /**
     * A copy of [spec] under [newId], named like a duplicated look ("<name> copy", "<name> copy 2", …, via [copyName])
     * so it doesn't clash with any name in [existing]. A long name is shortened so the copy still fits
     * [GradientRules.MAX_NAME] with its suffix. [fallbackName] stands in for a blank name.
     */
    fun duplicate(
        spec: GradientSpec,
        existing: List<GradientSpec>,
        newId: String,
        fallbackName: String,
        copy: (String) -> String,
        copyN: (String, Int) -> String,
    ): GradientSpec {
        val taken = existing.map { it.name }
        var base = spec.name.trim().ifEmpty { fallbackName }
        var name = copyName(base, taken, copy, copyN)
        while (name.length > GradientRules.MAX_NAME && base.isNotEmpty()) {
            base = base.dropLast(name.length - GradientRules.MAX_NAME).trimEnd()
            name = copyName(base, taken, copy, copyN)
        }
        return spec.copy(id = newId, name = name.take(GradientRules.MAX_NAME))
    }

    /**
     * Where focus goes after deleting [deletedId] from [ids] (the tiles in order): the tile that takes its place,
     * else the one before it, or null when none are left (focus goes to the screen's Back button).
     */
    fun focusAfterDelete(ids: List<String>, deletedId: String): String? {
        val i = ids.indexOf(deletedId)
        if (i < 0) return null
        return ids.getOrNull(i + 1) ?: ids.getOrNull(i - 1)
    }
}
