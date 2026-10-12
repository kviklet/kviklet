// This file is not MIT licensed
package dev.kviklet.kviklet.mcp

import dev.kviklet.kviklet.service.EntityAlreadyExists
import dev.kviklet.kviklet.service.EntityNotFound
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.stereotype.Component
import java.lang.reflect.UndeclaredThrowableException

/**
 * Spring AI turns runtime exceptions of a tool into a tool error the agent can read, but rethrows checked
 * ones, which ends the call with an HTTP 500. Several services throw checked exceptions, like
 * [EntityNotFound] for an unknown id, so the tools' handlers turn those into tool errors too.
 */
@Component
class McpToolErrors : BeanPostProcessor {

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        if (bean !is List<*> || bean.isEmpty() || bean.any { it !is SyncToolSpecification }) return bean
        return bean.map { (it as SyncToolSpecification).withCheckedExceptionsAsErrors() }
    }

    private fun SyncToolSpecification.withCheckedExceptionsAsErrors() =
        SyncToolSpecification(tool()) { context, request ->
            try {
                callHandler().apply(context, request)
            } catch (e: UndeclaredThrowableException) {
                val cause = e.undeclaredThrowable
                val message = when (cause) {
                    is EntityNotFound -> cause.detail
                    is EntityAlreadyExists -> cause.detail
                    else -> cause.message ?: cause.javaClass.simpleName
                }
                CallToolResult.builder().isError(true).addTextContent(message).build()
            }
        }
}
