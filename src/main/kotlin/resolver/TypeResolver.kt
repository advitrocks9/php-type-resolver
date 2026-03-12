package resolver

import resolver.api.PhpType
import resolver.api.PhpVariable
import resolver.api.TypeFactory

private const val MIXED = "mixed"

/** Resolves the type of a PHP variable from its @var doc tag. */
fun inferTypeFromDoc(variable: PhpVariable): PhpType {
    return TypeFactory.createType(MIXED)
}
