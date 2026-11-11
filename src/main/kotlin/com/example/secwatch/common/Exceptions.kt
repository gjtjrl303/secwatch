package com.example.secwatch.common

class InvalidInputException(message: String) : RuntimeException(message)

class ConflictException(message: String) : RuntimeException(message)

class NotFoundException(message: String) : RuntimeException(message)

class UnsupportedFormatException(message: String) : RuntimeException(message)
