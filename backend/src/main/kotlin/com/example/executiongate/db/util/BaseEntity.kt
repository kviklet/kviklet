package com.example.executiongate.db.util

import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.GenericGenerator

@MappedSuperclass
open class BaseEntity {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "com.example.executiongate.db.util.IdGenerator")
    open val id: String = ""
}
