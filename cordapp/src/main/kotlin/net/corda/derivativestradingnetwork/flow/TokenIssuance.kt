package net.corda.derivativestradingnetwork.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.derivativestradingnetwork.entity.IssuanceRequest
import java.util.*

@InitiatingFlow
@StartableByRPC
class UserIssuanceRequestFlow(val amount : Long, val currency : Currency, val ourBank : Party) : FlowLogic<SignedTransaction>() {

    companion object {
        object SENDING_REQUEST_TO_BANK : ProgressTracker.Step("Sending request to bank")


        fun tracker() = ProgressTracker(
                SENDING_REQUEST_TO_BANK
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        logger.info("Starting UserIssuanceRequestFlow")
        progressTracker.currentStep = SENDING_REQUEST_TO_BANK

        val session = initiateFlow(ourBank)
        session.send(IssuanceRequest(amount, currency))

        throw UnsupportedOperationException()
    }
}

@InitiatedBy(UserIssuanceRequestFlow::class)
class UserIssuanceRequestResponderFlow(val flowSession : FlowSession) : FlowLogic<SignedTransaction>() {


    override fun call(): SignedTransaction {
        logger.info("Initiated by user")

        val requestFromClient = flowSession.receive<IssuanceRequest>().unwrap { it }

        logger.info("Received request $requestFromClient")

        throw UnsupportedOperationException()
    }
}