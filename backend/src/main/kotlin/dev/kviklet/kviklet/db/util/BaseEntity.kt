package dev.kviklet.kviklet.db.util

import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.GenericGenerator
import org.springframework.data.util.ProxyUtils

@MappedSuperclass
open class BaseEntity {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "dev.kviklet.kviklet.db.util.IdGenerator")
    var id: String? = null

    override fun equals(other: Any?): Boolean {
        other ?: return false

        if (this === other) return true

        if (javaClass != ProxyUtils.getUserClass(other)) return false

        other as BaseEntity

        return if (null == this.id) false else this.id == other.id
    }

    override fun hashCode(): Int = 31

    override fun toString() = "Entity of type ${this.javaClass.name} with id: $id"
}
