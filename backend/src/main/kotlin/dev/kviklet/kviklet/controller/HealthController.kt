package dev.kviklet.kviklet.controller
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {

    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<String> = ResponseEntity.ok("Healthy")
}
