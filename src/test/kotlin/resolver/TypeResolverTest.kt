package resolver

import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.Nested
import resolver.api.DocTag
import resolver.api.PhpDocBlock
import resolver.api.PhpVariable
import resolver.api.TypeFactory
import resolver.api.UnionType

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

    @Nested
    inner class VariableNameMatching {

        @Test
        fun returnsMatchingTypeWhenTagVariableMatchesInspectedVariable() {
            val variable = makeVariable("\$log", makeDocBlock(listOf("Logger \$log")))
            assertEquals(TypeFactory.createType("Logger"), inferTypeFromDoc(variable))
        }

        @Test
        fun returnsMixedWhenTagVariableDoesNotMatch() {
            val variable = makeVariable("\$guest", makeDocBlock(listOf("Admin \$adm")))
            assertEquals(TypeFactory.createType("mixed"), inferTypeFromDoc(variable))
        }

        @Test
        fun unnamedTagAppliesToAnyVariable() {
            val variable = makeVariable("\$anything", makeDocBlock(listOf("User")))
            assertEquals(TypeFactory.createType("User"), inferTypeFromDoc(variable))
        }
    }

    @Nested
    inner class UnionTypes {

        @Test
        fun resolvesUnionTypeForTwoTypes() {
            val variable = makeVariable("\$id", makeDocBlock(listOf("string|int")))
            val expected = TypeFactory.createUnionType(listOf(TypeFactory.createType("string"), TypeFactory.createType("int")))
            assertEquals(expected, inferTypeFromDoc(variable))
        }

        @Test
        fun resolvesUnionTypeForThreeTypes() {
            val variable = makeVariable("\$val", makeDocBlock(listOf("string|int|null")))
            val expected = TypeFactory.createUnionType(listOf(
                TypeFactory.createType("string"),
                TypeFactory.createType("int"),
                TypeFactory.createType("null")
            ))
            assertEquals(expected, inferTypeFromDoc(variable))
        }

        @Test
        fun ignoresTrailingPipeInTypeString() {
            val variable = makeVariable("\$x", makeDocBlock(listOf("string|")))
            assertEquals(TypeFactory.createType("string"), inferTypeFromDoc(variable))
        }

        @Test
        fun ignoresLeadingPipeInTypeString() {
            val variable = makeVariable("\$x", makeDocBlock(listOf("|string")))
            assertEquals(TypeFactory.createType("string"), inferTypeFromDoc(variable))
        }
    }

    @Nested
    inner class NullableShorthand {

        @Test
        fun handlesNullableShorthandAsUnionWithNull() {
            val variable = makeVariable("\$user", makeDocBlock(listOf("?User")))
            val expected = TypeFactory.createUnionType(listOf(TypeFactory.createType("User"), TypeFactory.createType("null")))
            assertEquals(expected, inferTypeFromDoc(variable))
        }

        @Test
        fun returnsMixedForBareQuestionMark() {
            val variable = makeVariable("\$x", makeDocBlock(listOf("?")))
            assertEquals(TypeFactory.createType("mixed"), inferTypeFromDoc(variable))
        }
    }

    @Nested
    inner class MultipleTags {

        @Test
        fun returnsCorrectTypeFromMultipleTagsWhenOneMatches() {
            val variable = makeVariable("\$name", makeDocBlock(listOf("int \$id", "string \$name")))
            assertEquals(TypeFactory.createType("string"), inferTypeFromDoc(variable))
        }

        @Test
        fun returnsMixedWhenMultipleTagsNoneMatch() {
            val variable = makeVariable("\$age", makeDocBlock(listOf("int \$id", "string \$name")))
            assertEquals(TypeFactory.createType("mixed"), inferTypeFromDoc(variable))
        }

        @Test
        fun prefersExplicitNameMatchOverUnnamedTag() {
            val variable = makeVariable("\$log", makeDocBlock(listOf("object", "Logger \$log")))
            assertEquals(TypeFactory.createType("Logger"), inferTypeFromDoc(variable))
        }
    }

    @Nested
    inner class WhitespaceAndFormatting {

        @Test
        fun trimsLeadingAndTrailingWhitespaceFromTagValue() {
            val variable = makeVariable("\$u", makeDocBlock(listOf("  User  ")))
            assertEquals(TypeFactory.createType("User"), inferTypeFromDoc(variable))
        }

        @Test
        fun handlesExtraWhitespaceBetweenTokens() {
            val variable = makeVariable("\$log", makeDocBlock(listOf("Logger    \$log")))
            assertEquals(TypeFactory.createType("Logger"), inferTypeFromDoc(variable))
        }

        @Test
        fun ignoresDescriptionTextAfterVariableName() {
            val variable = makeVariable("\$admin", makeDocBlock(listOf("User \$admin the main admin")))
            assertEquals(TypeFactory.createType("User"), inferTypeFromDoc(variable))
        }

        @Test
        fun skipsEmptyTagValue() {
            val variable = makeVariable("\$x", makeDocBlock(listOf("", "string \$x")))
            assertEquals(TypeFactory.createType("string"), inferTypeFromDoc(variable))
        }

        @Test
        fun skipsWhitespaceOnlyTagValue() {
            val variable = makeVariable("\$x", makeDocBlock(listOf("   ", "string \$x")))
            assertEquals(TypeFactory.createType("string"), inferTypeFromDoc(variable))
        }
    }
}
