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
    val firstTag = tags.firstOrNull() ?: return TypeFactory.createType(MIXED)
    val (typeString, _) = parseTagValue(firstTag.getValue())
    if (typeString.isEmpty()) return TypeFactory.createType(MIXED)
    return resolveTypeString(typeString)
}

/** Extracts the type string and optional variable name from a tag value. */
private fun parseTagValue(value: String): Pair<String, String?> {
    val tokens = value.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return Pair("", null)
    return Pair(tokens[0], null)
}

/** Resolves a type string into a PhpType. */
private fun resolveTypeString(typeString: String): PhpType {
    return TypeFactory.createType(typeString)
}
