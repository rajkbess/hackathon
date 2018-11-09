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
        class Redeem : Commands()
    }

    override fun verify(tx : LedgerTransaction) {
       //@todo put your contract checks here
        //1) amount can't be over 1000
        //2) if between 150 and 1000 then one of the signers has to be the amlauthority
    }


    data class State(
            val amount : Long,
            val currency : Currency,
            val issuer : Party,
            val holder : Party,
            val amlAuthority : Party? = null) : ContractState {


        override val participants = listOf(holder)
    }
}