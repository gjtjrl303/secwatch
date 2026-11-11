package com.example.secwatch.common

class SecwatchException(val errorCode: ErrorCode, cause: Throwable? = null) : RuntimeException(errorCode.message, cause)
