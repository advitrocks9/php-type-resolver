package resolver.api

object TypeFactory {
    fun createType(typeName: String): PhpType {
        return object : PhpType {
            override val name: String = typeName
            override fun equals(other: Any?): Boolean =
                other is PhpType && other.name == name
            override fun hashCode(): Int = name.hashCode()
            override fun toString(): String = "PhpType($name)"
        }
    }

    fun createUnionType(types: List<PhpType>): PhpType {
        return UnionType(types)
    }
}
