package net.corda.derivativestradingnetwork.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.member.support.BusinessNetworkAwareInitiatedFlow
import net.corda.cdmsupport.eventparsing.createContractIdentifier
import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction

@InitiatingFlow
@StartableByRPC
class ShareContractFlow(val shareWith : Party, val contractId : String, val contractIdScheme : String, val issuer : String? = null, val partyReference : String? = null) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val cdmVaultQuery = DefaultCdmVaultQuery(serviceHub)
        val contractIdentifier = createContractIdentifier(contractId, contractIdScheme, issuer, partyReference)

        val contract = cdmVaultQuery.getCdmContractState(listOf(contractIdentifier))
        val session = initiateFlow(shareWith)
        subFlow(SendTransactionFlow(session, serviceHub.validatedTransactions.getTransaction(contract.ref.txhash)!!))
    }
}

@InitiatedBy(ShareContractFlow::class)
class ShareContractFlowResponder(flowSession : FlowSession) : BusinessNetworkAwareInitiatedFlow<SignedTransaction>(flowSession) {

    @Suspendable
    override fun onOtherPartyMembershipVerified(): SignedTransaction {
        return subFlow(ReceiveTransactionFlow(flowSession, true, StatesToRecord.ALL_VISIBLE))
    }
}