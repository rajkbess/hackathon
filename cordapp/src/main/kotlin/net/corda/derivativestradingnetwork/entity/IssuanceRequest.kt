package net.corda.derivativestradingnetwork.entity

import net.corda.core.serialization.CordaSerializable
import java.util.*

@CordaSerializable
data class IssuanceRequest(val amount : Long, val currency : Currency)