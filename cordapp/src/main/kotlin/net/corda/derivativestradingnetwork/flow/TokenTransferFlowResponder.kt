package net.corda.derivativestradingnetwork.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

@InitiatedBy(TokenTransferFlow::class)
class TokenTransferFlowResponder(private val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    @Throws(FlowException::class)
    override fun call(): SignedTransaction {
        class SignTxFlow constructor(otherPartyFlow: FlowSession, progressTracker: ProgressTracker) : SignTransactionFlow(otherPartyFlow, progressTracker) {

            override fun checkTransaction(stx: SignedTransaction) {

            }
        }

        return subFlow(SignTxFlow(otherPartyFlow, SignTransactionFlow.tracker()))
    }
}