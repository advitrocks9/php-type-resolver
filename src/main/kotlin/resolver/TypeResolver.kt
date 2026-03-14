package resolver

import resolver.api.DocTag
import resolver.api.PhpType
import resolver.api.PhpVariable
import resolver.api.TypeFactory

private const val MIXED = "mixed"

/** Resolves the type of a PHP variable from its @var doc tag. */
fun inferTypeFromDoc(variable: PhpVariable): PhpType {
    val docBlock = variable.getDocBlock() ?: return TypeFactory.createType(MIXED)
    val tags = docBlock.getTagsByName("var")
    if (tags.isEmpty()) return TypeFactory.createType(MIXED)
    val match = findMatchingTag(tags, variable.getName()) ?: return TypeFactory.createType(MIXED)
    return resolveTypeString(match.first)
}

/** Extracts the type string and optional variable name from a tag value. */
private fun parseTagValue(value: String): Pair<String, String?> {
    val tokens = value.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return Pair("", null)
    val typeString = tokens[0]
    val varName = if (tokens.size >= 2 && tokens[1].startsWith("$")) tokens[1] else null
    return Pair(typeString, varName)
}

/** Finds the best matching tag using explicit name first, then unnamed fallback. */
private fun findMatchingTag(tags: List<DocTag>, variableName: String): Pair<String, String?>? {
    val parsed = tags.map { parseTagValue(it.getValue()) }.filter { it.first.isNotEmpty() }
    val explicit = parsed.firstOrNull { it.second == variableName }
    if (explicit != null) return explicit
    return parsed.firstOrNull { it.second == null }
}

/** Resolves a type string into a PhpType, handling nullable shorthand and unions. */
private fun resolveTypeString(typeString: String): PhpType {
    if (typeString == "?") return TypeFactory.createType(MIXED)
    val expanded = if (typeString.startsWith("?")) typeString.drop(1) + "|null" else typeString
    val parts = expanded.split("|").filter { it.isNotEmpty() }
    if (parts.isEmpty()) return TypeFactory.createType(MIXED)
    if (parts.size == 1) return TypeFactory.createType(parts[0])
    return TypeFactory.createUnionType(parts.map { TypeFactory.createType(it) })
}
