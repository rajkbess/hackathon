package net.corda.derivativestradingnetwork.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.common.PartyAndMembershipMetadata
import net.corda.businessnetworks.membership.member.GetMembersFlow
import net.corda.businessnetworks.membership.member.support.BusinessNetworkAwareInitiatedFlow
import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.CdmContractNotFound
import net.corda.cdmsupport.MultipleCdmContractsFound
import net.corda.cdmsupport.eventparsing.createContractIdentifier
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.extensions.matchAny
import net.corda.cdmsupport.network.NetworkMap
import net.corda.cdmsupport.transactionbuilding.CdmTransactionBuilder
import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.derivativestradingnetwork.states.DraftCDMContractState
import org.isda.cdm.ContractIdentifier
import java.time.LocalDate
import java.util.*

@InitiatingFlow
@StartableByRPC
class ApproveDraftCDMContractOnLedgerFlow(val networkMap : NetworkMap, val contractId : String, val contractIdScheme : String, val issuer : String? = null, val partyReference : String? = null, val pageSize : Int = 1000) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val contractIdentifier = createContractIdentifier(contractId, contractIdScheme, issuer, partyReference)
        val draftContract = findTheDraftContract(contractIdentifier)
        val cdmNewTradeEvent = wrapInCdmNewTradeEvent(draftContract.state.data)
        return persistOnTheLedger(cdmNewTradeEvent, draftContract)
    }

    @Suspendable
    private fun persistOnTheLedger(eventJson : String, draftContractToConsume : StateAndRef<DraftCDMContractState>) : SignedTransaction {
        val event = parseEventFromJson(eventJson)
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val cdmTransactionBuilder = CdmTransactionBuilder(notary, event, serviceHub, networkMap, DefaultCdmVaultQuery(serviceHub),CDMEvent.ID)
        cdmTransactionBuilder.addInputState(draftContractToConsume)

        cdmTransactionBuilder.verify(serviceHub)
        val signedByMe = serviceHub.signInitialTransaction(cdmTransactionBuilder)
        val counterPartySessions = cdmTransactionBuilder.getPartiesToSign().minus(ourIdentity).map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(signedByMe, counterPartySessions))
        return subFlow(FinalityFlow(stx))
    }


    private fun findTheDraftContract(resolvedContractIdentifier : ContractIdentifier) : StateAndRef<DraftCDMContractState> {
        val allDraftContracts = serviceHub.vaultService.queryBy<DraftCDMContractState>(QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED), PageSpecification(DEFAULT_PAGE_NUM, pageSize)).states
        val matchingDraftContracts = allDraftContracts.filter { it.state.data.contract().matchAny(listOf(resolvedContractIdentifier)) }

        when {
            matchingDraftContracts.isEmpty() -> throw CdmContractNotFound(listOf(resolvedContractIdentifier))
            matchingDraftContracts.size > 1 -> throw MultipleCdmContractsFound(listOf(resolvedContractIdentifier))
            else -> return matchingDraftContracts.get(0)
        }
    }

    @Suspendable
    fun getMembershipMetadata(party : Party) : MembershipMetadata {
        return getPartiesOnThisBusinessNetwork().filter { it.party == party }.single().membershipMetadata
    }

    @Suspendable
    fun getPartiesOnThisBusinessNetwork() : List<PartyAndMembershipMetadata> {
        return subFlow(GetMembersFlow(false))
    }

    @Suspendable
    private fun wrapInCdmNewTradeEvent(draftContract : DraftCDMContractState) : String {

        val eventDate = LocalDate.now().toString()
        val eventIdentifier = "${serviceHub.myInfo.legalIdentities.first().name.organisation}-${Date().time}"
        val party1MembershipMetadata = getMembershipMetadata(draftContract.participants[0])
        val party2MembershipMetadata = getMembershipMetadata(draftContract.participants[1])
        val draftContractAsJson = serializeCdmObjectIntoJson(draftContract.contract())

        return """{
              "action" : "NEW",
              "eventDate" : "#EVENT_DATE#",
              "eventEffect" : {
                "contract" : [ "dummyEventEffect1" ],
                "effectedEvent" : "dummyEventEffect2"
              },
              "eventIdentifier" : {
                "identifierValue" : {
                  "identifier" : "#EVENT_IDENTIFIER#"
                }
              },
              "intent" : "NEW_TRADE",
              "party" : [ {
                "legalEntity" : {
                  "entityId" : "#PARTY_1_LEGAL_ENTITY_ID#",
                  "name" : "#PARTY_1_LEGAL_ENTITY_NAME#"
                },
                "partyId" : [ "#PARTY_1_ID#" ],
                "partyIdScheme" : "http://www.fpml.org/coding-scheme/external/iso17442"
              }, {
                "legalEntity" : {
                  "entityId" : "#PARTY_2_LEGAL_ENTITY_ID#",
                  "name" : "#PARTY_2_LEGAL_ENTITY_NAME#"
                },
                "partyId" : [ "#PARTY_2_ID#" ],
                "partyIdScheme" : "http://www.fpml.org/coding-scheme/external/iso17442"
              } ],
              "primitive" : {
                "newTrade" : [ {
                  "contract" : #CONTRACT#
                } ]
              },
              "timestamp" : {
                "creationTimestamp" : "2018-10-17T21:40:56.713"
              },
              "rosettaKey" : "da0c376e"
            }""".replace("#EVENT_DATE#",eventDate)
                .replace("#EVENT_IDENTIFIER#",eventIdentifier)
                .replace("#PARTY_1_LEGAL_ENTITY_ID#",party1MembershipMetadata.legalEntityId)
                .replace("#PARTY_1_LEGAL_ENTITY_NAME#",party1MembershipMetadata.name)
                .replace("#PARTY_1_ID#",party1MembershipMetadata.partyId)
                .replace("#PARTY_2_LEGAL_ENTITY_ID#",party2MembershipMetadata.legalEntityId)
                .replace("#PARTY_2_LEGAL_ENTITY_NAME#",party2MembershipMetadata.name)
                .replace("#PARTY_2_ID#",party2MembershipMetadata.partyId)
                .replace("#CONTRACT#",draftContractAsJson)

    }


}

@InitiatedBy(ApproveDraftCDMContractOnLedgerFlow::class)
class ApproveDraftCDMContractOnLedgerFlowResponder(flowSession : FlowSession) : BusinessNetworkAwareInitiatedFlow<SignedTransaction>(flowSession) {

    @Suspendable
    override fun onOtherPartyMembershipVerified(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                //don't sign this unless it's proposed by us
                if(ourIdentity != (stx.toLedgerTransaction(serviceHub,false).inputStates.find { it is DraftCDMContractState } as DraftCDMContractState).proposer) {
                    throw FlowException("We never proposed this draft")
                }
            }
        }

        return subFlow(signTransactionFlow)
    }
}