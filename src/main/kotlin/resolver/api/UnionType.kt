package resolver.api

class UnionType(val types: List<PhpType>) : PhpType {
    override val name: String
        get() = types.joinToString("|") { it.name }

    override fun equals(other: Any?): Boolean =
        other is UnionType && types.toSet() == other.types.toSet()

    override fun hashCode(): Int = types.toSet().hashCode()
}
