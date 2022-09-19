/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType

fun KtAnalysisSession.isNullableAnyType(type: KtType) = type.isAny && type.isMarkedNullable

fun KtAnalysisSession.isNotNullableBooleanType(type: KtType) = type.isBoolean && !type.isMarkedNullable

fun KtAnalysisSession.matchesEqualsMethodSignature(function: KtFunctionSymbol): Boolean {
    if (function.typeParameters.isNotEmpty()) return false
    val param = function.valueParameters.singleOrNull() ?: return false
    val paramType = param.returnType
    val returnType = function.returnType
    return isNullableAnyType(paramType) && isNotNullableBooleanType(returnType)
}

fun KtAnalysisSession.matchesHashCodeMethodSignature(function: KtFunctionSymbol): Boolean {
    if (function.typeParameters.isNotEmpty()) return false
    if (function.valueParameters.isNotEmpty()) return false
    val returnType = function.returnType
    return returnType.isInt && !returnType.isMarkedNullable
}