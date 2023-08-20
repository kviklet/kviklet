package com.example.executiongate

import com.example.executiongate.service.dto.DatasourceConnection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.acls.domain.ObjectIdentityImpl
import org.springframework.security.acls.model.AclService
import org.springframework.security.acls.model.MutableAcl
import org.springframework.security.acls.model.MutableAclService
import org.springframework.security.acls.model.ObjectIdentity
import org.springframework.test.web.servlet.MockMvc


@SpringBootTest
@AutoConfigureMockMvc
class AclPlayground(
    @Autowired val mockMvc: MockMvc,
    @Autowired val mutableAclService: MutableAclService,
    @Autowired val aclService: AclService,
) {

    @AfterEach
    fun tearDown() {
    }

    @Test
    fun test1() {
        val oi: ObjectIdentity = ObjectIdentityImpl(DatasourceConnection::class.java, 123)
        val acl: MutableAcl = mutableAclService.createAcl(oi)



    }

}
