package io.github.realmlabs.yggdrasil.domain.model

sealed interface OperationResult<out T> {
    data class Success<T>(val value: T) : OperationResult<T>
    data class Failure(val error: AppError) : OperationResult<Nothing>
}

inline fun <T, R> OperationResult<T>.map(transform: (T) -> R): OperationResult<R> =
    when (this) {
        is OperationResult.Success -> OperationResult.Success(transform(value))
        is OperationResult.Failure -> this
    }
