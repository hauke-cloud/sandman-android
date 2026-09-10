package cloud.hauke.sandman

import cloud.hauke.sandman.data.model.PowerPhase
import cloud.hauke.sandman.data.model.PowerRequest
import cloud.hauke.sandman.data.model.PowerState
import cloud.hauke.sandman.data.remote.ApiConfig
import cloud.hauke.sandman.data.remote.PowerAction
import cloud.hauke.sandman.data.remote.SandmanClient
import cloud.hauke.sandman.data.remote.SandmanException
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SandmanClientTest {

  private lateinit var server: MockWebServer
  private lateinit var client: SandmanClient

  private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = false
  }

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    client = SandmanClient(OkHttpClient(), json)
  }

  @After
  fun tearDown() {
    server.close()
  }

  private fun config() = ApiConfig(baseUrl = server.url("/").toString(), token = "s3cret")

  @Test
  fun `lists devices and their summary`() = runTest {
    server.enqueue(
      MockResponse(
        code = 200,
        body = """
        {
          "items": [
            {
              "name": "worker-01",
              "node": "worker-01.lab",
              "macAddress": "aa:bb:cc:dd:ee:ff",
              "broadcastAddress": "192.168.1.255",
              "state": "Started",
              "desiredState": "On",
              "inFlight": false,
              "paused": false,
              "nodeStatus": { "found": true, "ready": true },
              "timestamps": { "phaseSince": "2026-09-11T08:00:00Z" }
            }
          ],
          "count": 1,
          "summary": { "Started": 1 }
        }
        """.trimIndent(),
      ),
    )

    val list = client.listDevices(config())

    assertEquals(1, list.count)
    assertEquals(PowerPhase.Started, list.items.single().state)
    assertEquals(PowerState.On, list.items.single().desiredState)
    assertEquals(mapOf("Started" to 1), list.summary)

    val request = server.takeRequest()
    assertEquals("/api/v1/devices", request.url.encodedPath)
    assertEquals("Bearer s3cret", request.headers["Authorization"])
  }

  @Test
  fun `a phase this app does not know decodes as Unknown`() = runTest {
    server.enqueue(
      MockResponse(
        code = 200,
        body = """{"name":"worker-01","state":"Hibernating"}""",
      ),
    )

    assertEquals(PowerPhase.Unknown, client.getDevice(config(), "worker-01").state)
  }

  @Test
  fun `a stop posts to the device's stop route`() = runTest {
    server.enqueue(
      MockResponse(
        code = 202,
        body = """{"accepted":true,"requestID":"abc","message":"stopping worker-01.lab"}""",
      ),
    )

    val response = client.power(
      config = config(),
      name = "worker-01",
      action = PowerAction.Stop,
      request = PowerRequest(requestedBy = "hauke", reason = "maintenance"),
    )

    assertTrue(response.accepted)
    assertEquals("abc", response.requestId)

    val request = server.takeRequest()
    assertEquals("POST", request.method)
    assertEquals("/api/v1/devices/worker-01/stop", request.url.encodedPath)
    // sandman decodes the body with DisallowUnknownFields, so it must carry
    // these two fields and nothing else.
    assertEquals(
      """{"requestedBy":"hauke","reason":"maintenance"}""",
      request.body?.utf8(),
    )
  }

  @Test
  fun `sandman's error envelope becomes an api exception`() = runTest {
    server.enqueue(
      MockResponse(
        code = 409,
        body = """{"error":"shutdown_disabled","message":"device worker-01 has spec.shutdown.mode=Disabled"}""",
      ),
    )

    val thrown = runCatching {
      client.power(config(), "worker-01", PowerAction.Stop, PowerRequest())
    }.exceptionOrNull()

    val api = thrown as SandmanException.Api
    assertEquals(409, api.status)
    assertEquals("shutdown_disabled", api.code)
    assertEquals("The device has spec.shutdown.mode=Disabled.", api.hint)
  }

  @Test
  fun `a bare host is treated as http and a sub-path is kept`() {
    assertEquals(
      "http://sandman.lab:8080/",
      SandmanClient.normalizeBaseUrl("sandman.lab:8080").toString(),
    )
    assertEquals(
      "https://ops.example.com/sandman",
      SandmanClient.normalizeBaseUrl("https://ops.example.com/sandman/").toString(),
    )
  }

  /**
   * Reading a response body pulls from the socket. OkHttp's call is
   * asynchronous, but the coroutine resumes on whichever dispatcher asked for
   * it -- on Android that is the main thread, and Android kills a main-thread
   * socket read. So the read has to happen somewhere else, and this is the
   * only place a JVM test can see that: StrictMode does not exist here.
   */
  @Test
  fun `the response body is never read on the caller's thread`() = runTest {
    var readThread: String? = null
    val recording = Interceptor { chain ->
      val response = chain.proceed(chain.request())
      val original = response.body
      val watched = object : ForwardingSource(original.source()) {
        override fun read(sink: Buffer, byteCount: Long): Long {
          readThread = Thread.currentThread().name
          return super.read(sink, byteCount)
        }
      }.buffer()
      response.newBuilder()
        .body(watched.asResponseBody(original.contentType(), original.contentLength()))
        .build()
    }

    val watchedClient = SandmanClient(
      OkHttpClient.Builder().addNetworkInterceptor(recording).build(),
      json,
    )
    server.enqueue(MockResponse(code = 200, body = """{"items":[],"count":0,"summary":{}}"""))

    val caller = Thread.currentThread().name
    watchedClient.listDevices(config())

    assertNotNull("the body was never read", readThread)
    assertNotEquals(caller, readThread)
  }
}
