package site.lagnaatelier.app.account

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A stand-in for lagnaatelier.site on 127.0.0.1, from the JDK's own HTTP
 * server, so LagnaApi's real connections (and its bearer header) are what
 * the tests exercise. Each route answers with whatever [answer] returns for
 * it; every request is kept in [calls].
 */
class FakeSite : AutoCloseable {
    data class Call(val method: String, val path: String, val query: String?, val authorization: String?, val body: String)

    data class Answer(val status: Int, val body: String)

    val calls = CopyOnWriteArrayList<Call>()

    /** "METHOD /path" to the answer; a missing route is a 404. */
    val routes = mutableMapOf<String, (Call) -> Answer>()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val call = Call(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                query = exchange.requestURI.rawQuery,
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                body = exchange.requestBody.readBytes().toString(Charsets.UTF_8),
            )
            calls += call
            val answer = routes["${call.method} ${call.path}"]?.invoke(call) ?: Answer(404, """{"detail":"Not found"}""")
            val bytes = answer.body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(answer.status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        start()
    }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    fun on(route: String, status: Int = 200, body: String) {
        routes[route] = { Answer(status, body) }
    }

    fun callsTo(route: String) = calls.filter { "${it.method} ${it.path}" == route }

    override fun close() = server.stop(0)
}
