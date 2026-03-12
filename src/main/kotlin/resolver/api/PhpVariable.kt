package resolver.api

interface PhpVariable {
    fun getDocBlock(): PhpDocBlock?
    fun getName(): String
}
