package net.corda.derivativestradingnetwork.integrationTests

import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverDSL
import okhttp3.Request
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberNode(driver : DriverDSL, testIdentity : TestIdentity, autoStart : Boolean) : BusinessNetworkNode(driver, testIdentity, autoStart) {



    //business processes
    fun askForMembership(role : String, alternativeName : String) {
        val response = postPlainTextToUrl("{}", "http://${webHandle.listenAddress}/api/memberApi/requestMembership")
        assertEquals("OK", response.message())
        assertTrue(response.isSuccessful)
    }



    fun getMembersVisibleToNode() : List<*> {
        val nodeAddress = webHandle.listenAddress
        val url = "http://$nodeAddress/api/members"
        val request = Request.Builder().url(url).build()
        val response = getPatientHttpClient().newCall(request).execute()

        assertTrue(response.isSuccessful)
        assertEquals("OK", response.message())
        return getSuitableGson().fromJson(response.body().charStream(),List::class.java)
    }


}
