package net.corda.derivativestradingnetwork.integrationTests

import com.google.common.reflect.TypeToken
import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.derivativestradingnetwork.entity.PartyNameAndMembershipMetadata
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverDSL
import okhttp3.Request
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberNode(driver : DriverDSL, testIdentity : TestIdentity, autoStart : Boolean) : BusinessNetworkNode(driver, testIdentity, autoStart) {

    //cdm events related
    fun persistCDMEventOnLedger(cdmEventJson : String) {
        val response = postJsonToUrl(cdmEventJson, "http://${webHandle.listenAddress}/api/memberApi/persistCDMEvent")
        assertEquals("OK", response.message())
        assertTrue(response.isSuccessful)
    }

    fun processSettlementInstruction(settlementInstructionJson : String) {
        val response = postJsonToUrl(settlementInstructionJson, "http://${webHandle.listenAddress}/api/memberApi/processSettlementInstruction")
        assertEquals("OK", response.message())
        assertTrue(response.isSuccessful)
    }

    fun shareContract(shareWith : String, contractId : String, contractIdScheme : String, issuer : String? = null, partyReference : String? = null) {
        val nodeAddress = webHandle.listenAddress
        val url = "http://$nodeAddress/api/memberApi/shareContract"
        val response = postHeadersToUrl(url, mapOf("shareWith" to shareWith,"contractId" to contractId, "contractIdScheme" to contractIdScheme, "issuer" to issuer, "partyReference" to partyReference).filter { it.value != null } as Map<String,String>)

        assertTrue(response.isSuccessful)
        assertEquals("OK", response.message())
    }

    //vault query related
    fun getLiveContracts() : List<*> {
        return getCdmObjects("liveCDMContracts")
    }

    fun getTerminatedContracts() : List<*> {
        return getCdmObjects("terminatedCDMContracts")
    }

    fun getNovatedContracts() : List<*> {
        return getCdmObjects("novatedCDMContracts")
    }

    fun getResets(contractId : String,contractIdScheme : String,issuer : String? = null,partyReference : String? = null) : List<*> {
        return getContractEvents("CDMResets", contractId, contractIdScheme, issuer, partyReference)
    }

    fun getPayments(contractId : String,contractIdScheme : String,issuer : String? = null,partyReference : String? = null) : List<*> {
        return getContractEvents("CDMPayments", contractId, contractIdScheme, issuer, partyReference)
    }

    fun getAllPayments() : List<*> {
        return getCdmObjects("CDMPaymentsAll")
    }

    fun getAllPayments(status : String) : List<*> {
        return getAllPayments().filter {
            val asMap = it as Map<String,Object>
            asMap.get("paymentStatus").toString().equals(status, true)
        }
    }

    private fun getContractEvents(type : String, contractId : String,contractIdScheme : String,issuer : String? = null,partyReference : String? = null) : List<*> {
        val nodeAddress = webHandle.listenAddress
        val url = "http://$nodeAddress/api/memberApi/$type"
        val response = getFromUrlWithAQueryParameter(url, mapOf("contractId" to contractId, "contractIdScheme" to contractIdScheme, "issuer" to issuer, "partyReference" to partyReference).filter { it.value != null } as Map<String,String>)

        assertTrue(response.isSuccessful)
        assertEquals("OK", response.message())

        val responseInJson = response.body().string()
        return getSuitableGson().fromJson(responseInJson,List::class.java)
    }

    fun getCdmObjects(qualifier : String) : List<*> {
        val nodeAddress = webHandle.listenAddress
        val url = "http://$nodeAddress/api/memberApi/$qualifier"
        val request = Request.Builder().url(url).build()
        val response = getPatientHttpClient().newCall(request).execute()

        assertTrue(response.isSuccessful)
        assertEquals("OK", response.message())

        val responseInJson = response.body().string()
        return getSuitableGson().fromJson(responseInJson,List::class.java)
    }

    //membership related
    fun askForMembership(membershipMetadata : MembershipMetadata) {
        val response = postObjectAsJsonToUrl(membershipMetadata, "http://${webHandle.listenAddress}/api/memberApi/requestMembership")
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
