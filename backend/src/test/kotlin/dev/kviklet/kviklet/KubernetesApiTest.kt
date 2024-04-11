package dev.kviklet.kviklet
import dev.kviklet.kviklet.shell.KubernetesApi
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodList
import io.kubernetes.client.openapi.models.V1PodStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class KubernetesApiTest {
    @Test
    fun testGetActivePods() {
        // Create a mock instance of CoreV1Api
        val mockCoreV1Api = mock(CoreV1Api::class.java)
        // Configure the mock CoreV1Api to return a predefined list of pods
        val pod1 = V1Pod()
        pod1.metadata = V1ObjectMeta().name("pod1").namespace("default")
        pod1.status = V1PodStatus().phase("Running")
        val pod2 = V1Pod()
        pod2.metadata = V1ObjectMeta().name("pod2").namespace("default")
        pod2.status = V1PodStatus().phase("Running")
        val podList = V1PodList().items(listOf(pod1, pod2))
        `when`(mockCoreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null))
            .thenReturn(podList)
        // Set the mock CoreV1Api in your code under test
        val kubernetesApi = KubernetesApi(mockCoreV1Api)
        // Call the method under test
        val activePods = kubernetesApi.getActivePods()
        // Assert the expected behavior
        assertEquals(2, activePods.size)
        assertEquals("pod1", activePods[0].metadata?.name)
        assertEquals("default", activePods[0].metadata?.namespace)
        assertEquals("Running", activePods[0].status?.phase)
        assertEquals("pod2", activePods[1].metadata?.name)
        assertEquals("default", activePods[1].metadata?.namespace)
        assertEquals("Running", activePods[1].status?.phase)
    }
}
