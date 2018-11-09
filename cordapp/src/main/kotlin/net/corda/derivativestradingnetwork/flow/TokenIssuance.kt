package net.corda.derivativestradingnetwork.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.derivativestradingnetwork.entity.IssuanceRequest
import net.corda.derivativestradingnetwork.states.MoneyToken
import java.util.*

@InitiatingFlow
@StartableByRPC
class UserIssuanceRequestFlow(val amount: Long, val currency: Currency, val ourBank: Party) : FlowLogic<Unit>() {

    companion object {
        object SENDING_REQUEST_TO_BANK : ProgressTracker.Step("Sending request to bank")

        fun tracker() = ProgressTracker(
                SENDING_REQUEST_TO_BANK
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() {
        logger.info("Starting UserIssuanceRequestFlow")
        progressTracker.currentStep = SENDING_REQUEST_TO_BANK

        val session = initiateFlow(ourBank)
        session.send(IssuanceRequest(amount, currency))
    }
}

@InitiatedBy(UserIssuanceRequestFlow::class)
class UserIssuanceRequestResponderFlow(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        logger.info("Initiated by user")

        val requestFromClient = flowSession.receive<IssuanceRequest>().unwrap { it }

        logger.info("Received request $requestFromClient")

        val amount = requestFromClient.amount
        val currency = requestFromClient.currency

        return subFlow(TokenIssuanceFlow(amount, currency, flowSession.counterparty))
    }
}

@InitiatingFlow
class TokenIssuanceFlow(val amount: Long, val currency: Currency, val user: Party) : FlowLogic<SignedTransaction>() {

    companion object {
        object PROPOSING_TRANSACTION_TO_ISSUER : ProgressTracker.Step("Proposing transaction to issuer")

        fun tracker() = ProgressTracker(
                PROPOSING_TRANSACTION_TO_ISSUER
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        logger.info("Starting TokenIssuanceFlow")
        progressTracker.currentStep = PROPOSING_TRANSACTION_TO_ISSUER

        val issuer = getIssuer()
        val sessionWithIssuer = initiateFlow(issuer)

        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        //@todo create the transaction here with the output state of the money token, the holder being the bank
        val txBuilder = TransactionBuilder(notary)
                .addOutputState(MoneyToken.State(amount, currency, issuer, ourIdentity), MoneyToken.CONTRACT_NAME)
                .addCommand(MoneyToken.Commands.Issue(), ourIdentity.owningKey, issuer.owningKey)

        txBuilder.verify(serviceHub)

        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)
        val fullySignedTransaction = subFlow(CollectSignaturesFlow(partSignedTx, listOf(sessionWithIssuer)))
        val finalisedTx = subFlow(FinalityFlow(fullySignedTransaction))

        //@todo and when that's signed by the ECB and notarized then invoke another flow that will change the holder from the bank to the user
        val ledgerTx = finalisedTx.toLedgerTransaction(serviceHub)
        val moneyStateInput = ledgerTx.outRef<MoneyToken.State>(0)

        val txBuilder2 = TransactionBuilder(notary)
                .addInputState(moneyStateInput)
                .addOutputState(MoneyToken.State(amount, currency, issuer, user), MoneyToken.CONTRACT_NAME)
                .addCommand(MoneyToken.Commands.Transfer(), ourIdentity.owningKey)

        txBuilder2.verify(serviceHub)
        val signedTx = serviceHub.signInitialTransaction(txBuilder2)
        return subFlow(FinalityFlow(signedTx))
    }

    @Suspendable
    private fun getIssuer(): Party {
        return serviceHub.networkMapCache.allNodes.map { it.legalIdentities.first() }.single { it.name.organisation.contains("issuer", true) }
    }
}

@InitiatedBy(TokenIssuanceFlow::class)
class TokenIssuanceResponderFlow(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        //@todo this is the point where the issuer (ECB) has to sign the transaction coming from the commercial bank. Speak to Cais.
        val signTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) {

            }
        }

        return subFlow(signTransactionFlow)
    }
}