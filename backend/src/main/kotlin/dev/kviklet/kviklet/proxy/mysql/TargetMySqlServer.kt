package dev.kviklet.kviklet.proxy.mysql

import com.mysql.cj.jdbc.ConnectionImpl
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.DatasourceType
import java.net.Socket
import java.sql.DriverManager
import java.util.*

class TargetMySqlConnection(val socket: Socket)

class TargetMySqlSocketFactory(
    private val datasourceType: DatasourceType,
    private val authenticationDetails: AuthenticationDetails.UserPassword,
    private val databaseName: String,
    private val targetHost: String,
    private val targetPort: Int,
) {
    fun createTargetMySqlConnection(): TargetMySqlConnection {
        val props = Properties()
        props.setProperty("user", authenticationDetails.username)
        props.setProperty("password", authenticationDetails.password)
        
        val dbName = if (databaseName.isNotEmpty()) databaseName else ""
        
        if (datasourceType == DatasourceType.MARIADB) {
            val url = "jdbc:mariadb://$targetHost:$targetPort/$dbName"
            Class.forName("org.mariadb.jdbc.Driver")
            val conn = DriverManager.getConnection(url, props)
            try {
                val socket = getMariaDbSocket(conn)
                return TargetMySqlConnection(socket)
            } catch (e: Exception) {
                conn.close()
                throw e
            }
        } else {
            val url = "jdbc:mysql://$targetHost:$targetPort/$dbName"
            Class.forName("com.mysql.cj.jdbc.Driver")
            val conn = DriverManager.getConnection(url, props) as ConnectionImpl
            try {
                val sessionField = ConnectionImpl::class.java.getDeclaredField("session")
                sessionField.isAccessible = true
                val session = sessionField.get(conn)
                
                val getProtocolMethod = session.javaClass.getMethod("getProtocol")
                val protocol = getProtocolMethod.invoke(session)
                
                val getSocketConnectionMethod = protocol.javaClass.getMethod("getSocketConnection")
                val socketConnection = getSocketConnectionMethod.invoke(protocol)
                
                val getMysqlSocketMethod = socketConnection.javaClass.getMethod("getMysqlSocket")
                val mysqlSocket = getMysqlSocketMethod.invoke(socketConnection) as Socket
                
                return TargetMySqlConnection(mysqlSocket)
            } catch (e: Exception) {
                conn.close()
                throw e
            }
        }
    }

    private fun getMariaDbSocket(conn: java.sql.Connection): Socket {
        val getClientMethod = conn.javaClass.getMethod("getClient")
        val client = getClientMethod.invoke(conn)
        var currentClass: Class<*>? = client.javaClass
        while (currentClass != null) {
            try {
                val socketField = currentClass.getDeclaredField("socket")
                socketField.isAccessible = true
                return socketField.get(client) as Socket
            } catch (e: NoSuchFieldException) {
                currentClass = currentClass.superclass
            }
        }
        throw NoSuchFieldException("Could not find field 'socket' on MariaDB client class")
    }
}
