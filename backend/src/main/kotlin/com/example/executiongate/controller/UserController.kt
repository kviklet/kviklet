package com.example.executiongate.controller

import com.example.executiongate.db.User
import com.example.executiongate.db.UserAdapter
import com.example.executiongate.db.UserEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import javax.validation.Valid

import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

data class CreateUserRequest(
        @field:NotBlank
        @field:Size(min = 3, max = 50)
        val email: String,

        @field:NotBlank
        @field:Size(min = 6, max = 50)
        val password: String,

        @field:NotBlank
        @field:Size(min = 1, max = 50)
        val fullName: String
)

data class UserResponse(
        val id: String,
        val email: String,
        val fullName: String?
) {
    constructor(user: User) : this(
            id = user.id,
            email = user.email,
            fullName = user.fullName
    )
}

data class UsersResponse(
        val users: List<UserResponse>
) {
    companion object {
        fun fromUsers(users: List<User>): UsersResponse {
            return UsersResponse(users.map { UserResponse(it) })
        }
    }
}


@RestController()
@Validated
@RequestMapping("/users")
class UserController(
    private val userAdapter: UserAdapter,
    private val passwordEncoder: PasswordEncoder
) {

    @PostMapping("/")
    fun createUser(@RequestBody @Valid userRequest: CreateUserRequest): UserResponse {
        val user = User(email = userRequest.email, fullName = userRequest.fullName, password = passwordEncoder.encode(userRequest.password))
        return UserResponse(userAdapter.createUser(user))
    }

    @GetMapping("/")
    fun getUsers(): UsersResponse {
        return UsersResponse.fromUsers(userAdapter.listUsers())
    }
}
