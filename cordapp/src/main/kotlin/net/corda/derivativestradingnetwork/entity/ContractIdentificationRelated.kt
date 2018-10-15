package net.corda.derivativestradingnetwork.entity

import net.corda.core.serialization.CordaSerializable
import org.isda.cdm.Contract

enum class ContractStatus {
    LIVE,
    TERMINATED,
    NOVATED,
    DRAFT
}

@CordaSerializable
data class ContractIdAndContractIdScheme(val contractId : String, val contractIdScheme : String)

@CordaSerializable
data class CDMContractAndState(val cdmContract : Contract, val contractStatus : ContractStatus)