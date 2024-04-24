package dev.kviklet.kviklet.shell

import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.KubernetesExecutionResult
import io.kubernetes.client.Exec
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.util.Config
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Service
class KubernetesApi(
    private val coreV1Api: CoreV1Api,
) {
    fun getActivePods(): List<V1Pod> {
        try {
            val pods: List<V1Pod> = coreV1Api.listPodForAllNamespaces(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
            ).items
            return pods.filter { it.status?.phase == "Running" }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    fun executeCommandOnPod(
        executionRequestId: ExecutionRequestId,
        namespace: String,
        podName: String,
        containerName: String? = null,
        command: String,
        timeout: Long = 5,
        exec: Exec = Exec(Config.defaultClient()),
    ): KubernetesExecutionResult {
        val commands = arrayOf("/bin/sh", "-c", command)
        val process = when (containerName != null) {
            true -> exec.exec(namespace, podName, commands, containerName, true, false)
            false -> exec.exec(namespace, podName, commands, true, false)
        }

        val outputLines = mutableListOf<String>()
        val errorLines = mutableListOf<String>()

        val inputReader = process.inputStream.bufferedReader()
        val errorReader = process.errorStream.bufferedReader()

        val completed = process.waitFor(5, TimeUnit.SECONDS)
        var lineCount = 0

        while (inputReader.ready() && lineCount < 10) {
            outputLines.add(inputReader.readLine())
            lineCount++
        }

        lineCount = 0
        while (errorReader.ready() && lineCount < 10) {
            errorLines.add(errorReader.readLine())
            lineCount++
        }

        if (!completed) {
            CompletableFuture.runAsync {
                process.waitFor(timeout, TimeUnit.MINUTES)
                process.destroy()
            }
        }
        return KubernetesExecutionResult(
            executionRequestId = executionRequestId,
            errors = errorLines,
            messages = outputLines,
            finished = completed,
            exitCode = if (completed) process.exitValue() else null,
        )
    }
}

@Configuration
class KubernetesConfig {
    @Bean
    fun coreV1Api(): CoreV1Api {
        val client = Config.defaultClient()
        io.kubernetes.client.openapi.Configuration.setDefaultApiClient(client)
        return CoreV1Api()
    }
}
