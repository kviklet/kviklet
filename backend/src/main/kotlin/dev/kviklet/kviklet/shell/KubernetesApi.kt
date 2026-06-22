package dev.kviklet.kviklet.shell

import io.kubernetes.client.Exec
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodList
import io.kubernetes.client.util.Config
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Service
class KubernetesApi(private val coreV1Api: CoreV1Api) {

    companion object {
        private val logger = LoggerFactory.getLogger(KubernetesApi::class.java)
    }

    fun getActivePods(): List<V1Pod> {
        val pods: V1PodList = try {
            coreV1Api.listPodForAllNamespaces().execute()
        } catch (e: Exception) {
            // Log the underlying cause (cluster unreachable, auth failure, client/server
            // version mismatch, ...) and surface a user-friendly error instead of silently
            // returning an empty list, which previously made failures look like "no pods".
            logger.error("Failed to list pods from the Kubernetes cluster", e)
            throw IllegalArgumentException(
                "Could not list pods from the Kubernetes cluster. " +
                    "Please check the cluster connection and the server logs for details.",
            )
        }
        return pods.items.orEmpty().filter { it.status?.phase == "Running" }
    }

    fun executeCommandOnPod(
        namespace: String,
        podName: String,
        containerName: String? = null,
        command: String,
        initialWaitTimeoutSeconds: Long = 5,
        timeoutMinutes: Long = 60,
        exec: Exec = Exec(Config.defaultClient()),
    ): KubernetesResult {
        val commands = arrayOf("/bin/sh", "-c", command)
        val process = when (containerName != null) {
            true -> exec.exec(namespace, podName, commands, containerName, true, false)
            false -> exec.exec(namespace, podName, commands, true, false)
        }

        val outputLines = mutableListOf<String>()
        val errorLines = mutableListOf<String>()

        val inputReader = process.inputStream.bufferedReader()
        val errorReader = process.errorStream.bufferedReader()

        val completed = process.waitFor(initialWaitTimeoutSeconds, TimeUnit.SECONDS)
        var lineCount = 0

        while (inputReader.ready() && lineCount < 100) {
            outputLines.add(inputReader.readLine())
            lineCount++
        }

        lineCount = 0
        while (errorReader.ready() && lineCount < 100) {
            errorLines.add(errorReader.readLine())
            lineCount++
        }

        if (!completed) {
            CompletableFuture.runAsync {
                process.waitFor(timeoutMinutes, TimeUnit.MINUTES)
                process.destroy()
            }
        }
        return KubernetesResult(
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

data class KubernetesResult(
    val errors: List<String>,
    val messages: List<String>,
    val finished: Boolean = true,
    val exitCode: Int? = 0,
)
