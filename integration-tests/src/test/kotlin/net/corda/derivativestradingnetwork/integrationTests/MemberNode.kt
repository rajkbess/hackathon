package net.corda.derivativestradingnetwork.integrationTests

import com.google.common.reflect.TypeToken
import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.derivativestradingnetwork.entity.*
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverDSL
import okhttp3.Request
import org.isda.cdm.Contract
import org.isda.cdm.StateEnum
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemberNode(driver : DriverDSL, testIdentity : TestIdentity, autoStart : Boolean) : BusinessNetworkNode(driver, testIdentity, autoStart) {

    //cdm events and contracts related
    fun persistDraftCDMContractOnLedger(cdmContractJson : String) {
        val response = postJsonToUrl(cdmContractJson, "http://${webHandle.listenAddress}/api/memberApi/persistDraftCDMContract")
        assertEquals("OK", response.message())
        assertTrue(response.isSuccessful)
    }

    fun compressCDMContractsOnLedger(compressionRequest: CompressionRequest) {
        val response = postObjectAsJsonToUrl(compressionRequest, "http://${webHandle.listenAddress}/api/memberApi/compressCDMContracts")
        assertEquals("OK", response.message())
        assertTrue(response.isSuccessful)
    }

    fun approveDraftCDMContractOnLedger(contractId : String, contractIdScheme : String, expectErrorMessage : String? = null) {
        val nodeAddress = webHandle.listenAddress
        val url = "http://$nodeAddress/api/memberApi/approveDraftCDMContract"
        val contractIdAndContractIdScheme = ContractIdAndContractIdScheme(contractId, contractIdScheme)
        val response = postObjectAsJsonToUrl(contractIdAndContractIdScheme, url)

        if(expectErrorMessage == null) {
            assertTrue(response.isSuccessful)
            assertEquals("OK", response.message())
        } else {
            assertFalse(response.isSuccessful)
            assertTrue(response.body().string().contains(expectErrorMessage))
        }

    }

    fun clearCDMContract(contractId : String, contractIdScheme : String, expectErrorMessage : String? = null) {
        val nodeAddress = webHandle.listenAddress
        val url = "http://$nodeAddress/api/memberApi/clearCDMContract"
        val contractIdAndContractIdScheme = ContractIdAndContractIdScheme(contractId, contractIdScheme)
        val response = postObjectAsJsonToUrl(contractIdAndContractIdScheme, url)

        if(expectErrorMessage == null) {
            assertTrue(response.isSuccessful)
            assertEquals("OK", response.message())
        } else {
            assertFalse(response.isSuccessful)
            assertTrue(response.body().string().contains(expectErrorMessage))
        }

    }

    fun shareContract(shareWith : String, contractId : String, contractIdScheme : String) {
        val nodeAddress = webHandle.listenAddress
        val url = "http://$nodeAddress/api/memberApi/shareContract"
        val contractIdAndContractIdScheme = ContractIdAndContractIdScheme(contractId, contractIdScheme)
        val response = postObjectAsJsonToUrl(ShareRequest(shareWith, contractIdAndContractIdScheme), url)

        assertTrue(response.isSuccessful)
        assertEquals("OK", response.message())
    }

    //vault query related
    fun getContractParents(contractId : String,contractIdScheme : String, contractState : StateEnum?,issuer : String? = null,partyReference : String? = null) : List<Contract> {
        val nodeAddress = webHandle.listenAddress
        val url = "http://$nodeAddress/api/memberApi/CDMContractParents"
        val response = getFromUrlWithAQueryParameter(url, mapOf("contractId" to contractId, "contractIdScheme" to contractIdScheme, "issuer" to issuer, "partyReference" to partyReference, "contractState" to contractState?.toString()).filter { it.value != null } as Map<String,String>)

        assertTrue(response.isSuccessful)
        assertEquals("OK", response.message())

        val responseInJson = response.body().string()
        val desiredType = object : TypeToken<List<Contract>>() {}.type
        return getSuitableGson().fromJson(responseInJson,desiredType)
    }

    fun getAllContracts() : List<CDMContractAndState> {
        val nodeAddress = webHandle.listenAddress
        val url = "http://$nodeAddress/api/memberApi/allCDMContracts"
        val request = Request.Builder().url(url).build()
        val response = getPatientHttpClient().newCall(request).execute()

        assertTrue(response.isSuccessful)
        assertEquals("OK", response.message())

        val responseInJson = response.body().string()
        val desiredType = object : TypeToken<List<CDMContractAndState>>() {}.type
        return getSuitableGson().fromJson(responseInJson,desiredType)
    }

    fun getLiveContracts() : List<Contract> {
        return getCdmObjects("liveCDMContracts")
    }

    fun getDraftContracts() : List<Contract> {
        return getCdmObjects("draftCDMContracts")
    }

    fun getTerminatedContracts() : List<Contract> {
        return getCdmObjects("terminatedCDMContracts")
    }

    fun getNovatedContracts() : List<Contract> {
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

    fun getCdmObjects(qualifier : String) : List<Contract> {
        val nodeAddress = webHandle.listenAddress
        val url = "http://$nodeAddress/api/memberApi/$qualifier"
        val request = Request.Builder().url(url).build()
        val response = getPatientHttpClient().newCall(request).execute()

        assertTrue(response.isSuccessful)
        assertEquals("OK", response.message())

        val responseInJson = response.body().string()
        val desiredType = object : TypeToken<List<Contract>>() {}.type
        return getSuitableGson().fromJson(responseInJson,desiredType)
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
