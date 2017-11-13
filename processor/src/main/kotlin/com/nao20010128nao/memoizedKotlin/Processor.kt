package com.nao20010128nao.memoizedKotlin

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.type.*


class Processor: AbstractProcessor() {
    override fun process(annotations: MutableSet<out TypeElement>, round: RoundEnvironment): Boolean {
        val poet=FileSpec.builder("","")
        round.getElementsAnnotatedWith(KotlinMemoized::class.java)
                .filter { it.kind==ElementKind.METHOD }
                .filter { it is ExecutableElement }
                .map { it as ExecutableElement }
                .forEach {
                    val annotation=it.getAnnotation(KotlinMemoized::class.java)
                    val willBe=convertNameToMemoizedName(it.simpleName.toString(),annotation.value)
                    if(Modifier.STATIC in it.modifiers){
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
                        poet.addFunction(FunSpec.builder(willBe)
                                .apply{jvmModifiers(it.modifiers - Modifier.ABSTRACT)}
                                .build())
                    }else{
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
                        poet.addFunction(FunSpec.builder(willBe)
                                .apply{jvmModifiers(it.modifiers - Modifier.ABSTRACT)}
                                .build())
                    }
                }
        return true
    }


    // https://github.com/nao20010128nao/Confuser/blob/master/processor/src/main/java/com/nao20010128nao/confuser/ConfusionProcessor.java#L250
    fun typeMirrorToClassName(input: TypeMirror): String = when (input) {
        is ArrayType -> "Array<"+typeMirrorToClassName(input.componentType)+">"
        is DeclaredType -> when(input.typeArguments.size) {
            0 -> input.asElement().toString()
            else -> input.asElement().toString()+"<"+ input.typeArguments.joinToString { typeMirrorToClassName(it) } +">"
        }
        is PrimitiveType -> when(input.toString()){
            "int"->"Int"
            "long"->"Long"
            "short"->"Short"
            "byte"->"Byte"
            "float"->"Float"
            "double"->"double"
            "char"->"Char"
            else -> input.toString()
        }
        is NoType -> "Unit"
        is WildcardType->""
        else -> "Nothing"
    }

    /**
     * (specified by annotation): (specified by annotation)
     * _methodName: methodName
     * methodName: memoizedMethodName
     */
    fun convertNameToMemoizedName(txt: String,specified: String):String = when {
        specified!="" -> specified
        txt.startsWith("_") -> txt[1].toLowerCase().toString()+txt.substring(2)
        else -> "memoized"+txt[0].toUpperCase().toString()+txt.substring(1)
    }

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
        fun Modifier.text(){

        }
    }
}
