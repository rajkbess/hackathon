package net.corda.derivativestradingnetwork.entity

import net.corda.core.serialization.CordaSerializable
import java.time.LocalDate

@CordaSerializable
data class SettlementInstruction(val receiverPartyId : String,
                                 val receiverAccountId : String?,
                                 val receiverName : String?,
                                 val payerPartyId : String,
                                 val payerAccountId : String?,
                                 val payerName : String?,
                                 val amount : Double,
                                 val currency : String,
                                 val paymentDate : LocalDate,
                                 val settlementConfirmation : String) {
}