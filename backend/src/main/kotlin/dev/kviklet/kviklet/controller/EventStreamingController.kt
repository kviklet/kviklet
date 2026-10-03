// This file is not MIT licensed
package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.service.EventStreamingService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/config/event-streaming")
class EventStreamingController(private val eventStreamingService: EventStreamingService) {
    @GetMapping
    fun getConfiguration() = eventStreamingService.getConfiguration()
}
