package net.corda.derivativestradingnetwork.entity

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class ShareRequest(val shareWith : String, val contractToShare: ContractIdAndContractIdScheme)