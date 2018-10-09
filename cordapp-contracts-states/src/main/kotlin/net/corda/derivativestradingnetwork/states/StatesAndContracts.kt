package net.corda.derivativestradingnetwork.states

import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper
import net.corda.cdmsupport.CDMContractState
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

class DraftCDMContract : Contract {
    companion object {
        val ID = "net.corda.derivativestradingnetwork.states.DraftCDMContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<CommandData>()

        when (command.value) {
            is DraftCDMContract.Commands.Draft -> return
            is CDMEvent.Commands.NewTrade -> requireThat {
                val input = tx.inputs.single()
                val inputState = input.state.data as DraftCDMContractState
                val output = tx.outputs.filter { it.data is CDMContractState }.single()
                val outputState = output.data as CDMContractState

                "Draft input state must be the same as live output state" using (serializeCdmObjectIntoJson(inputState.contract()) == serializeCdmObjectIntoJson(outputState.contract()))
            }
            else -> throw IllegalArgumentException("Unsupported command ${command.value}")
        }


    }

    interface Commands : CommandData {
        class Draft() : Commands
    }
}

data class DraftCDMContractState(
        val proposer : Party,
        private val contractJson: String,
        override val participants: List<Party>,
        override val linearId: UniqueIdentifier) : LinearState {

    fun contract() : org.isda.cdm.Contract {
        val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
        return rosettaObjectMapper.readValue<org.isda.cdm.Contract>(contractJson, org.isda.cdm.Contract::class.java)
    }
}