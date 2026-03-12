package resolver.api

interface PhpDocBlock {
    fun getTagsByName(tagName: String): List<DocTag>
}
