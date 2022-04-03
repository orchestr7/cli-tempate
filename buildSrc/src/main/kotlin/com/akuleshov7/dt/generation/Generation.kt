/**
 * This file contains code for codegen: generating a list of options for config files and README.
 */

@file:Suppress("FILE_NAME_MATCH_CLASS")

package com.akuleshov7.dt.generation

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import org.gradle.api.Project

import java.io.BufferedReader
import java.io.File

/**
 * The comment that will be added to the generated sources file.
 */
private val autoGenerationComment =
        """
            | ---------------------------------------------------------------------
            | ******* This file was auto generated, please don't modify it. *******
            | ---------------------------------------------------------------------
        """.trimMargin()

/**
 * This class represents the general form of each key in json file with config options
 * @property argType Type which will be used by ArgParser
 * @property kotlinType Type which will be used in kotlin code
 * @property fullName Full name of option for usage in dt cli
 * @property shortName Short name of option for usage in dt cli
 * @property description Option description
 * @property default default value for option
 */
@Suppress(
    "USE_DATA_CLASS",
    "MISSING_KDOC_CLASS_ELEMENTS",
    "KDOC_NO_CLASS_BODY_PROPERTIES_IN_HEADER")
class Option {
    lateinit var argType: String
    lateinit var kotlinType: String
    lateinit var fullName: String
    lateinit var shortName: String
    lateinit var description: String
    lateinit var default: String
}

/**
 * Paths, where to store generated files
 */
fun Project.generatedOptionsTablePath() = "$rootDir/OptionsTable.md"

/**
 * Path to config file
 */
fun Project.configFilePath() = "$rootDir/buildSrc/src/main/resources/config-options.json"

/**
 * Generate options for ArgParser
 *
 * @param jsonObject map of cli option names to [Option] objects
 * @return a corresponding [FunSpec.Builder]
 */
@Suppress("TOO_MANY_LINES_IN_LAMBDA")
fun FunSpec.Builder.generateOptions(jsonObject: Map<String, Option>): FunSpec.Builder {
    jsonObject.forEach {
        val option = StringBuilder().apply {
            append("val ${it.key} by parser.option(\n")
            append("${it.value.argType},\n")
            append("fullName = \"${it.value.fullName}\",\n")
            if (it.value.shortName.isNotEmpty()) {
                append("shortName = \"${it.value.shortName}\",\n")
            }
            // We replace whitespaces to `·`, in aim to avoid incorrect line breaking,
            // which could be done by kotlinpoet (see https://github.com/square/kotlinpoet/issues/598)
            append("description = \"${it.value.description.replace(" ", "·")}\"\n")
            append(")\n")
        }
            .toString()
        this.addStatement(option)
    }
    return this
}

/**
 * Assign class members to options
 *
 * @param jsonObject map of cli option names to [Option] objects
 * @return a corresponding [FunSpec.Builder]
 */
fun FunSpec.Builder.assignMembersToOptions(jsonObject: Map<String, Option>): FunSpec.Builder {
    jsonObject.forEach {
        val assign = "this.${it.key} = ${it.key}"
        this.addStatement(assign)
    }
    return this
}

/**
 * General function for auto generation of config options and readme table
 *
 * @param destination a destination file for generated code
 */
@Suppress("EMPTY_BLOCK_STRUCTURE_ERROR")
fun Project.generateConfigOptions(destination: File) {
    val configFile = configFilePath()
    val gson = Gson()
    val bufferedReader: BufferedReader = File(configFile).bufferedReader()
    val jsonString = bufferedReader.use { it.readText() }
    val jsonObject: Map<String, Option> = gson.fromJson(jsonString, object : TypeToken<Map<String, Option>>() {}.type)
    generateProperties(jsonObject, destination)
    generateReadme(jsonObject, File(generatedOptionsTablePath()))
}

/**
 * Generate DtProperties class which represents configuration properties of Dt application
 *
 * @param jsonObject map of cli option names to [Option] objects
 * @param destination
 */
fun generateProperties(jsonObject: Map<String, Option>, destination: File) {
    val builder = FileSpec.builder("com.akuleshov7.dt.common.config", "DtProperties")
    builder.addComment(autoGenerationComment)
    builder.addImport("kotlinx.cli", "ArgParser")
    builder.addImport("kotlinx.cli", "ArgType")
    builder.addImport("kotlinx.cli", "optional")
    builder.addImport("kotlinx.cli", "vararg")
    val classBuilder = generatePropertiesClass(jsonObject)
    val mergeFunc = generateMergeConfigFunc(jsonObject)
    classBuilder.addFunction(mergeFunc.build())
    builder.addType(classBuilder.build())
    builder.addFunction(generateDefaultConfig(jsonObject).build())
    builder.indent("    ")
    destination.writeText(builder.build().toString())
}

/**
 * Generate constructors for DtProperties class
 *
 * @param jsonObject map of cli option names to [Option] objects
 * @return a corresponding [TypeSpec.Builder]
 */
