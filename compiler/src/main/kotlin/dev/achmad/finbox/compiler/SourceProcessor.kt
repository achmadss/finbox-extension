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

private const val SOURCE_ANNOTATION = "dev.achmad.finbox.extension.Source"
private const val TRANSACTION_SOURCE = "dev.achmad.finbox.extension.TransactionSource"
private const val SOURCE_FACTORY = "dev.achmad.finbox.extension.SourceFactory"

/** Must match GENERATED_FACTORY in FinboxExtensionPlugin, which puts it in the manifest. */
private const val GENERATED_PACKAGE = "dev.achmad.finbox.extension.generated"
private const val GENERATED_CLASS = "GeneratedSourceFactory"

/**
 * Turns the single `@Source` class in an extension module into a
 * [SOURCE_FACTORY] with a fixed, predictable name.
 *
 * The indirection is what lets `finbox { }` drop `className`: the manifest can
 * name [GENERATED_CLASS] unconditionally, because this processor guarantees one
 * exists. It also moves what used to be a runtime failure — a typo'd class name
 * surfacing as "Failed to instantiate" on the user's phone — to a build error.
 */
class SourceProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private var invoked = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked) return emptyList()
        invoked = true

        val annotated = resolver.getSymbolsWithAnnotation(SOURCE_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        val source = when {
            annotated.isEmpty() -> {
                logger.error(
                    "No @Source class found. Annotate this extension's entry point with " +
                        "@dev.achmad.finbox.extension.Source.",
                )
                return emptyList()
            }
            annotated.size > 1 -> {
                val names = annotated.joinToString { it.qualifiedName?.asString().orEmpty() }
                logger.error(
                    "Exactly one @Source class is allowed per extension, found ${annotated.size}: $names. " +
                        "Expose several parsers from one APK by implementing SourceFactory instead.",
                    annotated.first(),
                )
                return emptyList()
            }
            else -> annotated.single()
        }

        val superTypes = source.getAllSuperTypes()
            .mapNotNull { it.declaration.qualifiedName?.asString() }
            .toSet()
        val isFactory = SOURCE_FACTORY in superTypes
        val isSource = TRANSACTION_SOURCE in superTypes

        if (!isFactory && !isSource) {
            logger.error("@Source class must implement TransactionSource or SourceFactory.", source)
            return emptyList()
        }
        if (source.classKind != ClassKind.CLASS && source.classKind != ClassKind.OBJECT) {
            logger.error("@Source must be on a class or object, not a ${source.classKind}.", source)
            return emptyList()
        }
        if (Modifier.ABSTRACT in source.modifiers) {
            logger.error("@Source class must be concrete; the app instantiates it reflectively.", source)
            return emptyList()
        }
        if (source.primaryConstructor?.parameters?.isNotEmpty() == true) {
            logger.error("@Source class must have a no-argument constructor.", source)
            return emptyList()
        }

        val fqn = source.qualifiedName?.asString() ?: run {
            logger.error("@Source class must be top-level and named.", source)
            return emptyList()
        }

        // An object is referenced directly; a class is constructed.
        val instance = if (source.classKind == ClassKind.OBJECT) fqn else "$fqn()"
        val sources = if (isFactory) "$instance.createSources()" else "listOf($instance)"

        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = false, source.containingFile!!),
            packageName = GENERATED_PACKAGE,
            fileName = GENERATED_CLASS,
        ).bufferedWriter().use { out ->
            out.write(
                """
                |// Generated from @Source on $fqn. Do not edit.
                |package $GENERATED_PACKAGE
                |
                |import dev.achmad.finbox.extension.SourceFactory
                |import dev.achmad.finbox.extension.TransactionSource
                |
                |class $GENERATED_CLASS : SourceFactory {
                |    override fun createSources(): List<TransactionSource> = $sources
                |}
                |
                """.trimMargin(),
            )
        }

        return emptyList()
    }
}

class SourceProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        SourceProcessor(environment.codeGenerator, environment.logger)
}
