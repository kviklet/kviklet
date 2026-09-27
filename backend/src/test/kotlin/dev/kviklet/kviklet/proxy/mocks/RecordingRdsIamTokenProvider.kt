// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.mocks

import dev.kviklet.kviklet.service.RdsIamTokenProvider
import java.util.concurrent.CopyOnWriteArrayList

// Stands in for the AWS signer so an IAM session can be driven through the proxy against a testcontainer:
// the "token" it mints is the container's real password (an RDS token is nothing more than a short-lived
// password on the wire), and every request is recorded so tests can assert what the proxy asked for.
class RecordingRdsIamTokenProvider(private val token: String) : RdsIamTokenProvider {
    data class TokenRequest(val hostname: String, val port: Int, val username: String, val roleArn: String?)

    val requests = CopyOnWriteArrayList<TokenRequest>()

    override fun generateToken(hostname: String, port: Int, username: String, roleArn: String?): String {
        requests.add(TokenRequest(hostname, port, username, roleArn))
        return token
    }
}
