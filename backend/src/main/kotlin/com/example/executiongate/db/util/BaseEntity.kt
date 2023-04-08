package com.example.executiongate.db.util

import org.hibernate.annotations.GenericGenerator
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.MappedSuperclass

@MappedSuperclass
open class BaseEntity {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "com.example.executiongate.db.util.IdGenerator")
    val id: String = ""
}
