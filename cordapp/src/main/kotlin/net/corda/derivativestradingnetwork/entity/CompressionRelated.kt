package net.corda.derivativestradingnetwork.entity

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class CompressionRequest(val contractsToCompress : List<ContractIdAndContractIdScheme>)

