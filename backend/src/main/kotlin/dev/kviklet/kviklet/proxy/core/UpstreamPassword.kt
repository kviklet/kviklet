// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.core

import dev.kviklet.kviklet.service.RdsIamTokenProvider
import dev.kviklet.kviklet.service.dto.AuthenticationDetails

// The password the proxy presents to the upstream for one connection: the stored one, or a freshly minted
// RDS IAM token (valid for 15 minutes and bound to this host, port and user), so it is resolved per
// upstream connect rather than once per session.
fun AuthenticationDetails.upstreamPassword(host: String, port: Int, tokenProvider: RdsIamTokenProvider): String =
    when (this) {
        is AuthenticationDetails.UserPassword -> password
        is AuthenticationDetails.AwsIam -> tokenProvider.generateToken(host, port, username, roleArn)
    }
