package dev.kviklet.kviklet
import dev.kviklet.kviklet.shell.KubernetesApi
import io.kubernetes.client.Exec
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodList
import io.kubernetes.client.openapi.models.V1PodStatus
import io.kubernetes.client.util.Config
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

class KubernetesApiTest {
    @Test
    fun testGetActivePods() {
        val mockCoreV1Api = mockk<CoreV1Api>()

        val pod1 = V1Pod().apply {
            metadata = V1ObjectMeta().apply {
                name = "pod1"
                namespace = "default"
            }
            status = V1PodStatus().apply {
                phase = "Running"
            }
        }

        val pod2 = V1Pod().apply {
            metadata = V1ObjectMeta().apply {
                name = "pod2"
                namespace = "default"
            }
            status = V1PodStatus().apply {
                phase = "Running"
            }
        }

        val podList = V1PodList().apply {
            items = listOf(pod1, pod2)
        }

        coEvery {
            mockCoreV1Api.listPodForAllNamespaces().execute()
        } returns podList

        val kubernetesApi = KubernetesApi(mockCoreV1Api)

        val activePods = kubernetesApi.getActivePods()

        assertEquals(2, activePods.size)
        assertEquals("pod1", activePods[0].metadata?.name)
        assertEquals("default", activePods[0].metadata?.namespace)
        assertEquals("Running", activePods[0].status?.phase)
        assertEquals("pod2", activePods[1].metadata?.name)
        assertEquals("default", activePods[1].metadata?.namespace)
        assertEquals("Running", activePods[1].status?.phase)
    }

    @Test
    fun testExecuteCommandOnPod() {
        val mockExec = mockk<Exec>()
        val mockProcess = mockk<Process>()
        val mockApiClient = mockk<ApiClient>()

        val mockCoreV1Api = mockk<CoreV1Api>()

        mockkStatic(Config::class)
        every { Config.defaultClient() } returns mockApiClient
        every {
            mockExec.exec(
                eq("default"),
                eq("pod1"),
                arrayOf("/bin/sh", "-c", "echo 'Hello, World!'"),
                true,
                false,
            )
        } returns mockProcess

        val commandOutput = "Hello, World!\n".toByteArray()
        val errorOutput = "Error message\n".toByteArray()
        every { mockProcess.inputStream } returns ByteArrayInputStream(commandOutput)
        every { mockProcess.errorStream } returns ByteArrayInputStream(errorOutput)
        every { mockProcess.waitFor(5, TimeUnit.SECONDS) } returns true
        every { mockProcess.exitValue() } returns 0

        val kubernetesApi = KubernetesApi(mockCoreV1Api)

        val namespace = "default"
        val podName = "pod1"
        val command = "echo 'Hello, World!'"

        val result = kubernetesApi.executeCommandOnPod(
            namespace,
            podName,
            command = command,
            exec = mockExec,
        )

        assertEquals(1, result.messages.size)
        assertEquals("Hello, World!", result.messages.first())
        assertEquals(1, result.errors.size)
        assertEquals("Error message", result.errors.first())
        assertEquals(true, result.finished)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun testExecuteCommandOnPodWithContainerName() {
        val mockExec = mockk<Exec>()
        val mockProcess = mockk<Process>()
        val mockApiClient = mockk<ApiClient>()

        val mockCoreV1Api = mockk<CoreV1Api>()

        mockkStatic(Config::class)
        every { Config.defaultClient() } returns mockApiClient

        val commandOutput = "Hello from container!\n".toByteArray()
        val errorOutput = "Error message\n".toByteArray()
        every { mockProcess.inputStream } returns ByteArrayInputStream(commandOutput)
        every { mockProcess.errorStream } returns ByteArrayInputStream(errorOutput)
        every { mockProcess.waitFor(5, TimeUnit.SECONDS) } returns true
        every { mockProcess.exitValue() } returns 0

        // Define test data
        val namespace = "default"
        val podName = "pod1"

        every {
            mockExec.exec(
                eq("default"),
                eq("pod1"),
                arrayOf("/bin/sh", "-c", "echo 'Hello from container!'"),
                eq("container1"),
                true,
                false,
            )
        } returns mockProcess

        val kubernetesApi = KubernetesApi(mockCoreV1Api)

        val command = "echo 'Hello from container!'"
        val containerName = "container1"

        val result = kubernetesApi.executeCommandOnPod(
            namespace,
            podName,
            containerName,
            command,
            exec = mockExec,
        )

        assertEquals("Hello from container!", result.messages.first())
        assertEquals("Error message", result.errors.first())
    }

    @Test
    fun testExecuteCommandTimeoutNotExceeded() {
        val mockExec = mockk<Exec>()
        val mockProcess = mockk<Process>()
        val mockApiClient = mockk<ApiClient>()

        val mockCoreV1Api = mockk<CoreV1Api>()

        mockkStatic(Config::class)
        every { Config.defaultClient() } returns mockApiClient

        val commandOutput = "Hello from container!\n".toByteArray()
        every { mockProcess.inputStream } returns ByteArrayInputStream(commandOutput)
        every { mockProcess.errorStream } returns ByteArrayInputStream("".toByteArray())

        val namespace = "default"
        val podName = "pod1"

        val command = "echo 'Hello from container!'"

        every {
            mockExec.exec(
                eq("default"),
                eq("pod1"),
                arrayOf("/bin/sh", "-c", "echo 'Hello from container!'"),
                true,
                false,
            )
        } returns mockProcess

        every { mockProcess.waitFor(5, TimeUnit.SECONDS) } returns false

        every { mockProcess.exitValue() } returns 1

        val kubernetesApi = KubernetesApi(mockCoreV1Api)

        val result = kubernetesApi.executeCommandOnPod(
            namespace,
            podName,
            command = command,
            exec = mockExec,
        )

        assertEquals(false, result.finished)
        assertNull(result.exitCode)
    }
}
