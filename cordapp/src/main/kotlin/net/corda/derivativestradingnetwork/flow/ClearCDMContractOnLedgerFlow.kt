package net.corda.derivativestradingnetwork.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.common.PartyAndMembershipMetadata
import net.corda.businessnetworks.membership.member.GetMembersFlow
import net.corda.businessnetworks.membership.member.support.BusinessNetworkAwareInitiatedFlow
import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.cdmsupport.CDMContractState
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.createContractIdentifier
import net.corda.cdmsupport.eventparsing.parseContractFromJson
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.network.NetworkMap
import net.corda.cdmsupport.transactionbuilding.CdmTransactionBuilder
import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import org.isda.cdm.*
import java.time.LocalDate
import java.util.*

@InitiatingFlow
@StartableByRPC
class ClearCDMContractOnLedgerFlow(val networkMap : NetworkMap, val clearingHouse : Party, val contractId : String, val contractIdScheme : String, val issuer : String? = null, val partyReference : String? = null) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val contractIdentifier = createContractIdentifier(contractId, contractIdScheme, issuer, partyReference)
        val cdmVaultQuery = DefaultCdmVaultQuery(serviceHub)
        val bilateralContract = cdmVaultQuery.getCdmContractState(listOf(contractIdentifier))
        val cdmClearingEvent = createClearingCdmEvent(bilateralContract.state.data)
        val finalisedTx = persistCDMEventOnTheLedger(cdmClearingEvent)
        sendToRegulators(finalisedTx)
        return finalisedTx
    }

    @Suspendable
    private fun sendToRegulators(signedTransaction: SignedTransaction) {
        val regulators = getPartiesOnThisBusinessNetwork().filter { it.membershipMetadata.role.equals("regulator",true) }.map { it.party }
        regulators.forEach {
            subFlow(ShareTransactionFlow(it, signedTransaction))
        }
    }

    @Suspendable
    private fun persistCDMEventOnTheLedger(eventJson : String) : SignedTransaction {
        val event = parseEventFromJson(eventJson)
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val cdmTransactionBuilder = CdmTransactionBuilder(notary, event, serviceHub, networkMap, DefaultCdmVaultQuery(serviceHub), newTradeOutputContractId = CDMEvent.ID)
        cdmTransactionBuilder.verify(serviceHub)
        val signedByMe = serviceHub.signInitialTransaction(cdmTransactionBuilder)
        val counterPartySessions = cdmTransactionBuilder.getPartiesToSign().minus(ourIdentity).map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(signedByMe, counterPartySessions))
        return subFlow(FinalityFlow(stx))
    }


    @Suspendable
    private fun getMembershipMetadata(party : Party) : MembershipMetadata {
        return getPartiesOnThisBusinessNetwork().filter { it.party == party }.single().membershipMetadata
    }

    @Suspendable
    private fun getPartiesOnThisBusinessNetwork() : List<PartyAndMembershipMetadata> {
        return subFlow(GetMembersFlow(false))
    }

    private fun createNewTrade(bilateralContract : Contract, clearingHouse : MembershipMetadata, steppingOutParty : MembershipMetadata, newContractIdentifier : String, newContractIdentifierScheme : String) : String {
        val tradeJson = serializeCdmObjectIntoJson(bilateralContract)
        val tradeJsonWithNewParty = tradeJson.replace(steppingOutParty.partyId, clearingHouse.partyId)
        val tradeContract = parseContractFromJson(tradeJsonWithNewParty)
        val tradeContractBuilder = tradeContract.toBuilder()
        tradeContractBuilder.contractIdentifier.clear()
        tradeContractBuilder.addContractIdentifier(createPartyContractIdentifier(newContractIdentifier, newContractIdentifierScheme))
        val newTrade = tradeContractBuilder.build()
        return serializeCdmObjectIntoJson(newTrade)
    }

    private fun createPartyContractIdentifier(contractId : String, contractIdScheme : String, issuer : String? = null, partyReference : String? = null) : PartyContractIdentifier {
        val contractIdentifier = createContractIdentifier(contractId, contractIdScheme, issuer, partyReference)

        val partyContractIdentifierBuilder = PartyContractIdentifier.PartyContractIdentifierBuilder()
        partyContractIdentifierBuilder.setIdentifierValue(contractIdentifier.identifierValue)
        return partyContractIdentifierBuilder.build()
    }

    private fun createBilateralTradeAfter(bilateralContract : Contract) : String {
        return serializeCdmObjectIntoJson(bilateralContract.toBuilder().setState(StateEnum.NOVATED).build())
    }

    @Suspendable
    private fun createClearingCdmEvent(bilateralContract : CDMContractState) : String {

        val eventDate = LocalDate.now().toString()
        val eventIdentifier = "${serviceHub.myInfo.legalIdentities.first().name.organisation}-${Date().time}"
        val party1MembershipMetadata = getMembershipMetadata(bilateralContract.participants[0])
        val party2MembershipMetadata = getMembershipMetadata(bilateralContract.participants[1])
        val clearerMembershipMetadata = getMembershipMetadata(clearingHouse)
        val bilateralContractId = bilateralContract.contract().contractIdentifier.single().identifierValue.identifier
        val bilateralContractIdScheme = bilateralContract.contract().contractIdentifier.single().identifierValue.identifierScheme
        val newTrade1 = createNewTrade(bilateralContract.contract(), clearerMembershipMetadata, party1MembershipMetadata,bilateralContractId + "_A",bilateralContractIdScheme)
        val newTrade2 = createNewTrade(bilateralContract.contract(), clearerMembershipMetadata, party2MembershipMetadata, bilateralContractId + "_B",bilateralContractIdScheme)
        val bilateralTradeAfterJson = createBilateralTradeAfter(bilateralContract.contract())

        return """{
              "action" : "NEW",
              "effectiveDate" : "2018-11-19",
              "eventDate" : "#EVENT_DATE#",
              "eventEffect" : {
                "contract" : [ "dummyEventEffect1", "dummyEventEffect2" ],
                "effectedContractReference" : [ "dummyEventEffect3" ],
                "effectedEvent" : "dummyEventEffect4"
              },
              "eventIdentifier" : {
                "identifierValue" : {
                  "identifier" : "#EVENT_IDENTIFIER#"
                }
              },
              "intent" : "NOVATION",
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
              }, {
                "legalEntity" : {
                  "entityId" : "#PARTY_3_LEGAL_ENTITY_ID#",
                  "name" : "#PARTY_3_LEGAL_ENTITY_NAME#"
                },
                "partyId" : [ "#PARTY_3_ID#" ],
                "partyIdScheme" : "http://www.fpml.org/coding-scheme/external/iso17442"
              } ],
              "primitive" : {
                "newTrade" : [ {"contract" : #NOVATED_TRADE_A#}, {"contract" : #NOVATED_TRADE_B#} ],
                "quantityChange" : [ {
                  "after" : {
                    "contract" : [ #BILATERAL_TRADE_AFTER# ]
                  },
                  "before" : {
                    "contractReference" : [ {
                      "identifierValue" : {
                        "identifier" : "#BILATERAL_TRADE_CONTRACT_IDENTIFIER#",
                        "identifierScheme" : "#BILATERAL_TRADE_CONTRACT_IDENTIFIER_SCHEME#"
                      },
                      "version" : 1,
                      "rosettaKey" : "7c9da2"
                    } ]
                  },
                  "change" : [ {
                    "quantity" : {
                      "amount" : 500000000.00
                    }
                  } ]
                } ]
              },
              "timestamp" : {
                "creationTimestamp" : "2018-11-16T10:55:50.533"
              },
              "rosettaKey" : "18a05371"
            }""".replace("#EVENT_DATE#",eventDate)
                .replace("#EVENT_IDENTIFIER#",eventIdentifier)
                .replace("#PARTY_1_LEGAL_ENTITY_ID#",party1MembershipMetadata.legalEntityId)
                .replace("#PARTY_1_LEGAL_ENTITY_NAME#",party1MembershipMetadata.name)
                .replace("#PARTY_1_ID#",party1MembershipMetadata.partyId)
                .replace("#PARTY_2_LEGAL_ENTITY_ID#",party2MembershipMetadata.legalEntityId)
                .replace("#PARTY_2_LEGAL_ENTITY_NAME#",party2MembershipMetadata.name)
                .replace("#PARTY_2_ID#",party2MembershipMetadata.partyId)
                .replace("#PARTY_3_LEGAL_ENTITY_ID#",clearerMembershipMetadata.legalEntityId)
                .replace("#PARTY_3_LEGAL_ENTITY_NAME#",clearerMembershipMetadata.name)
                .replace("#PARTY_3_ID#",clearerMembershipMetadata.partyId)
                .replace("#NOVATED_TRADE_A#", newTrade1)
                .replace("#NOVATED_TRADE_B#", newTrade2)
                .replace("#BILATERAL_TRADE_AFTER#",bilateralTradeAfterJson)
                .replace("#BILATERAL_TRADE_CONTRACT_IDENTIFIER#", bilateralContractId)
                .replace("#BILATERAL_TRADE_CONTRACT_IDENTIFIER_SCHEME#", bilateralContractIdScheme)

    }


}

