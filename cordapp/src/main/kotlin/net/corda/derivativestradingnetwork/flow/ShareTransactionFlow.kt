package net.corda.derivativestradingnetwork.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.member.support.BusinessNetworkAwareInitiatedFlow
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction

@InitiatingFlow
@StartableByRPC
class ShareTransactionFlow(val shareWith : Party, val signedTransaction: SignedTransaction) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val session = initiateFlow(shareWith)
        subFlow(SendTransactionFlow(session, signedTransaction))
    }
}

@InitiatedBy(ShareTransactionFlow::class)
class ShareTransactionFlowResponder(flowSession : FlowSession) : BusinessNetworkAwareInitiatedFlow<SignedTransaction>(flowSession) {

    @Suspendable
    override fun onOtherPartyMembershipVerified(): SignedTransaction {
        return subFlow(ReceiveTransactionFlow(flowSession, true, StatesToRecord.ALL_VISIBLE))
    }
}