@Suppress("TOO_LONG_FUNCTION")
fun generatePropertiesClass(jsonObject: Map<String, Option>): TypeSpec.Builder {
    val classBuilder = TypeSpec.classBuilder("AppProperties").addModifiers(KModifier.DATA)
    val properties = jsonObject.entries.joinToString("\n") { "@property ${it.key} ${it.value.description}" }
    val kdoc = """
               |Configuration properties of dt application, retrieved either from properties file
               |or from CLI args.
               |$properties
               """.trimMargin()
    classBuilder.addKdoc(kdoc)
    classBuilder.addAnnotation(AnnotationSpec.builder(ClassName("kotlinx.serialization", "Serializable")).build())

    // Generate primary ctor
    val primaryCtor = FunSpec.constructorBuilder()
    for ((name, value) in jsonObject) {
        primaryCtor.addParameter(ParameterSpec.builder(name, createClassName(value.kotlinType).copy(nullable = true))
            .defaultValue("null")
            .build())
        val property = PropertySpec.builder(name, createClassName(value.kotlinType).copy(nullable = true))
            .initializer(name)
            .mutable()
        classBuilder.addProperty(property.build())
    }
    primaryCtor.addParameter(
        ParameterSpec.builder(
            "testFiles", ClassName("kotlin.collections", "List")
                .parameterizedBy(ClassName("kotlin", "String")).copy(nullable = true)
        )
            .defaultValue("null")
            .build()
    )
    val property = PropertySpec.builder("testFiles", ClassName("kotlin.collections", "List")
        .parameterizedBy(ClassName("kotlin", "String")).copy(nullable = true)
    )
        .initializer("testFiles")
        .mutable()
    classBuilder.addProperty(property.build())
    classBuilder.primaryConstructor(primaryCtor.build())

    // Generate secondary ctor
    val secondaryCtor = FunSpec.constructorBuilder()
    secondaryCtor.addParameter("args", ClassName("kotlin", "Array")
        .parameterizedBy(ClassName("kotlin", "String")))
    secondaryCtor.callThisConstructor()
    secondaryCtor.addStatement("val parser = ArgParser(\"dt\")")
        .generateOptions(jsonObject)
        .addStatement("parser.parse(args)")
        .assignMembersToOptions(jsonObject)
    classBuilder.addFunction(secondaryCtor.build())

    return classBuilder
}

/**
 * Create ClassName object from string, which represents generic kotlin type
 *
 * @param type kotlin type
 * @return corresponding ClassName object
 */
fun createClassName(type: String): TypeName {
    if (!type.contains("<")) {
        return extractClassNameFromString(type)
    }
    val packageName = type.substringBefore("<")
    val simpleName = type.substringAfter("<").substringBeforeLast(">")
    return extractClassNameFromString(packageName).parameterizedBy(createClassName(simpleName))
}

/**
 * Create ClassName object from string, which represents simple kotlin type
 *
 * @param type kotlin type
 * @return corresponding ClassName object
 */
fun extractClassNameFromString(type: String) = ClassName(type.substringBeforeLast("."), type.substringAfterLast("."))

/**
 * Create the instance with default field values, in aim to use them if no options provided neither from CLI nor from dt.properties
 *
 * @param jsonObject map of cli option names to [Option] objects
 * @return function which returns instance of DtProperties with default values
 */
fun generateDefaultConfig(jsonObject: Map<String, Option>): FunSpec.Builder {
    val defaultFields = jsonObject.map { (name, value) ->
        if (value.kotlinType == "kotlin.String" && value.default != "null") {
            "    $name = \"${value.default}\","
        } else {
            "    $name = ${value.default},"
        }
    }.joinToString("\n") + "\ntestFiles = emptyList()\n"

    // bad indent https://github.com/square/kotlinpoet/issues/415, failed to use `%[` and `%]` for now
    return FunSpec.builder("defaultConfig")
        .addStatement(
            "return AppProperties( \n" +
                    defaultFields +
                    ")"
        )
}

/**
 * Generate function, which will merge cli config options and options from property file
 *
 * @param jsonObject map of cli option names to [Option] objects
 * @return a corresponding [FunSpec.Builder]
 */
fun generateMergeConfigFunc(jsonObject: Map<String, Option>): FunSpec.Builder {
    val kdoc =
            """                
                |@param configFromPropertiesFile - config that will be used as a fallback in case when the field was not provided
                |@return this configuration
            """.trimMargin()
    val mergeFunc = FunSpec.builder("mergeConfigWithPriorityToThis")
        .addKdoc(kdoc)
        .addParameter("configFromPropertiesFile", ClassName("com.akuleshov7.dt.common.config", "AppProperties"))
        .returns(ClassName("com.akuleshov7.dt.common.config", "AppProperties"))
        .addStatement("val defaultConfig = defaultConfig()")
    val statements = jsonObject.entries.joinToString("\n") {
        "${it.key} = ${it.key} ?: configFromPropertiesFile.${it.key} ?: defaultConfig.${it.key}".replace(" ", "·")
    }
    mergeFunc.addStatement(statements)
    mergeFunc.addStatement("return this")
    return mergeFunc
}

/**
 * Generate readme table from json object
 *
 * @param jsonObject map of cli option names to [Option] objects
 * @param destination a destination file to write the table into
 */
@Suppress("TOO_MANY_LINES_IN_LAMBDA")
fun generateReadme(jsonObject: Map<String, Option>, destination: File) {
    var readmeContent =
            """
                |Most (except for `-h` and `-prop`) of the options below can be passed to a Dependecy-Tree via `dt.properties` file
                |
                || Short name | Long name  | Description   | Default |
                ||------------|------------|---------------|---------------|
                || h | help | Usage info | - |
            """.trimMargin()
    jsonObject.forEach {
        val shortName = if (it.value.shortName.isNotEmpty()) it.value.shortName else "-"
        val longName = it.value.fullName
        val description = it.value.description
        var default = it.value.default
        // If some option have user defined type, then we will print to the README
        // only the value (e.g. LanguageType.UNDEFINED --> UNDEFINED)
        if (default != "null") {
            if (it.value.kotlinType != "kotlin.String") {
                default = default.substringAfterLast(".")
            }
        } else {
            default = "-"
        }
        readmeContent += "\n| $shortName | $longName | $description | $default |"
    }
    destination.writeText(readmeContent)
}