@InitiatedBy(ClearCDMContractOnLedgerFlow::class)
class ClearCDMContractOnLedgerFlowResponder(flowSession : FlowSession) : BusinessNetworkAwareInitiatedFlow<SignedTransaction>(flowSession) {

    @Suspendable
    override fun onOtherPartyMembershipVerified(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                //if you are the clearing house look at the notional
                if(areWeTheClearingHouse()) {
                    verifyNotionalNotBiggerThan(stx, 1000000000)
                }
            }
        }

        return subFlow(signTransactionFlow)
    }

    @Suspendable
    private fun verifyNotionalNotBiggerThan(stx: SignedTransaction, limit : Long) {
        val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
        val newContracts = ledgerTx.outputStates.filter { it is CDMContractState }.map { it as CDMContractState }
        newContracts.forEach { verifyNotionalNotBiggerThan(it.contract(), limit) }
    }

    private fun verifyNotionalNotBiggerThan(contract : Contract, limit : Long) {
        contract.contractualProduct.economicTerms.payout.interestRatePayout.forEach {
            if(it.quantity.notionalSchedule.notionalStepSchedule.initialValue.toLong() > limit) {
                val contractId = contract.contractIdentifier.single().identifierValue.identifier
                throw FlowException("The notional on the contract $contractId exceeds limit")
            }
        }
    }

    @Suspendable
    private fun areWeTheClearingHouse() : Boolean {
        val members = subFlow(GetMembersFlow(false))
        val us = members.filter { it.party == ourIdentity }.single()
        return us.membershipMetadata.role.equals("ccp",true)
    }
}