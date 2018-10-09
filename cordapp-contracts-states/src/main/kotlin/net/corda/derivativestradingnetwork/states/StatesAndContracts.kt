package net.corda.derivativestradingnetwork.states

import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

class DraftCDMContract : Contract {
    companion object {
        val ID = "net.corda.derivativestradingnetwork.states.DraftCDMContract"
    }

    override fun verify(tx: LedgerTransaction) {
        // TODO: Write the verify logic.
    }

    interface Commands : CommandData {
        class Draft() : Commands
    }
}

data class DraftCDMContractState(
        private val contractJson: String,
        override val participants: List<Party>,
        override val linearId: UniqueIdentifier) : LinearState {

    fun contract() : org.isda.cdm.Contract {
        val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
        return rosettaObjectMapper.readValue<org.isda.cdm.Contract>(contractJson, org.isda.cdm.Contract::class.java)
    }
}