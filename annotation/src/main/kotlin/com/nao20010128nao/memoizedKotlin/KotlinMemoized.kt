package com.nao20010128nao.memoizedKotlin

@Target(AnnotationTarget.FUNCTION)
annotation class KotlinMemoized(
        /** Created method name */
        val value: String = "")
