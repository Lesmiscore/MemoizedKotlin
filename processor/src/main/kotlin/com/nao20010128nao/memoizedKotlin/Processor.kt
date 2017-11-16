package com.nao20010128nao.memoizedKotlin

import com.squareup.kotlinpoet.*
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.type.*
import kotlin.reflect.KClass


class Processor : AbstractProcessor() {
    override fun process(annotations: MutableSet<out TypeElement>, round: RoundEnvironment): Boolean {
        val ktPoem = FileSpec.builder("", "MemoizedKotlin.kt")
        val tuplesToMake: MutableSet<Int> = hashSetOf()
        round.getElementsAnnotatedWith(KotlinMemoized::class.java)
                .filter { it.kind == ElementKind.METHOD }
                .filter { it is ExecutableElement }
                .map { it as ExecutableElement }
                .forEach {
                    val paramsLen = it.parameters.size
                    val annotation = it.getAnnotation(KotlinMemoized::class.java)
                    val willBe = convertNameToMemoizedName(it.simpleName.toString(), annotation.value)
                    if (Modifier.STATIC in it.modifiers) {
                        /*
                         * method is static
                         * H          is the type which the target method belongs to
                         * methodName is the target method
                         * T          is the return type
                         * Tuple      is one of Single, Pair, Triple
                         * */
                        /*
                         * private val methodNameCache:MutableMap<Tuple<...>,T> = hashMapOf()
                         * [modifiers] fun H.Companion.methodName([params]): T =
                         *    methodNameCache.getOrPut(Tuple([params])){ H._methodName([params]) }
                         * */
                        throw UnsupportedOperationException("KotlinMemoized for static (companion) methods is currently unsupported.")
                    } else {
                        /*
                         * method is not static
                         * H          is the type which the target method belongs to
                         * methodName is the target method
                         * T          is the return type
                         * Tuple      is one of Single, Pair, Triple
                         * */
                        /*
                         * private val methodNameCache:MutableMap<Pair<H,Tuple<...>>,T> = hashMapOf()
                         * [modifiers] fun H.methodName([params]): T =
                         *    methodNameCache.getOrPut(this to Tuple([params])){ this._methodName([params]) }
                         * */
                        val tuple = toTupleClass(paramsLen)
                        tuplesToMake.add(paramsLen)
                        val retType = it.returnType.asTypeName()
                        if (paramsLen == 0) {
                            ktPoem.addProperty(
                                    PropertySpec.builder("${willBe}Cache", retType.asNullable())
                                            .addModifiers(KModifier.PRIVATE)
                                            .build()
                            )
                            ktPoem.addFunction(
                                    FunSpec.builder(willBe)
                                            .returns(retType)
                                            .apply { jvmModifiers(it.modifiers) }
                                            .addCode("if(${willBe}Cache == null){ ${willBe}Cache = $willBe() }")
                                            .addCode("return ${willBe}Cache" + if (retType.nullable) "" else "!!")
                                            .build()
                            )
                        } else {
                            ktPoem.addProperty(
                                    PropertySpec.builder("${willBe}Cache",
                                            ParameterizedTypeName.get(MutableMap::class.asClassName(),
                                                    ParameterizedTypeName.get(tuple, *it.parameters.map { it.asType().asTypeName() }.toTypedArray()),
                                                    retType
                                            ))
                                            .addModifiers(KModifier.PRIVATE)
                                            .initializer("hashMapOf()")
                                            .build()
                            )
                            val params = it.parameters.joinToString { it.simpleName }
                            ktPoem.addFunction(
                                    FunSpec.builder(willBe)
                                            .returns(retType)
                                            .apply { jvmModifiers(it.modifiers) }
                                            .addAnnotations(it.annotationMirrors.map { AnnotationSpec.Companion.get(it) })
                                            .addCode("return ${willBe}Cache.getOrPut(this to ${tuple.canonicalName}($params)) { this.${it.simpleName}($params) }")
                                            .addParameters(it.parameters.map { ParameterSpec.get(it) })
                                            .build()
                            )
                        }
                    }
                }
        val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
        ktPoem.build().writeTo(File(kaptKotlinGeneratedDir, "MemoizedKotlin.kt"))
        return true
    }

    /**
     * (specified by annotation): (specified by annotation)
     * _methodName: methodName
     * methodName: memoizedMethodName
     */
    fun convertNameToMemoizedName(txt: String, specified: String): String = when {
        specified != "" -> specified
        txt.startsWith("_") -> txt[1].toLowerCase().toString() + txt.substring(2)
        else -> "memoized" + txt[0].toUpperCase().toString() + txt.substring(1)
    }

    fun toTupleClass(num: Int): ClassName = when (num) {
        0 -> Nothing::class.asTypeName()
        1 -> Single::class.asTypeName()
        2 -> Pair::class.asTypeName()
        3 -> Triple::class.asTypeName()
        4 -> Quartet::class.asTypeName()
        else -> processingEnv.elementUtils.getTypeElement("com.nao20010128nao.memoizedKotlin.Tuple${num}Kt").asClassName()
    }

    fun jvmModifiers(modifiers: Iterable<Modifier>): Pair<List<KModifier>, List<KClass<out Annotation>>> {
        val visibility: MutableList<KModifier> = arrayListOf(KModifier.INTERNAL)
        val annotations: MutableList<KClass<out Annotation>> = arrayListOf()
        for (modifier in modifiers) {
            when (modifier) {
                Modifier.PUBLIC -> visibility[0] = KModifier.PUBLIC
                Modifier.PROTECTED -> visibility[0] = KModifier.PROTECTED
                Modifier.PRIVATE -> visibility[0] = KModifier.PRIVATE
                Modifier.ABSTRACT -> visibility.add(KModifier.ABSTRACT)
                Modifier.FINAL -> visibility.add(KModifier.FINAL)
                Modifier.NATIVE -> visibility.add(KModifier.EXTERNAL)
                Modifier.DEFAULT -> Unit
                Modifier.STATIC -> annotations.add(JvmStatic::class)
                Modifier.SYNCHRONIZED -> annotations.add(Synchronized::class)
                Modifier.STRICTFP -> annotations.add(Strictfp::class)
                else -> throw IllegalArgumentException("unexpected fun modifier $modifier")
            }
        }
        return visibility to annotations
    }

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
    }
}
