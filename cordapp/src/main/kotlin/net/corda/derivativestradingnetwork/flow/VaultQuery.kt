package net.corda.derivativestradingnetwork.flow

import co.paralleluniverse.fibers.Suspendable
import com.google.gson.*
import net.corda.cdmsupport.eventparsing.createContractIdentifier
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.serialization.CordaSerializable
import net.corda.derivativestradingnetwork.entity.CDMContractAndState
import net.corda.derivativestradingnetwork.entity.ContractStatus
import net.corda.derivativestradingnetwork.states.DraftCDMContractState
import org.isda.cdm.Contract
import java.lang.reflect.Type
import com.google.gson.JsonElement



@CordaSerializable
enum class VaultQueryType {
    ALL_CONTRACTS,
    LIVE_CONTRACTS,
    TERMINATED_CONTRACTS,
    NOVATED_CONTRACTS,
    PAYMENTS,
    DRAFT_CONTRACTS
}

@CordaSerializable
enum class VaultTargetedQueryType {
    RESETS,
    PAYMENTS
}

@StartableByRPC
class VaultQueryFlow(val vaultQueryType : VaultQueryType) : FlowLogic<String>() {

    @Suspendable
    override fun call(): String {
        val cdmVaultQuery = DefaultCdmVaultQuery(serviceHub)
        return when (vaultQueryType) {
            VaultQueryType.LIVE_CONTRACTS -> liveContracts(cdmVaultQuery)
            VaultQueryType.TERMINATED_CONTRACTS -> terminatedContracts(cdmVaultQuery)
            VaultQueryType.NOVATED_CONTRACTS -> novatedContracts(cdmVaultQuery)
            VaultQueryType.PAYMENTS -> payments(cdmVaultQuery)
            VaultQueryType.DRAFT_CONTRACTS -> draftContracts()
            VaultQueryType.ALL_CONTRACTS -> allContracts(cdmVaultQuery)
        }
    }

    @Suspendable
    private fun liveContracts(cdmVaultQuery: DefaultCdmVaultQuery) : String {
        return serializeCdmObjectIntoJson(cdmVaultQuery.getLiveContracts())
    }

    @Suspendable
    private fun terminatedContracts(cdmVaultQuery: DefaultCdmVaultQuery) : String {
        return serializeCdmObjectIntoJson(cdmVaultQuery.getTerminatedContracts())
    }

    @Suspendable
    private fun novatedContracts(cdmVaultQuery: DefaultCdmVaultQuery) : String {
        return serializeCdmObjectIntoJson(cdmVaultQuery.getNovatedContracts())
    }

    @Suspendable
    private fun payments(cdmVaultQuery: DefaultCdmVaultQuery) : String {
        return serializeCdmObjectIntoJson(cdmVaultQuery.getPayments())
    }

    @Suspendable
    private fun draftContracts() : String {
        return serializeCdmObjectIntoJson(serviceHub.vaultService.queryBy<DraftCDMContractState>().states.map { it.state.data.contract() })
    }

    @Suspendable
    private fun allContracts(cdmVaultQuery: DefaultCdmVaultQuery) : String {
        val liveContracts = cdmVaultQuery.getLiveContracts().map { CDMContractAndState(it, ContractStatus.LIVE) }
        val terminatedContracts = cdmVaultQuery.getTerminatedContracts().map { CDMContractAndState(it, ContractStatus.TERMINATED) }
        val novatedContracts = cdmVaultQuery.getNovatedContracts().map { CDMContractAndState(it, ContractStatus.NOVATED) }
        val draftContracts = serviceHub.vaultService.queryBy<DraftCDMContractState>().states.map { it.state.data.contract() }.map { CDMContractAndState(it, ContractStatus.DRAFT) }
        val allContracts = listOf(liveContracts, terminatedContracts, novatedContracts, draftContracts).flatten()
        return getSuitableGson().toJson(allContracts)
    }

    private fun getSuitableGson() : Gson {
        return GsonBuilder().registerTypeAdapter(Contract::class.java, object : JsonSerializer<Contract> {

            override fun serialize(src: Contract?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
                val contractAsJson = serializeCdmObjectIntoJson(src!!)
                return Gson().fromJson(contractAsJson, JsonElement::class.java)
            }

        }).create()
    }
}

@StartableByRPC
class VaultTargetedQueryFlow(val vaultTargetedQueryType : VaultTargetedQueryType,val contractId : String,val contractIdScheme : String,val issuer : String? = null,val partyReference : String? = null) : FlowLogic<String>() {

    @Suspendable
    override fun call(): String {
        val cdmVaultQuery = DefaultCdmVaultQuery(serviceHub)
        val contractIdentifier = createContractIdentifier(contractId, contractIdScheme, issuer, partyReference)
        return when (vaultTargetedQueryType) {
            VaultTargetedQueryType.RESETS -> serializeCdmObjectIntoJson(cdmVaultQuery.getResets(contractIdentifier))
            VaultTargetedQueryType.PAYMENTS -> serializeCdmObjectIntoJson(cdmVaultQuery.getPayments(contractIdentifier))
        }
    }

}
