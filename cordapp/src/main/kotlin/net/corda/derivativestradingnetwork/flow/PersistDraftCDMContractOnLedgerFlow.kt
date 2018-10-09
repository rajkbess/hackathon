package net.corda.derivativestradingnetwork.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.member.support.BusinessNetworkAwareInitiatedFlow
import net.corda.cdmsupport.eventparsing.parseContractFromJson
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.extensions.getPartyReferences
import net.corda.cdmsupport.network.NetworkMap
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.derivativestradingnetwork.states.DraftCDMContract
import net.corda.derivativestradingnetwork.states.DraftCDMContractState
import org.isda.cdm.Contract

@InitiatingFlow
@StartableByRPC
class PersistDraftCDMContractOnLedgerFlow(val cdmContractJson : String, val networkMap : NetworkMap) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val contract = parseContractFromJson(cdmContractJson)
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val txTransactionBuilder = TransactionBuilder(notary)
        val outputState = createOutputState(contract)
        val outputStateParticipants = outputState.participants
        txTransactionBuilder.addOutputState(outputState,DraftCDMContract.ID)
        txTransactionBuilder.addCommand(DraftCDMContract.Commands.Draft(),outputStateParticipants.map { it.owningKey })
        txTransactionBuilder.verify(serviceHub)
        val signedByMe = serviceHub.signInitialTransaction(txTransactionBuilder)
        val counterPartySessions = outputStateParticipants.minus(ourIdentity).map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(signedByMe, counterPartySessions))
        return subFlow(FinalityFlow(stx))
    }

    private fun createOutputState(contract : Contract) : DraftCDMContractState {
        val json = serializeCdmObjectIntoJson(contract)
        val participants = contract.getPartyReferences().map { networkMap.partyIdToCordaParty[it] ?: throw FlowException("Cannot find party for party id $it") }
        return DraftCDMContractState(ourIdentity,json, participants, UniqueIdentifier())
    }
}

@InitiatedBy(PersistDraftCDMContractOnLedgerFlow::class)
class PersistDraftCDMContractOnLedgerFlowResponder(flowSession : FlowSession) : BusinessNetworkAwareInitiatedFlow<SignedTransaction>(flowSession) {

    @Suspendable
    override fun onOtherPartyMembershipVerified(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                //make sure no one pretends we are the proposer
                if(ourIdentity == (stx.toLedgerTransaction(serviceHub,false).outputStates.single() as DraftCDMContractState).proposer) {
                    throw FlowException("Other side is putting me down as a proposer of this draft")
                }
            }
        }

        return subFlow(signTransactionFlow)
    }
}