package net.corda.derivativestradingnetwork.entity

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class ContractIdAndContractIdScheme(val contractId : String, val contractIdScheme : String)