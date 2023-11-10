package com.example.executiongate

import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.core.GenericHandler
import org.springframework.integration.dsl.HeaderEnricherSpec
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.IntegrationFlows
import org.springframework.integration.ip.IpHeaders
import org.springframework.integration.ip.dsl.Tcp
import org.springframework.integration.ip.tcp.connection.AbstractClientConnectionFactory
import org.springframework.integration.ip.tcp.connection.AbstractServerConnectionFactory
import org.springframework.integration.ip.tcp.connection.TcpConnection
import org.springframework.integration.ip.tcp.connection.TcpConnectionOpenEvent
import org.springframework.integration.ip.tcp.connection.ThreadAffinityClientConnectionFactory
import org.springframework.integration.ip.tcp.serializer.ByteArrayLengthHeaderSerializer
import org.springframework.integration.ip.tcp.serializer.SoftEndOfStreamException
import org.springframework.messaging.MessageHeaders
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

@OptIn(ExperimentalStdlibApi::class)
@Configuration
class TcpConfig {

    @Bean
    fun fromToConnectionMappings(): MutableMap<String, String> {
        return ConcurrentHashMap()
    }

    @Bean
    fun toFromConnectionMappings(): MutableMap<String, String> {
        return ConcurrentHashMap()
    }

    @Bean
    fun proxyInboundFlow(): IntegrationFlow {
        return IntegrationFlows.from(Tcp.inboundAdapter(serverFactory()))
            .handle<Any>(
                GenericHandler<Any> { p: Any?, h: MessageHeaders ->
//                mapConnectionIds(h)

                    println("inbound from client: " + (p as ByteArray).toString(charset("ascii")))
                    println("inbound from client: " + (p as ByteArray).toHexString())
                    p
                },
            )
            .handle(Tcp.outboundAdapter(threadConnectionFactory()))
            .get()
    }

    @Bean
    fun proxyOutboundFlow(): IntegrationFlow {
        return IntegrationFlows.from(Tcp.inboundAdapter(threadConnectionFactory()))
            .handle<Any> { p, h ->
                println("inbound from server: " + (p as ByteArray).toString(charset("ascii")))
                println("inbound from server: " + (p as ByteArray).toHexString())

                p
            }
            .enrichHeaders(
                Consumer<HeaderEnricherSpec> { e: HeaderEnricherSpec ->
                    e
                        .headerExpression(
                            IpHeaders.CONNECTION_ID,
                            "@toFromConnectionMappings.get(headers['" +
                                IpHeaders.CONNECTION_ID + "'])",
                        ).defaultOverwrite(true)
                },
            )
            .handle(Tcp.outboundAdapter(serverFactory()))
            .get()
    }

    private fun mapConnectionIds(connectionId: String) {
        try {
            val connection: TcpConnection = threadConnectionFactory().getConnection()
            val mapping = toFromConnectionMappings()[connection.connectionId]
            if (mapping == null || mapping != connectionId) {
                println("Adding new mapping " + connectionId + " to " + connection.connectionId)
                toFromConnectionMappings().put(connection.connectionId, connectionId)
                fromToConnectionMappings().put(connectionId, connection.connectionId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Bean
    fun threadConnectionFactory(): ThreadAffinityClientConnectionFactory {
        return object : ThreadAffinityClientConnectionFactory(clientFactory()) {
            override fun isSingleUse(): Boolean {
                return false
            }
        }
    }

    @Bean
    fun serverFactory(): AbstractServerConnectionFactory {
        return Tcp.netServer(1234).deserializer(MysqlHeaderSerializer()).serializer(MysqlHeaderSerializer()).get()
    }

    @Bean
    fun clientFactory(): AbstractClientConnectionFactory {
        val clientFactory: AbstractClientConnectionFactory = Tcp.netClient("localhost", 3306)
            .deserializer(MysqlHeaderSerializer())
            .serializer(MysqlHeaderSerializer())
            .get()
        clientFactory.isSingleUse = true
        return clientFactory
    }

    @Bean
    fun opener(): ApplicationListener<TcpConnectionOpenEvent> {
        return ApplicationListener<TcpConnectionOpenEvent> { e: TcpConnectionOpenEvent ->
            if (e.connectionFactoryName == "serverFactory") {
                mapConnectionIds(e.connectionId)
            }
            println(e)
        }
    }
}

class MysqlHeaderSerializer() : ByteArrayLengthHeaderSerializer() {

    init {
        maxMessageSize = 16777216
    }

    private val headerSize = 3

    override fun readHeader(inputStream: InputStream?): Int {
        val header = ByteArray(headerSize)
        return try {
            val status = read(inputStream, header, true)
            if (status < 0) {
                throw SoftEndOfStreamException("Stream closed between payloads")
            }

            val bb = ByteBuffer.wrap(header.copyOf(4))
            bb.order(ByteOrder.LITTLE_ENDIAN)

            val length = bb.getInt()

            println("Reading package with length ${length + 1}.")

            length + 1
        } catch (e: SoftEndOfStreamException) { // NOSONAR catch and throw
            throw e // it's an IO exception and we don't want an event for this
        } catch (ex: IOException) {
            publishEvent(ex, header, -1)
            throw ex
        } catch (ex: RuntimeException) {
            publishEvent(ex, header, -1)
            throw ex
        }
    }

    override fun writeHeader(outputStream: OutputStream, length: Int) {
        val lengthPart = ByteBuffer.allocate(3)
        lengthPart.order(ByteOrder.LITTLE_ENDIAN)
        lengthPart.putShort((length - 1).toShort())
        lengthPart.put(0.toByte())
        outputStream.write(lengthPart.array())
    }
}
