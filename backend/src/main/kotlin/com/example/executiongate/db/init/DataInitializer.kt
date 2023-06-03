package com.example.executiongate.db.init

import com.example.executiongate.db.User
import com.example.executiongate.db.UserRepository
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class DataInitializer {

    @Bean
    fun initializer(userRepository: UserRepository, passwordEncoder: PasswordEncoder): ApplicationRunner {
        return ApplicationRunner { args ->
            val user = User(
                    username = "testUser",
                    password = passwordEncoder.encode("testPassword")
            )
            userRepository.save(user)
        }
    }
}
