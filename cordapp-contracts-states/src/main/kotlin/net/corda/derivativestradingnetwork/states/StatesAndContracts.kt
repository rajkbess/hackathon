package net.corda.derivativestradingnetwork.states

import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.util.*


class MoneyToken : Contract {
    companion object {
        val CONTRACT_NAME = "net.corda.derivativestradingnetwork.states.MoneyToken"
    }

    open class Commands : CommandData {
        class Issue : Commands()
        class Transfer : Commands()
    }

    override fun verify(tx: LedgerTransaction) {
        val moneyTokenOutputs = tx.outputsOfType<MoneyToken.State>()
        if (moneyTokenOutputs.size != 1) throw IllegalArgumentException("There must be a single output.")
        val moneyTokenOutput = moneyTokenOutputs.single()

        val command = tx.commands.single()

        when (command.value) {
            is Commands.Issue -> {
                //1) amount can't be over 1000
                if (moneyTokenOutput.amount > 1000)
                    throw IllegalArgumentException("Value too high: ${moneyTokenOutput.amount}.")
            }

            is Commands.Transfer -> {
                //2) if between 150 and 1000 then one of the signers has to be the amlauthority
                if (moneyTokenOutput.amount > 150) {
                    val requiredSigners = command.signers
                    val amlAuthority = moneyTokenOutput.amlAuthority
                    val amlAuthoritysKey = amlAuthority.owningKey
                    if (!(requiredSigners.contains(amlAuthoritysKey)))
                        throw IllegalArgumentException("AML is not required signer on transfer of value ${moneyTokenOutput.amount}.")
                }
            }
        }
    }

    data class State(
            val amount: Long,
            val currency: Currency,
            val issuer: Party,
            val holder: Party,
            val amlAuthority: Party) : ContractState {


        override val participants = listOf(holder)
    }
}