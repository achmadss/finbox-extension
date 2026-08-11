package dev.achmad.finbox.compiler

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier

private const val PARSER_ANNOTATION = "dev.achmad.finbox.extension.Parser"
private const val TRANSACTION_PARSER = "dev.achmad.finbox.extension.TransactionParser"

/** Must match GENERATED_CLASS in FinboxExtensionPlugin, which puts it in the manifest. */
private const val GENERATED_PACKAGE = "dev.achmad.finbox.extension.generated"
private const val GENERATED_CLASS = "GeneratedParser"

/**
 * Re-exports the single `@Parser` parser in an extension module under a fixed,
 * predictable name.
 *
 * The indirection is what lets `finbox { }` drop `className`: the manifest can
 * name [GENERATED_CLASS] unconditionally, because this processor guarantees one
 * exists. It also moves what used to be a runtime failure — a typo'd class name
 * surfacing as "Failed to instantiate" on the user's phone — to a build error.
 */
class ParserProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private var invoked = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked) return emptyList()
        invoked = true

        val annotated = resolver.getSymbolsWithAnnotation(PARSER_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        val decl = when {
            annotated.isEmpty() -> {
                logger.error(
                    "No @Parser class found. Annotate this extension's entry point with " +
                        "@dev.achmad.finbox.extension.Parser.",
                )
                return emptyList()
            }
            annotated.size > 1 -> {
                val names = annotated.joinToString { it.qualifiedName?.asString().orEmpty() }
                logger.error(
                    "Exactly one @Parser class is allowed per extension, found ${annotated.size}: $names.",
                    annotated.first(),
                )
                return emptyList()
            }
            else -> annotated.single()
        }

        val isParser = decl.getAllSuperTypes()
            .any { it.declaration.qualifiedName?.asString() == TRANSACTION_PARSER }
        if (!isParser) {
            logger.error("@Parser class must implement TransactionParser.", decl)
            return emptyList()
        }
        if (decl.classKind != ClassKind.CLASS && decl.classKind != ClassKind.OBJECT) {
            logger.error("@Parser must be on a class or object, not a ${decl.classKind}.", decl)
            return emptyList()
        }
        if (Modifier.ABSTRACT in decl.modifiers) {
            logger.error("@Parser class must be concrete; the app instantiates it reflectively.", decl)
            return emptyList()
        }
        if (decl.primaryConstructor?.parameters?.isNotEmpty() == true) {
            logger.error("@Parser class must have a no-argument constructor.", decl)
            return emptyList()
        }

        val fqn = decl.qualifiedName?.asString() ?: run {
            logger.error("@Parser class must be top-level and named.", decl)
            return emptyList()
        }

        // An object is referenced directly; a class is constructed.
        val instance = if (decl.classKind == ClassKind.OBJECT) fqn else "$fqn()"

        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = false, decl.containingFile!!),
            packageName = GENERATED_PACKAGE,
            fileName = GENERATED_CLASS,
        ).bufferedWriter().use { out ->
            out.write(
                """
                |// Generated from @Parser on $fqn. Do not edit.
                |package $GENERATED_PACKAGE
                |
                |import dev.achmad.finbox.extension.TransactionParser
                |
                |class $GENERATED_CLASS : TransactionParser by $instance
                |
                """.trimMargin(),
            )
        }

        return emptyList()
    }
}

class ParserProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        ParserProcessor(environment.codeGenerator, environment.logger)
}
