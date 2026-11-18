package jr.brian.commandpannel.ui.settings

/**
 * A name for a copy of [name] that no saved look uses yet: "<name> copy", then "<name> copy 2", "copy 3", … [copy]
 * and [copyN] build the localized forms (`theme_copy_name` / `theme_copy_name_n`). Comparison ignores case.
 */
internal fun copyName(name: String, taken: Collection<String>, copy: (String) -> String, copyN: (String, Int) -> String): String {
    val used = taken.map { it.trim().lowercase() }.toSet()
    val first = copy(name)
    if (first.trim().lowercase() !in used) return first
    var n = 2
    while (copyN(name, n).trim().lowercase() in used) n++
    return copyN(name, n)
}

/** [name], or [fallback] when it's blank (a look always saves with a name). */
internal fun nonBlankName(name: String, fallback: () -> String): String = name.trim().ifEmpty { fallback() }
