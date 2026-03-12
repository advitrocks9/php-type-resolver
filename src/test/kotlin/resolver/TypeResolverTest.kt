package resolver

import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.Nested
import resolver.api.DocTag
import resolver.api.PhpDocBlock
import resolver.api.PhpVariable
import resolver.api.TypeFactory

class TypeResolverTest {

    private fun makeVariable(name: String, docBlock: PhpDocBlock?): PhpVariable {
        return object : PhpVariable {
            override fun getDocBlock(): PhpDocBlock? = docBlock
            override fun getName(): String = name
        }
    }

    private fun makeDocBlock(varTags: List<String>): PhpDocBlock {
        return object : PhpDocBlock {
            override fun getTagsByName(tagName: String): List<DocTag> {
                if (tagName != "var") return emptyList()
                return varTags.map { value ->
                    object : DocTag {
                        override fun getValue(): String = value
                    }
                }
            }
        }
    }

    @Nested
    inner class FallbackBehavior {

        @Test
        fun returnsMixedWhenDocBlockIsNull() {
            val variable = makeVariable("\$x", null)
            assertEquals(TypeFactory.createType("mixed"), inferTypeFromDoc(variable))
        }

        @Test
        fun returnsMixedWhenTagListIsEmpty() {
            val variable = makeVariable("\$x", makeDocBlock(emptyList()))
            assertEquals(TypeFactory.createType("mixed"), inferTypeFromDoc(variable))
        }
    }

    @Nested
    inner class SimpleTypeResolution {

        @Test
        fun resolvesSimpleTypeFromUnnamedTag() {
            val variable = makeVariable("\$user", makeDocBlock(listOf("User")))
            assertEquals(TypeFactory.createType("User"), inferTypeFromDoc(variable))
        }

        @Test
        fun resolvesFullyQualifiedClassName() {
            val variable = makeVariable("\$user", makeDocBlock(listOf("\\App\\Models\\User")))
            assertEquals(TypeFactory.createType("\\App\\Models\\User"), inferTypeFromDoc(variable))
        }
    }
}
