package dev.kviklet.kviklet.shell

import io.kubernetes.client.Exec
import io.kubernetes.client.openapi.ApiClient
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

    fun getPods() {
        val activePods = getActivePods()
        activePods.forEach { pod ->
            println(
                "Pod: ${pod.metadata?.name}, Namespace: ${pod.metadata?.namespace}, Status: ${pod.status?.phase}",
            )
        }
        println("Getting pods")
    }

    fun executeCommandOnPod(
        namespace: String,
        podName: String,
        command: String,
        timeout: Long = 5,
    ): Pair<String, String> {
        val apiClient: ApiClient = Config.defaultClient()
        val exec = Exec(apiClient)

        val commands = arrayOf("/bin/sh", "-c", command)
        val process: Process = exec.exec(namespace, podName, commands, true, true)

        val inputReader = process.inputStream.bufferedReader()
        val errorReader = process.errorStream.bufferedReader()

        val completed = process.waitFor(5, TimeUnit.SECONDS)

        val outputLines = mutableListOf<String>()
        val errorLines = mutableListOf<String>()
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

        val output = outputLines.joinToString("\n")
        val error = errorLines.joinToString("\n")

        if (!completed) {
            CompletableFuture.runAsync {
                process.waitFor(timeout, TimeUnit.MINUTES)
                println("Waited $timeout minutes")
                process.destroy()
            }
        }

        return Pair(output, error)
    }

    fun executeCommandOnPodLive(
        namespace: String,
        podName: String,
        command: String,
        requestId: String,
        sendWebSocketMessage: (String) -> Unit,
    ): Process {
        val apiClient: ApiClient = Config.defaultClient()
        val exec = Exec(apiClient)

        val commands = arrayOf("/bin/sh", "-c", command)
        val process: Process = exec.exec(namespace, podName, commands, true, true)

        // asynchronously forwards all output of the process to the shell websocket
        CompletableFuture.runAsync {
            process.inputStream.bufferedReader().use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    sendWebSocketMessage(line)
                    line = reader.readLine()
                }
            }
        }

        // asynchronously forwards all error output of the process to the shell websocket
        CompletableFuture.runAsync {
            process.errorStream.bufferedReader().use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    sendWebSocketMessage(line!!)
                    line = reader.readLine()
                }
            }
        }

        return process
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
