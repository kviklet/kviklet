const { OAuth2Server, OAuth2Service } = require('oauth2-mock-server');


async function main() {
    let server = new OAuth2Server();
    await server.issuer.keys.generate('RS256');
    server.service.once('beforeResponse', (tokenEndpointResponse, req) => {
        console.log(tokenEndpointResponse)
    });
    server.service.on('beforeTokenSigning', (token, req) => {
        token.payload.scope = "openid email profile"
        token.payload.email = "user@kviklet.test"
        token.payload.name = "Kviklet user"
        token.payload.sub = "1"
    });
    await server.start(4000, '0.0.0.0');
    console.log('Issuer URL:', server.issuer.url);
}

main()
