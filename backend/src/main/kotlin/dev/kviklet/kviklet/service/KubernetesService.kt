package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.shell.KubernetesApi
import io.kubernetes.client.openapi.models.V1Pod
import org.springframework.stereotype.Service

@Service
class KubernetesService(private val kubernetesApi: KubernetesApi) {

    @Policy(Permission.EXECUTION_REQUEST_EDIT, checkIsPresentOnly = true)
    fun listPods(): List<V1Pod> = kubernetesApi.getActivePods()
}
