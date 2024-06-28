package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.service.KubernetesService
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class Pod(
    val id: String,
    val name: String,
    val namespace: String,
    val status: String,
    val containerNames: List<String>,
)

data class PodList(val pods: List<Pod>)

data class CommandRequest(val namespace: String, val podName: String, val command: String)

data class CommandResponse(val output: List<String>, val error: List<String>)

@RestController
@Validated
@RequestMapping("/kubernetes")
@Tag(
    name = "Kubernetes",
    description = "Interact with a kubernetes cluster",
)
class KubernetesController(private val kubernetesService: KubernetesService) {

    @GetMapping("/pods")
    fun getPods(): PodList = PodList(
        pods = kubernetesService.listPods().map {
            Pod(
                id = it.metadata?.uid ?: "",
                name = it.metadata?.name ?: "",
                namespace = it.metadata?.namespace ?: "",
                status = it.status?.phase ?: "",
                containerNames = it.spec?.containers?.map { container -> container.name } ?: emptyList(),
            )
        },
    )
}
