package net.corda.derivativestradingnetwork.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.member.support.BusinessNetworkAwareInitiatedFlow
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.network.NetworkMap
import net.corda.cdmsupport.transactionbuilding.CdmTransactionBuilder
import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.derivativestradingnetwork.entity.SettlementInstruction
import org.isda.cdm.Payment

@InitiatingFlow
@StartableByRPC
class SettlePaymentsFlow(val settlementInstructions : List<SettlementInstruction>) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val vaultQuery = DefaultCdmVaultQuery(serviceHub)
        val allPayments = vaultQuery.getPayments()
        settlementInstructions.forEach {
            processSettlementInstruction(it, allPayments)
        }
//        val event = parseEventFromJson(eventJson)
//        val notary = serviceHub.networkMapCache.notaryIdentities.first()
//        val cdmTransactionBuilder = CdmTransactionBuilder(notary, event, serviceHub, networkMap, DefaultCdmVaultQuery(serviceHub))
//        cdmTransactionBuilder.verify(serviceHub)
//        val signedByMe = serviceHub.signInitialTransaction(cdmTransactionBuilder)
//        val counterPartySessions = cdmTransactionBuilder.getPartiesToSign().minus(ourIdentity).map { initiateFlow(it) }
//        val stx = subFlow(CollectSignaturesFlow(signedByMe, counterPartySessions))
//        return subFlow(FinalityFlow(stx))
        throw RuntimeException("meh")
    }

    private fun processSettlementInstruction(settlementInstruction : SettlementInstruction, allPayments : List<Payment>) {
        val paymentsToProcess = allPayments.filter {
            val payerParty = it.payerReceiver.payerPartyReference.first()
            val receiverParty = it.payerReceiver.receiverPartyReference.first()
            val paymentStatus = it.paymentStatus.toString()
            val currency = it.paymentAmount.currency

            settlementInstruction.currency == currency && paymentStatus.equals("PENDING",true) && setOf(settlementInstruction.payerPartyId,settlementInstruction.receiverPartyId) == setOf(payerParty, receiverParty)
        }
    }

    private fun settlePayment() {

        //you will have to make it a reference state
    }
}

@InitiatedBy(SettlePaymentsFlow::class)
class SettlePaymentsFlowResponder(flowSession : FlowSession) : BusinessNetworkAwareInitiatedFlow<SignedTransaction>(flowSession) {

    @Suspendable
    override fun onOtherPartyMembershipVerified(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                //always sign
            }
        }

        return subFlow(signTransactionFlow)
    }
}