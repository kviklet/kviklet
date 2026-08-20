package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.security.CurrentUser
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.OncallGrantService
import dev.kviklet.kviklet.service.UserService
import dev.kviklet.kviklet.service.dto.OncallGrant
import dev.kviklet.kviklet.service.dto.OncallGrantKind
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

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

data class UserResponse(
    val id: String,
    val email: String,
    val fullName: String?,
    val roles: List<RoleResponse>,
    val activeOncallGrant: OncallGrantResponse? = null,
    val pendingOncallGrant: OncallGrantResponse? = null,
) {
    constructor(user: User) : this(
        id = user.getId()!!,
        email = user.email,
        fullName = user.fullName,
        roles = user.roles.map { RoleResponse.fromDto(it) },
        activeOncallGrant = user.activeOncallGrant?.takeIf { it.isActive() }?.let { OncallGrantResponse(it) },
        pendingOncallGrant = user.pendingOncallGrant?.takeIf { it.isPending() }?.let { OncallGrantResponse(it) },
    )
}

data class OncallGrantResponse(
    val id: String,
    val userId: String,
    val kind: OncallGrantKind,
    val reason: String?,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val bypassApproval: Boolean,
    val grantedByUserId: String,
    val createdAt: LocalDateTime,
    val status: String,
    val durationMinutes: Long,
    val approvedAt: LocalDateTime?,
) {
    constructor(grant: OncallGrant) : this(
        id = grant.id!!,
        userId = grant.userId,
        kind = grant.kind,
        reason = grant.reason,
        startsAt = grant.startsAt,
        endsAt = grant.endsAt,
        bypassApproval = grant.bypassApproval,
        grantedByUserId = grant.grantedByUserId,
        createdAt = grant.createdAt,
        status = grant.status(),
        durationMinutes = grant.durationMinutes,
        approvedAt = grant.approvedAt,
    )
}

data class StartOncallGrantRequest(
    @field:NotNull
    val kind: OncallGrantKind,

    @field:Min(15)
    @field:Max(14400)
    val durationMinutes: Long,

    val reason: String? = null,

    val bypassApproval: Boolean? = null,
)

data class UsersResponse(val users: List<UserResponse>) {
    companion object {
        fun fromUsers(users: List<User>): UsersResponse = UsersResponse(users.map { UserResponse(it) })
    }
}

@RestController()
@Validated
@RequestMapping("/users")
class UserController(
    private val userService: UserService,
    private val oncallGrantService: OncallGrantService,
) {

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

    @PostMapping("/{id}/oncall-grant")
    fun startOncallGrant(
        @PathVariable id: String,
        @RequestBody @Valid
        request: StartOncallGrantRequest,
        @CurrentUser currentUser: UserDetailsWithId,
    ): OncallGrantResponse = OncallGrantResponse(
        oncallGrantService.startGrant(
            userId = UserId(id),
            kind = request.kind,
            durationMinutes = request.durationMinutes,
            reason = request.reason,
            bypassApproval = request.bypassApproval,
            actorUserId = currentUser.id,
        ),
    )

    @PostMapping("/{id}/oncall-grant/request")
    fun requestOncallGrant(
        @PathVariable id: String,
        @RequestBody @Valid
        request: StartOncallGrantRequest,
        @CurrentUser currentUser: UserDetailsWithId,
    ): OncallGrantResponse = OncallGrantResponse(
        oncallGrantService.requestGrant(
            userId = UserId(id),
            kind = request.kind,
            durationMinutes = request.durationMinutes,
            reason = request.reason,
            bypassApproval = request.bypassApproval,
            actorUserId = currentUser.id,
        ),
    )

    @PostMapping("/{id}/oncall-grant/approve")
    fun approveOncallGrant(
        @PathVariable id: String,
        @CurrentUser currentUser: UserDetailsWithId,
    ): OncallGrantResponse = OncallGrantResponse(
        oncallGrantService.approvePending(UserId(id), currentUser.id),
    )

    @DeleteMapping("/{id}/oncall-grant")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeOncallGrant(
        @PathVariable id: String,
        @CurrentUser currentUser: UserDetailsWithId,
    ) {
        oncallGrantService.revokeOpen(UserId(id), currentUser.id)
    }
}
