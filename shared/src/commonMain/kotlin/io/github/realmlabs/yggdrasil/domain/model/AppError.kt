package io.github.realmlabs.yggdrasil.domain.model

sealed interface AppError {
    val message: String
    val cause: String?

    data class Validation(
        override val message: String,
        override val cause: String? = null,
    ) : AppError

    data class Connection(
        override val message: String,
        override val cause: String? = null,
    ) : AppError

    data class Storage(
        override val message: String,
        override val cause: String? = null,
    ) : AppError

    data class ZooKeeper(
        override val message: String,
        override val cause: String? = null,
    ) : AppError

    data class Unknown(
        override val message: String,
        override val cause: String? = null,
    ) : AppError
}
