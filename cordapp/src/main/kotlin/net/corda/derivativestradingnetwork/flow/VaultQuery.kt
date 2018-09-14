package net.corda.derivativestradingnetwork.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.cdmsupport.eventparsing.createContractIdentifier
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery
import net.corda.core.flows.*
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class VaultQueryType {
    LIVE_CONTRACTS,
    TERMINATED_CONTRACTS
}

@CordaSerializable
enum class VaultTargetedQueryType {
    RESETS
}

@StartableByRPC
class VaultQueryFlow(val vaultQueryType : VaultQueryType) : FlowLogic<String>() {

    @Suspendable
    override fun call(): String {
        val cdmVaultQuery = DefaultCdmVaultQuery(serviceHub)
        return when (vaultQueryType) {
            VaultQueryType.LIVE_CONTRACTS -> liveContracts(cdmVaultQuery)
            VaultQueryType.TERMINATED_CONTRACTS -> terminatedContracts(cdmVaultQuery)
        }
    }

    @Suspendable
    private fun liveContracts(cdmVaultQuery: DefaultCdmVaultQuery) : String {
        return toJson(cdmVaultQuery.getLiveContracts())
    }

    @Suspendable
    private fun terminatedContracts(cdmVaultQuery: DefaultCdmVaultQuery) : String {
        return toJson(cdmVaultQuery.getTerminatedContracts())
    }

    private fun toJson(objects : List<Any>) : String {
        return objects.fold("[") { left, right -> "${left}${serializeCdmObjectIntoJson(right)},"}.removeSuffix(",") + "]"
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
        }
    }

}
