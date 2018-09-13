package net.corda.derivativestradingnetwork.integrationTests

import com.google.common.reflect.TypeToken
import net.corda.derivativestradingnetwork.entity.PartyNameAndMembershipMetadata
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverDSL
import okhttp3.Request
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberNode(driver : DriverDSL, testIdentity : TestIdentity, autoStart : Boolean) : BusinessNetworkNode(driver, testIdentity, autoStart) {

    //business processes
    fun askForMembership(membershipDefinition : String) {
        val response = postPlainTextToUrl(membershipDefinition, "http://${webHandle.listenAddress}/api/memberApi/requestMembership")
        assertEquals("OK", response.message())
        assertTrue(response.isSuccessful)
    }

    fun getMembersVisibleToNode() : List<PartyNameAndMembershipMetadata> {
        val nodeAddress = webHandle.listenAddress
        val url = "http://$nodeAddress/api/memberApi/members"
        val request = Request.Builder().url(url).build()
        val response = getPatientHttpClient().newCall(request).execute()

        assertTrue(response.isSuccessful)
        assertEquals("OK", response.message())

        val desiredType = object : TypeToken<List<PartyNameAndMembershipMetadata>>() {}.type
        val responseInJson = response.body().string()
        return getSuitableGson().fromJson<List<PartyNameAndMembershipMetadata>>(responseInJson,desiredType)
    }

    fun getMembersVisibleToNode(members : String) : List<*> {
        val nodeAddress = webHandle.listenAddress
        val url = "http://$nodeAddress/api/memberApi/${members}"
        val request = Request.Builder().url(url).build()
        val response = getPatientHttpClient().newCall(request).execute()

        assertTrue(response.isSuccessful)
        assertEquals("OK", response.message())

        val desiredType = object : TypeToken<List<PartyNameAndMembershipMetadata>>() {}.type
        val responseInJson = response.body().string()
        return getSuitableGson().fromJson<List<PartyNameAndMembershipMetadata>>(responseInJson,desiredType)
    }


}
