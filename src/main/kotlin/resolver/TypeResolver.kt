package resolver

import resolver.api.PhpType
import resolver.api.PhpVariable
import resolver.api.TypeFactory

private const val MIXED = "mixed"
private val WHITESPACE = "\\s+".toRegex()
/**
 * Infers the [PhpType] of [variable] from its `@var` doc tag.
 *
 * Explicit name matches (e.g. `@var Logger $log`) take priority over unnamed tags.
 * Nullable shorthand (`?User`) is expanded to `User|null`.
 * Returns `mixed` if no doc block, no `@var` tags, or no matching tag is found.
 */
fun inferTypeFromDoc(variable: PhpVariable): PhpType {
    val docBlock = variable.getDocBlock() ?: return TypeFactory.createType(MIXED)
    val tags = docBlock.getTagsByName("var")
    if (tags.isEmpty()) return TypeFactory.createType(MIXED)
    val parsed = tags.map { parseTagValue(it.getValue()) }.filter { it.first.isNotEmpty() }
    val match = parsed.firstOrNull { it.second == variable.getName() }
        ?: parsed.firstOrNull { it.second == null }
        ?: return TypeFactory.createType(MIXED)
    return resolveTypeString(match.first)
}

private fun parseTagValue(value: String): Pair<String, String?> {
    val tokens = value.trim().split(WHITESPACE).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return Pair("", null)
    var depth = 0
    var typeEnd = 0
    for ((i, token) in tokens.withIndex()) {
        depth += token.count { it == '<' } - token.count { it == '>' }
        typeEnd = i; if (depth <= 0) break
    }
    val typeString = tokens.take(typeEnd + 1).joinToString(" ")
    return Pair(typeString, tokens.getOrNull(typeEnd + 1)?.takeIf { it.startsWith("$") })
}

private fun resolveTypeString(typeString: String): PhpType {
    if (typeString == "?") return TypeFactory.createType(MIXED)
    val expanded = if (typeString.startsWith("?")) typeString.drop(1) + "|null" else typeString
    val parts = splitTopLevelUnion(expanded).filter { it.isNotEmpty() }.distinct()
    if (parts.isEmpty()) return TypeFactory.createType(MIXED)
    if (parts.size == 1) return TypeFactory.createType(parts[0])
    return TypeFactory.createUnionType(parts.map { TypeFactory.createType(it) })
}
private fun splitTopLevelUnion(typeString: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var depth = 0
    for (c in typeString) when {
        c == '<' -> { depth++; current.append(c) }
        c == '>' -> { depth--; current.append(c) }
        c == '|' && depth == 0 -> { parts.add(current.toString()); current.clear() }
        else -> current.append(c)
    }
    parts.add(current.toString())
    return parts
}
