package com.example.secwatch.common

class SecwatchException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)
