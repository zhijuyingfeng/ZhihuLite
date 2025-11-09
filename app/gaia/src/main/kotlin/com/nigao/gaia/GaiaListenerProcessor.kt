package com.nigao.gaia

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.UNIT

class GaiaListenerProcessor(
    private val codeGenerator: CodeGenerator
): SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation("com.nigao.gaia.GaiaListen")
        val ret = symbols.filter { it !is KSFunctionDeclaration }.toList()

        val functionsByKey = mutableMapOf<String, MutableList<KSFunctionDeclaration>>()

        symbols.filterIsInstance<KSFunctionDeclaration>().forEach { function ->
            val key = function.getGaiaKey()
            functionsByKey.getOrPut(key) { mutableListOf() }.add(function)
        }

        if (functionsByKey.isNotEmpty()) {
            generateRegistryClass(functionsByKey)
        }
        return ret
    }

    private fun generateRegistryClass(functionsByKey: Map<String, MutableList<KSFunctionDeclaration>>) {
        val fileSpec = FileSpec.builder("com.nigao.gaia", "GaiaListenersRegistry")
            .addFileComment("Auto-generated timing listeners registry - DO NOT EDIT")

        functionsByKey.values.flatten()
            .filter { it.functionKind == FunctionKind.TOP_LEVEL }
            .forEach { function ->
                val packageName = function.packageName.asString()
                fileSpec.addImport(packageName, function.simpleName.asString())
            }

        val registerAllFunction = FunSpec.builder("registerAll").addModifiers(KModifier.PUBLIC)

        functionsByKey.forEach { key, functions ->
            addRegisterStatements(registerAllFunction, key, functions)
        }

        fileSpec.addFunction(registerAllFunction.build())

        val file = codeGenerator.createNewFile(Dependencies.ALL_FILES, "com.nigao.gaia", "GaiaListenersRegistry")
        file.write(fileSpec.build().toString().toByteArray())
    }

    private fun addRegisterStatements(builder: FunSpec.Builder, key: String, functions: List<KSFunctionDeclaration>) {
        builder.addComment("Listeners for Gaia key: $key")

        functions.forEach {
            val functionName = it.simpleName.asString()
            if (it.functionKind == FunctionKind.TOP_LEVEL) {
                if (it.hasGaiaEvent()) {
                    builder.addStatement("GaiaEventManager.registerEventObserver(%S) { event -> %L(event) }", key, functionName)
                } else {
                    builder.addStatement("GaiaEventManager.registerEventObserver(%S) { event -> %L() }", key, functionName)
                }
            } else {
                val parentClass = it.parent as KSClassDeclaration
                val parentClassName = parentClass.qualifiedName?.asString() ?: ""
                if (it.hasGaiaEvent()) {
                    builder.addStatement("GaiaEventManager.registerEventObserver(%S) { event -> %T.%L(event) }", key,
                        ClassName.bestGuess(parentClassName), functionName)
                } else {
                    builder.addStatement("GaiaEventManager.registerEventObserver(%S) { event -> %T.%L() }", key,
                        ClassName.bestGuess(parentClassName), functionName)
                }
            }
        }
    }
}


private fun KSFunctionDeclaration.getGaiaKey(): String {
    return annotations
        .first { it.annotationType.resolve().declaration.simpleName.asString() == "GaiaListen" }
        .arguments
        .first { it.name?.asString() == "key" }
        .value as String
}

private fun KSFunctionDeclaration.hasGaiaEvent(): Boolean {
    return parameters.any { param ->
        param.type.resolve().declaration.qualifiedName?.asString() == "com.nigao.gaia.GaiaEvent"
    }
}

class GaiaListenerProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return GaiaListenerProcessor(codeGenerator = environment.codeGenerator)
    }
}