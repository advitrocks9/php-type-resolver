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

/** Finds the first tag that matches the variable name or has no variable name. */
private fun findMatchingTag(tags: List<DocTag>, variableName: String): Pair<String, String?>? {
    return tags.map { parseTagValue(it.getValue()) }
        .filter { it.first.isNotEmpty() }
        .firstOrNull { it.second == null || it.second == variableName }
}

/** Resolves a type string into a PhpType. */
private fun resolveTypeString(typeString: String): PhpType {
    return TypeFactory.createType(typeString)
}
