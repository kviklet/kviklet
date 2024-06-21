package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.service.UserService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class CreateUserRequest(
    @field:NotBlank
    @field:Size(min = 3, max = 50)
    val email: String,

    @field:NotBlank
    @field:Size(min = 6, max = 50)
    val password: String,

    @field:NotBlank
    @field:Size(min = 1, max = 50)
    val fullName: String,
)

data class EditUserRequest(
    @field:Size(min = 3, max = 50)
    val email: String? = null,

    @field:Size(min = 1, max = 50)
    val fullName: String? = null,

    val roles: List<String>? = null,

    @field:Size(min = 6, max = 50)
    val password: String?,
)

data class UserResponse(val id: String, val email: String, val fullName: String?, val roles: List<RoleResponse>) {
    constructor(user: User) : this(
        id = user.getId()!!,
        email = user.email,
        fullName = user.fullName,
        roles = user.roles.map { RoleResponse.fromDto(it) },
    )
}

data class UsersResponse(val users: List<UserResponse>) {
    companion object {
        fun fromUsers(users: List<User>): UsersResponse = UsersResponse(users.map { UserResponse(it) })
    }
}

@RestController()
@Validated
@RequestMapping("/users")
class UserController(private val userService: UserService) {

    @PostMapping("/")
    fun createUser(
        @RequestBody @Valid
        userRequest: CreateUserRequest,
    ): UserResponse = UserResponse(
        userService.createUser(
            email = userRequest.email,
            password = userRequest.password,
            fullName = userRequest.fullName,
        ),
    )

    @GetMapping("/")
    fun getUsers(): UsersResponse {
        val users = userService.getUsers()
        return UsersResponse.fromUsers(users)
    }

    @PatchMapping("/{id}")
    fun patchUser(
        @PathVariable id: String,
        @RequestBody @Valid
        userRequest: EditUserRequest,
    ): UserResponse {
        if (userRequest.roles != null) {
            return UserResponse(
                userService.updateUserWithRoles(
                    userId = UserId(id),
                    email = userRequest.email,
                    fullName = userRequest.fullName,
                    roles = userRequest.roles,
                    password = userRequest.password,
                ),
            )
        } else {
            return UserResponse(
                userService.updateUser(
                    userId = UserId(id),
                    email = userRequest.email,
                    fullName = userRequest.fullName,
                    password = userRequest.password,
                ),
            )
        }
    }

    @DeleteMapping("/{id}")
    fun deleteUser(@PathVariable id: String) {
        userService.deleteUser(UserId(id))
    }
}
