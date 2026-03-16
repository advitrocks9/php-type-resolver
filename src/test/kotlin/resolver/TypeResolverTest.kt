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

    private fun phpVar(name: String, vararg tags: String): PhpVariable =
        makeVariable(name, if (tags.isNotEmpty()) makeDocBlock(tags.toList()) else null)

    private fun phpVarNoDoc(name: String): PhpVariable =
        makeVariable(name, null)

    @Nested
    inner class FallbackBehavior {

        @Test
        fun returnsMixedWhenDocBlockIsNull() {
            assertEquals(TypeFactory.createType("mixed"), inferTypeFromDoc(phpVarNoDoc("\$x")))
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
            assertEquals(TypeFactory.createType("User"), inferTypeFromDoc(phpVar("\$user", "User")))
        }

        @Test
        fun resolvesFullyQualifiedClassName() {
            assertEquals(TypeFactory.createType("\\App\\Models\\User"), inferTypeFromDoc(phpVar("\$user", "\\App\\Models\\User")))
        }
    }

    @Nested
    inner class VariableNameMatching {

        @Test
        fun returnsMatchingTypeWhenTagVariableMatchesInspectedVariable() {
            assertEquals(TypeFactory.createType("Logger"), inferTypeFromDoc(phpVar("\$log", "Logger \$log")))
        }

        @Test
        fun returnsMixedWhenTagVariableDoesNotMatch() {
            assertEquals(TypeFactory.createType("mixed"), inferTypeFromDoc(phpVar("\$guest", "Admin \$adm")))
        }

        @Test
        fun unnamedTagAppliesToAnyVariable() {
            assertEquals(TypeFactory.createType("User"), inferTypeFromDoc(phpVar("\$anything", "User")))
        }
    }

    @Nested
    inner class UnionTypes {

        @Test
        fun resolvesUnionTypeForTwoTypes() {
            val expected = TypeFactory.createUnionType(listOf(TypeFactory.createType("string"), TypeFactory.createType("int")))
            assertEquals(expected, inferTypeFromDoc(phpVar("\$id", "string|int")))
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
            assertEquals(TypeFactory.createType("string"), inferTypeFromDoc(phpVar("\$x", "string|")))
        }

        @Test
        fun ignoresLeadingPipeInTypeString() {
            assertEquals(TypeFactory.createType("string"), inferTypeFromDoc(phpVar("\$x", "|string")))
        }

        @Test
        fun resolvesUnionTypeOnNamedTag() {
            val expected = TypeFactory.createUnionType(listOf(TypeFactory.createType("string"), TypeFactory.createType("int")))
            assertEquals(expected, inferTypeFromDoc(phpVar("\$id", "string|int \$id")))
        }

        @Test
        fun unionTypeEqualityIsOrderIndependent() {
            val stringType = TypeFactory.createType("string")
            val intType = TypeFactory.createType("int")
            assertEquals(UnionType(listOf(stringType, intType)), UnionType(listOf(intType, stringType)))
        }

        @Test
        fun deduplicatesDuplicateTypesInUnion() {
            val expected = TypeFactory.createUnionType(listOf(TypeFactory.createType("string"), TypeFactory.createType("int")))
            assertEquals(expected, inferTypeFromDoc(phpVar("\$x", "string|string|int")))
        }

        @Test
        fun collapsesFullyDuplicateUnionToSingleType() {
            assertEquals(TypeFactory.createType("string"), inferTypeFromDoc(phpVar("\$x", "string|string")))
        }
    }

    @Nested
    inner class NullableShorthand {

        @Test
        fun handlesNullableShorthandAsUnionWithNull() {
            val expected = TypeFactory.createUnionType(listOf(TypeFactory.createType("User"), TypeFactory.createType("null")))
            assertEquals(expected, inferTypeFromDoc(phpVar("\$user", "?User")))
        }

        @Test
        fun returnsMixedForBareQuestionMark() {
            assertEquals(TypeFactory.createType("mixed"), inferTypeFromDoc(phpVar("\$x", "?")))
        }

        @Test
        fun handlesNullableShorthandOnNamedTag() {
            val expected = TypeFactory.createUnionType(listOf(TypeFactory.createType("User"), TypeFactory.createType("null")))
            assertEquals(expected, inferTypeFromDoc(phpVar("\$u", "?User \$u")))
        }
    }

    @Nested
    inner class MultipleTags {

        @Test
        fun returnsCorrectTypeFromMultipleTagsWhenOneMatches() {
            assertEquals(TypeFactory.createType("string"), inferTypeFromDoc(phpVar("\$name", "int \$id", "string \$name")))
        }

        @Test
        fun returnsMixedWhenMultipleTagsNoneMatch() {
            assertEquals(TypeFactory.createType("mixed"), inferTypeFromDoc(phpVar("\$age", "int \$id", "string \$name")))
        }

        @Test
        fun prefersExplicitNameMatchOverUnnamedTag() {
            assertEquals(TypeFactory.createType("Logger"), inferTypeFromDoc(phpVar("\$log", "object", "Logger \$log")))
        }
    }

    @Nested
    inner class WhitespaceAndFormatting {

        @Test
        fun trimsLeadingAndTrailingWhitespaceFromTagValue() {
            assertEquals(TypeFactory.createType("User"), inferTypeFromDoc(phpVar("\$u", "  User  ")))
        }

        @Test
        fun handlesExtraWhitespaceBetweenTokens() {
            assertEquals(TypeFactory.createType("Logger"), inferTypeFromDoc(phpVar("\$log", "Logger    \$log")))
        }

        @Test
        fun ignoresDescriptionTextAfterVariableName() {
            assertEquals(TypeFactory.createType("User"), inferTypeFromDoc(phpVar("\$admin", "User \$admin the main admin")))
        }

        @Test
        fun skipsEmptyTagValue() {
            assertEquals(TypeFactory.createType("string"), inferTypeFromDoc(phpVar("\$x", "", "string \$x")))
        }

        @Test
        fun skipsWhitespaceOnlyTagValue() {
            assertEquals(TypeFactory.createType("string"), inferTypeFromDoc(phpVar("\$x", "   ", "string \$x")))
        }
    }

    @Nested
    inner class GenericTypes {

        @Test
        fun preservesGenericTypeWithInternalPipe() {
            assertEquals(TypeFactory.createType("array<string|int>"), inferTypeFromDoc(phpVar("\$items", "array<string|int>")))
        }

        @Test
        fun splitsTopLevelUnionAroundGenericType() {
            val expected = TypeFactory.createUnionType(listOf(
                TypeFactory.createType("array<string|int>"),
                TypeFactory.createType("null")
            ))
            assertEquals(expected, inferTypeFromDoc(phpVar("\$items", "array<string|int>|null")))
        }

        @Test
        fun handlesNestedGenericsInUnion() {
            val expected = TypeFactory.createUnionType(listOf(
                TypeFactory.createType("Map<string, list<int|float>>"),
                TypeFactory.createType("null")
            ))
            assertEquals(expected, inferTypeFromDoc(phpVar("\$map", "Map<string, list<int|float>>|null")))
        }

        @Test
        fun parsesGenericTypeWithWhitespaceAndVariableName() {
            assertEquals(TypeFactory.createType("Map<string, int>"), inferTypeFromDoc(phpVar("\$m", "Map<string, int> \$m")))
        }

        @Test
        fun treatsUnclosedGenericAsPlainType() {
            assertEquals(TypeFactory.createType("array<string"), inferTypeFromDoc(phpVar("\$items", "array<string")))
        }
    }
}
