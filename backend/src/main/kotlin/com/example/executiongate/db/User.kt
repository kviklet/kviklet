package com.example.executiongate.db

import com.example.executiongate.db.util.BaseEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import javax.persistence.*

@Entity
@Table(name = "users")
data class UserEntity(
        @Column(nullable = true)
        var fullName: String? = null,

        @Column(nullable = true)
        var password: String? = null,

        @Column(unique = true)
        var googleId: String? = null,

        @Column(unique = true)
        var email: String = ""
): BaseEntity(){
    fun toDto() = User(
        id = id,
        fullName = fullName,
        password = password,
        googleId = googleId,
        email = email
    )
}

// A dto for the UserEntity
data class User(
        val id: String = "",
        val fullName: String? = null,
        val password: String? = null,
        val googleId: String? = null,
        val email: String = ""
)
interface UserRepository : JpaRepository<UserEntity, String> {
    fun findByEmail(email: String): UserEntity?
    fun findByGoogleId(googleId: String): UserEntity?
}

@Service
class UserAdapter(
    private val userRepository: UserRepository,
) {
    fun findByEmail(email: String): User? {
        val userEntity = userRepository.findByEmail(email) ?: return null
        return userEntity.toDto()
    }
    fun findByGoogleId(googleId: String): User? {
        val userEntity = userRepository.findByGoogleId(googleId) ?: return null
        return userEntity.toDto()
    }

    fun createUser(user: User): User {
        val userEntity = UserEntity(
            fullName = user.fullName,
            password = user.password,
            googleId = user.googleId,
            email = user.email
        )
        val savedUserEntity = userRepository.save(userEntity)
        return savedUserEntity.toDto()
    }

    fun createOrUpdateUser(user: User): User {
        val userEntity = userRepository.findByIdOrNull(user.id)

        if (userEntity == null) {
            return createUser(user)
        }
        else {
            userEntity.fullName = user.fullName
            userEntity.password = user.password
            userEntity.googleId = user.googleId
            userEntity.email = user.email
            val savedUserEntity = userRepository.save(userEntity)
            return savedUserEntity.toDto()
        }
    }

    fun listUsers(): List<User> {
        val userEntities = userRepository.findAll()
        return userEntities.map { it.toDto() }
    }
}