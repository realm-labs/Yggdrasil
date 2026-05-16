package io.github.realmlabs.yggdrasil.domain.model

class ZNodePath private constructor(val value: String) {
    val name: String
        get() = if (value == RootValue) RootValue else value.substringAfterLast("/")

    val parent: ZNodePath?
        get() = when {
            value == RootValue -> null
            value.lastIndexOf("/") == 0 -> Root
            else -> ZNodePath(value.substringBeforeLast("/"))
        }

    fun child(name: String): ZNodePath {
        require(isValidSegment(name)) { "Invalid znode name: $name" }
        return if (value == RootValue) ZNodePath("/$name") else ZNodePath("$value/$name")
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is ZNodePath && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        private const val RootValue = "/"

        val Root = ZNodePath(RootValue)

        fun parse(raw: String): OperationResult<ZNodePath> {
            val normalized = raw.trim()
            return if (isValid(normalized)) {
                OperationResult.Success(ZNodePath(normalized))
            } else {
                OperationResult.Failure(AppError.Validation("Invalid ZooKeeper path: $raw"))
            }
        }

        fun requireValid(raw: String): ZNodePath =
            when (val result = parse(raw)) {
                is OperationResult.Success -> result.value
                is OperationResult.Failure -> throw IllegalArgumentException(result.error.message)
            }

        fun isValid(raw: String): Boolean {
            if (raw.isBlank()) return false
            if (!raw.startsWith("/")) return false
            if (raw == RootValue) return true
            if (raw.length > 1 && raw.endsWith("/")) return false
            if (raw.contains("//")) return false
            if (raw.any { it.code == 0 }) return false

            val segments = raw.split("/").drop(1)
            return segments.all(::isValidSegment)
        }

        private fun isValidSegment(segment: String): Boolean =
            segment.isNotBlank() && segment != "." && segment != ".."
    }
}
