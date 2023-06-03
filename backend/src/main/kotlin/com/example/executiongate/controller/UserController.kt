import com.example.executiongate.db.User
import com.example.executiongate.db.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import javax.validation.Valid

import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

data class CreateUserRequest(
        @field:NotBlank
        @field:Size(min = 3, max = 50)
        val username: String,

        @field:NotBlank
        @field:Size(min = 6, max = 50)
        val password: String
)


@RestController
@RequestMapping("/users")
@CrossOrigin(origins = ["http://localhost:3000"])
class UserController(private val userRepository: UserRepository, private val passwordEncoder: PasswordEncoder) {

    @PostMapping
    fun createUser(@RequestBody @Valid userRequest: CreateUserRequest): User {
        val user = User(username = userRequest.username, password = passwordEncoder.encode(userRequest.password))
        return userRepository.save(user)
    }
}
