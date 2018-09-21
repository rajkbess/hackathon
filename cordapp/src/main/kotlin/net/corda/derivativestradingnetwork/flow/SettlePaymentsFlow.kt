package net.corda.derivativestradingnetwork.flow

import co.paralleluniverse.fibers.Suspendable
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper
import net.corda.businessnetworks.membership.member.support.BusinessNetworkAwareInitiatedFlow
import net.corda.cdmsupport.CDMContractState
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.PaymentState
import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.derivativestradingnetwork.entity.SettlementInstruction
import org.isda.cdm.Payment
import org.isda.cdm.PaymentStatusEnum

@StartableByRPC
class SettlePaymentsFlow(val settlementInstructions : List<SettlementInstruction>) : FlowLogic<List<SignedTransaction>>() {

    @Suspendable
    override fun call(): List<SignedTransaction> {
        val vaultQuery = DefaultCdmVaultQuery(serviceHub)
        val allPayments = vaultQuery.getPaymentStates()
        return settlementInstructions.flatMap {
            processSettlementInstruction(it, allPayments)
        }
    }

    @Suspendable
    private fun processSettlementInstruction(settlementInstruction : SettlementInstruction, allPayments : List<StateAndRef<PaymentState>>) : List<SignedTransaction> {
        val paymentsToSettleWithThisInstruction = allPayments.filter {
            val payment = it.state.data.payment()
            val payerParty = payment.payerReceiver.payerPartyReference
            val receiverParty = payment.payerReceiver.receiverPartyReference
            val paymentStatus = payment.paymentStatus.toString()
            val currency = payment.paymentAmount.currency

            settlementInstruction.currency == currency && paymentStatus.equals("PENDING",true) && setOf(settlementInstruction.payerPartyId,settlementInstruction.receiverPartyId) == setOf(payerParty, receiverParty)
        }

        return paymentsToSettleWithThisInstruction.map {
            subFlow(SettlePaymentFlow(it, settlementInstruction.settlementReference))
        }
    }
}

@InitiatingFlow
@StartableByRPC
class SettlePaymentFlow(val paymentState : StateAndRef<PaymentState>,val settlementReference : String) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val paymentParentTx = serviceHub.validatedTransactions.getTransaction(paymentState.ref.txhash)!!.toLedgerTransaction(serviceHub, false)
        val statesToReference = getContractReferenceStates(paymentParentTx) + getContractIndexedStates(paymentParentTx)
        val referenceStates = statesToReference.map{ ReferencedStateAndRef(it) }

        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(paymentState)
        referenceStates.forEach { txBuilder.addReferenceState(it) }
        txBuilder.addCommand(CDMEvent.Commands.SettlePayment(), paymentState.state.data.participants.map { it.owningKey })
        txBuilder.addOutputState(createOutputState(),CDMEvent.ID)
        txBuilder.verify(serviceHub)
        val signedByMe = serviceHub.signInitialTransaction(txBuilder)
        val counterPartySessions = paymentState.state.data.participants.minus(ourIdentity).map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(signedByMe, counterPartySessions))
        return subFlow(FinalityFlow(stx))
    }

    @Suspendable
    private fun getContractReferenceStates(tx : LedgerTransaction) : List<StateAndRef<CDMContractState>> {
        return tx.references.filter { it.state.data is CDMContractState }.map { it as StateAndRef<CDMContractState> }
    }

    @Suspendable
    private fun getContractIndexedStates(tx : LedgerTransaction) : List<StateAndRef<CDMContractState>> {
        val quantityChangeCommands = tx.commands.filter { it.value is CDMEvent.Commands.QuantityChange }.map { it.value as CDMEvent.Commands.QuantityChange }
        val quantityChangeCommandsOfInterest = quantityChangeCommands.filter { paymentState.state.data.inTransactionLineageInputIndices.contains(it.inputIndex) }
        return quantityChangeCommandsOfInterest.map { tx.outRef<CDMContractState>(it.outputIndex) }
    }

    @Suspendable
    private fun createOutputState() : PaymentState {
        val paymentState = paymentState.state.data
        val payment = paymentState.payment()
        val paymentSettled = markAsSettled(payment)
        val paymentSettledAsJson = paymentToJson(paymentSettled)
        return paymentState.copy(inTransactionLineageInputIndices = emptyList(), paymentJson = paymentSettledAsJson)
    }

    private fun markAsSettled(payment : Payment) : Payment {
        val paymentBuilder = payment.toBuilder()
        paymentBuilder.settlementReference = settlementReference
        paymentBuilder.paymentStatus = PaymentStatusEnum.SETTLED
        return paymentBuilder.build()
    }

    private fun paymentToJson(payment : Payment) : String {
        val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
        return rosettaObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payment)
    }

}

@InitiatedBy(SettlePaymentFlow::class)
class SettlePaymentFlowResponder(flowSession : FlowSession) : BusinessNetworkAwareInitiatedFlow<SignedTransaction>(flowSession) {

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