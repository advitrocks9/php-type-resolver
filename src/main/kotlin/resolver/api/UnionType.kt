package resolver.api

data class UnionType(val types: List<PhpType>) : PhpType {
    override val name: String
        get() = types.joinToString("|") { it.name }
}
