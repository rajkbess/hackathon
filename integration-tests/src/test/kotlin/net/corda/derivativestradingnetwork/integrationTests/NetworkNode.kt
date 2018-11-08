package net.corda.derivativestradingnetwork.integrationTests

import com.google.gson.*
import net.corda.core.concurrent.CordaFuture
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.getOrThrow
import net.corda.derivativestradingnetwork.flow.UserIssuanceRequestFlow
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.WebserverHandle
import okhttp3.*
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkNode(val driver : DriverDSL, val testIdentity : TestIdentity, autoStart : Boolean) {

    lateinit var coreHandle : NodeHandle
    lateinit var coreHandleFuture : CordaFuture<NodeHandle>
    lateinit var webHandle : WebserverHandle
    lateinit var webHandleFuture : CordaFuture<WebserverHandle>

    fun startUserIssuanceFlow(amount : Long, currency : Currency, bank : Party) {
        coreHandle.rpc.startTrackedFlow(::UserIssuanceRequestFlow, amount, currency, bank)
    }

    fun party() : Party {
        return coreHandle.nodeInfo.legalIdentities.first()
    }

    init {
        if(autoStart) {
            startCoreAsync().waitForCoreToStart()
            startWebAsync().waitForWebToStart()
        }
    }

    fun startCoreAsync() : NetworkNode {
        coreHandleFuture = driver.startNode(providedName = testIdentity.name)
        return this
    }

    fun waitForCoreToStart() : NetworkNode {
        coreHandle = coreHandleFuture.getOrThrow()
        return this
    }

    fun startWebAsync() : NetworkNode {
        webHandleFuture = driver.startWebserver(coreHandle)
        return this
    }

    fun waitForWebToStart() : NetworkNode {
        webHandle = webHandleFuture.getOrThrow()
        return this
    }

    //confirmation methods
    fun confirmNodeIsOnTheNetwork() {
        assertEquals(coreHandle.rpc.wellKnownPartyFromX500Name(coreHandle.nodeInfo.legalIdentities.first().name)!!.name, testIdentity.name)
    }

    //business operations


    //low level url operations

    //GETs
    protected fun getFromUrl(path : String) : Response {
        val nodeAddress = webHandle.listenAddress
        val url = "http://$nodeAddress/$path"
        val request = Request.Builder().url(url).build()
        val response = getPatientHttpClient().newCall(request).execute()

        assertTrue(response.isSuccessful)
        assertEquals("OK", response.message())
        return response
    }

    protected fun getFromUrlWithAQueryParameter(url : String, nameAndVauleMap : Map<String,String>) : Response {
        val httpBuilder = HttpUrl.parse(url).newBuilder()

        nameAndVauleMap.forEach {
            httpBuilder.addQueryParameter(it.key,it.value)
        }

        val request = Request.Builder().url(httpBuilder.build()).build()
        return getPatientHttpClient().newCall(request).execute()
    }

    //POSTs
    protected fun postHeadersToUrl(url : String, headers : Map<String,String>) : Response {
        val mediaType = MediaType.parse("text/plain")
        val request = Request.Builder().url(url).post(RequestBody.create(mediaType, ""))
        headers.forEach {
            request.addHeader(it.key, it.value)
        }

        return getPatientHttpClient().newCall(request.build()).execute()
    }

    protected fun postObjectAsJsonToUrl(objekt : Any, url : String) : Response {
        val jsonFormat = objectToJson(objekt)
        return postJsonToUrl(jsonFormat, url)
    }


    protected fun postPlainTextToUrl(plainText : String, url : String) : Response {
        val mediaType = MediaType.parse("text/plain")
        val request = Request.Builder().url(url).post(RequestBody.create(mediaType, plainText)).build()
        return getPatientHttpClient().newCall(request).execute()
    }

    protected fun postJsonToUrl(json : String, url : String) : Response {
        val mediaType = MediaType.parse("application/json")
        val request = Request.Builder().url(url).post(RequestBody.create(mediaType, json)).build()
        return getPatientHttpClient().newCall(request).execute()
    }

    private fun objectToJson(objekt : Any) : String {
        return Gson().toJson(objekt)
    }

    protected fun getPatientHttpClient() : OkHttpClient {
        return OkHttpClient.Builder().readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).build()
    }

}