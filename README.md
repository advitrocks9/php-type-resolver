# PHP TypeResolver

A lightweight static analysis tool that resolves the type of a PHP variable from its `@var` documentation block.

## Running

Requires JDK 21.

```bash
./gradlew test
```

## How It Works

The entry point is `inferTypeFromDoc(variable: PhpVariable): PhpType`. Given a PHP variable, it:

1. Retrieves the variable's doc block and looks up all `@var` tags.
2. Parses each tag value into a type string and an optional variable name.
3. Finds the best matching tag using a two-pass approach: first looks for a tag with an explicit variable name that matches the inspected variable, then falls back to the first unnamed tag.
4. Resolves the matched type string into a `PhpType`, handling union types (`string|int`) and nullable shorthand (`?User`).
5. Returns `mixed` if no doc block exists, no tags are found, or no tag matches the variable.

### Tag Matching

Each tag value is split on whitespace. The first token is always the type string. The second token, if it starts with `$`, is the variable name the tag targets. Anything after that is a human description and gets ignored.

A tag with no variable name (e.g. `@var User`) applies to whatever variable the doc block is attached to. A tag with a variable name (e.g. `@var Logger $log`) only applies if that name matches the inspected variable.

When multiple `@var` tags exist in a single doc block, an explicit name match always takes priority over an unnamed tag. This is done with two separate passes over the tag list rather than inline priority logic, which keeps the matching simple and predictable.

### Type Resolution

Nullable shorthand is expanded before union splitting. `?User` becomes `User|null`, then the pipe-splitting logic handles it the same way as any other union. This avoids duplicating the union construction code.

If splitting on `|` produces a single type after filtering empty segments, the result is a plain `PhpType` rather than a `UnionType` with one element.

## Design Decisions

**Helpers over monolith.** The resolver is split into three small private functions (`parseTagValue`, `findMatchingTag`, `resolveTypeString`) rather than one large function. Each does one thing and is independently testable.

**Two-pass matching.** A single-pass approach with inline priority tracking would work but is harder to read. Two passes makes the precedence rule (explicit name match > unnamed tag) obvious from the code structure.

**No regex for type parsing.** Splitting on `|` and checking for a `?` prefix is enough. Regex would be overkill for this grammar and harder to follow.

## Limitations

There are a few things this implementation does not handle that a full PHPDoc type resolver would need:

- Generic types like `array<string, User>` or `Collection<Item>`. A pipe inside angle brackets would currently be treated as a union separator.
- Intersection types (`A&B`), which are valid in PHP 8.1+ doc blocks.
- Array shapes like `array{id: int, name: string}`.
- The `self`, `static`, and `parent` pseudo-types, which would need class context to resolve.

These are out of scope for this task but would be natural next steps.

## Test Coverage

23 tests organized into 7 groups:

- **Fallback behavior**: null doc block, empty tag list
- **Simple type resolution**: standard types, fully qualified class names
- **Variable name matching**: named match, mismatch, unnamed tags
- **Union types**: two/three members, malformed pipes, unions on named tags
- **Nullable shorthand**: `?Type` expansion, bare `?`, nullable on named tags
- **Multiple tags**: correct selection, no match, explicit over unnamed precedence
- **Whitespace and formatting**: trimming, extra spaces, descriptions, empty/whitespace-only values
