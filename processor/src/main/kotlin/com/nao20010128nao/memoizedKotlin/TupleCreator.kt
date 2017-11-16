package com.nao20010128nao.memoizedKotlin

import com.squareup.kotlinpoet.*
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import java.util.ArrayList

const val CHARACTER_COUNT = 26

class TupleCreator : AbstractProcessor() {
    private val cachedLowerCaseNames: MutableList<String> = ArrayList()

    override fun process(annotations: MutableSet<out TypeElement>, round: RoundEnvironment): Boolean {
        val tuplesToMake:MutableSet<Int> = hashSetOf()
        round.getElementsAnnotatedWith(KotlinMemoized::class.java)
                .filter { it.kind== ElementKind.METHOD }
                .filter { it is ExecutableElement }
                .map { it as ExecutableElement }
                .forEach {
                    val paramsLen=it.parameters.size
                    if(Modifier.STATIC in it.modifiers){
                        throw UnsupportedOperationException("KotlinMemoized for static (companion) methods is currently unsupported.")
                    }else{
                        tuplesToMake.add(paramsLen)
                    }
                }
        tuplesToMake.filter { it>4 }.sorted().forEach {
            val typeVariable=(1..it).map { TypeVariableName(name(it-1).toUpperCase()) }
            val args=(1..it).map { ParameterSpec.builder(name(it-1).toLowerCase(),typeVariable[it-1]).build() }
            val poem= FileSpec.builder("com.nao20010128nao.memoizedKotlin","Tuple$it.kt")
                    .addType(
                    TypeSpec.classBuilder("Tuple$it")
                            .addModifiers(KModifier.DATA)
                            .addTypeVariables(typeVariable)
                            .primaryConstructor(
                                    FunSpec.constructorBuilder()
                                            .addParameters(args)
                                            .build()
                            )
                            .build()
                    )
                    .build()
            val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
            poem.writeTo(File(kaptKotlinGeneratedDir, "Tuple$it.kt"))
        }
        return true
    }

    private fun name(index: Int): String {
        if (index < cachedLowerCaseNames.size) {
            return cachedLowerCaseNames[index]
        }

        val name = newName(index)
        cachedLowerCaseNames.add(index, name)

        return name
    }


    private fun newName(index: Int): String {
        val baseIndex = index / CHARACTER_COUNT
        val offset = index % CHARACTER_COUNT

        val newChar = charAt(offset)

        return if (baseIndex == 0)
            String(charArrayOf(newChar))
        else
            name(baseIndex - 1) + newChar
    }

    private fun charAt(index: Int): Char = 'a' + index

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
    }
}