package com.example.executiongate.db.util

import org.hibernate.annotations.GenericGenerator
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass

@MappedSuperclass
open class BaseEntity {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "com.example.executiongate.db.util.IdGenerator")
    open val id: String = ""
}
