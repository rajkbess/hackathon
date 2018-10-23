package net.corda.derivativestradingnetwork

import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.ResetState
import net.corda.cdmsupport.transactionbuilding.CommandsCreator
import net.corda.core.contracts.Command
import net.corda.core.identity.Party
import java.math.BigDecimal
import java.time.LocalDate

class DtnCommandsCreator(val oracleParty : Party, val fixingDate : LocalDate, val fixingRate : BigDecimal) : CommandsCreator() {

    override fun createReset(resetState: ResetState): Command<CDMEvent.Commands.Reset> {
        val resetCommandData = CDMEvent.Commands.Reset(fixingDate,fixingRate)
        return Command(resetCommandData, resetState.participants.map { it.owningKey } + oracleParty.owningKey)
    }
}

