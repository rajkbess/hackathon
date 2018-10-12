package net.corda.derivativestradingnetwork.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.common.PartyAndMembershipMetadata
import net.corda.businessnetworks.membership.member.GetMembersFlow
import net.corda.businessnetworks.membership.member.support.BusinessNetworkAwareInitiatedFlow
import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.cdmsupport.CDMContractState
import net.corda.cdmsupport.eventparsing.createContractIdentifier
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.network.NetworkMap
import net.corda.cdmsupport.transactionbuilding.CdmTransactionBuilder
import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.derivativestradingnetwork.entity.CompressionRequest
import org.isda.cdm.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@InitiatingFlow
@StartableByRPC
class CompressCDMContractsOnLedgerFlow(val networkMap : NetworkMap, val compressionRequest : CompressionRequest) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val contractsToCompress = getContractsToCompress(compressionRequest)
        val compressionCdmEvent = createCompressionCdmEvent(contractsToCompress)
        val finalisedTx = persistCDMEventOnTheLedger(compressionCdmEvent)
        sendToRegulators(finalisedTx)
        return finalisedTx

    }

    @Suspendable
    private fun getContractsToCompress(compressionRequest: CompressionRequest) : List<CDMContractState> {
        val cdmVaultQuery = DefaultCdmVaultQuery(serviceHub)
        val contractIdentifiers = compressionRequest.contractsToCompress.map { createContractIdentifier(it.contractId, it.contractIdScheme) }
        val ret = contractIdentifiers.map { cdmVaultQuery.getCdmContractState(listOf(it)).state.data }
        if (ret.any { it.contract().state in listOf(StateEnum.NOVATED, StateEnum.TERMINATED, StateEnum.EXERCISED, StateEnum.ALLOCATED) }) {
            throw FlowException("The trades to be compressed must be LIVE")
        }
        return ret
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
        val cdmTransactionBuilder = CdmTransactionBuilder(notary, event, serviceHub, networkMap, DefaultCdmVaultQuery(serviceHub))
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

    private fun createQuantityChangesBlob(contracts : List<Contract>) : String {
        val quantityChanges = contracts.map { createQuantityChangeToTerminated(it) }
        val foldedQuantityChanges = quantityChanges.fold("", { left, quantityChange -> "$left, $quantityChange"})
        return if (foldedQuantityChanges.startsWith(",")) foldedQuantityChanges.removePrefix(",") else foldedQuantityChanges
    }

    private fun createQuantityChangeToTerminated(contract : Contract) : String {
        val contractId = contract.contractIdentifier.single().identifierValue.identifier
        val contractIdScheme = contract.contractIdentifier.single().identifierValue.identifierScheme
        val after = createTerminatedVersionOfTheContract(contract)

        return """{
                  "after" : {
                    "contract" : [ #CONTRACT_AFTER# ]
                  },
                  "before" : {
                    "contractReference" : [ {
                      "identifierValue" : {
                        "identifier" : "#CONTRACT_IDENTIFIER#",
                        "identifierScheme" : "#CONTRACT_IDENTIFIER_SCHEME#"
                      },
                      "version" : 1,
                      "rosettaKey" : "7c9da2"
                    } ]
                  },
                  "change" : [ {
                    "quantity" : {
                      "amount" : 500000000.00
                    }
                  }]}""".replace("#CONTRACT_IDENTIFIER#",contractId)
                      .replace("#CONTRACT_IDENTIFIER_SCHEME#",contractIdScheme)
                      .replace("#CONTRACT_AFTER#",after)
    }

    private fun createNewTrade(contracts : List<Contract>, contractId : String) : String {
        //create it from one of the old trades
        val totalNotional = contracts.map { it.contractualProduct.economicTerms.payout.interestRatePayout.first().quantity.notionalSchedule.notionalStepSchedule.initialValue.toLong() }.sum()
        val sampleContract = contracts.first()
        val contractIdScheme = sampleContract.contractIdentifier.single().identifierValue.identifierScheme
        val tradeContractBuilder = sampleContract.toBuilder()
        tradeContractBuilder.contractIdentifier.clear()
        tradeContractBuilder.addContractIdentifier(createPartyContractIdentifier(contractId, contractIdScheme))
        val newTrade = tradeContractBuilder.build()
        val newTradeNewNotional = setNotional(newTrade, totalNotional)
        return serializeCdmObjectIntoJson(newTradeNewNotional)
    }

    private fun setNotional(contract : Contract, notional : Long) : Contract {
        val notionalStepScheduleBuilder = contract.contractualProduct.economicTerms.payout.interestRatePayout[0].quantity.notionalSchedule.notionalStepSchedule.toBuilder()
        val notionalStepSchedule = notionalStepScheduleBuilder.setInitialValue(BigDecimal(notional)).build() as NonNegativeAmountSchedule

        val notionalScheduleBuilder = contract.contractualProduct.economicTerms.payout.interestRatePayout[0].quantity.notionalSchedule.toBuilder()
        val notionalSchedule = notionalScheduleBuilder.setNotionalStepSchedule(notionalStepSchedule).build()

        val quantityBuilder = contract.contractualProduct.economicTerms.payout.interestRatePayout[0].quantity.toBuilder()
        val quantity = quantityBuilder.setNotionalSchedule(notionalSchedule).build()

        val interestPayoutOneBuilder = contract.contractualProduct.economicTerms.payout.interestRatePayout[0].toBuilder()
        val interestPayoutTwoBuilder = contract.contractualProduct.economicTerms.payout.interestRatePayout[1].toBuilder()

        val interestPayoutOne = interestPayoutOneBuilder.setQuantity(quantity).build()
        val interestPayoutTwo = interestPayoutTwoBuilder.setQuantity(quantity).build()

        val payoutBuilder = contract.contractualProduct.economicTerms.payout.toBuilder()
        payoutBuilder.interestRatePayout.clear()
        payoutBuilder.addInterestRatePayout(interestPayoutOne)
        payoutBuilder.addInterestRatePayout(interestPayoutTwo)
        val payout = payoutBuilder.build()

        val economicTermsBuilder = contract.contractualProduct.economicTerms.toBuilder()
        val economicTerms = economicTermsBuilder.setPayout(payout).build()

        val contractualProductBuilder = contract.contractualProduct.toBuilder()
        val contractualProduct = contractualProductBuilder.setEconomicTerms(economicTerms).build()

        val contractBuilder = contract.toBuilder()
        return contractBuilder.setContractualProduct(contractualProduct).build()
    }

    private fun createPartyContractIdentifier(contractId : String, contractIdScheme : String, issuer : String? = null, partyReference : String? = null) : PartyContractIdentifier {
        val contractIdentifier = createContractIdentifier(contractId, contractIdScheme, issuer, partyReference)

        val partyContractIdentifierBuilder = PartyContractIdentifier.PartyContractIdentifierBuilder()
        partyContractIdentifierBuilder.setIdentifierValue(contractIdentifier.identifierValue)
        return partyContractIdentifierBuilder.build()
    }

    private fun createTerminatedVersionOfTheContract(contract : Contract) : String {
        return serializeCdmObjectIntoJson(contract.toBuilder().setState(StateEnum.TERMINATED).build())
    }

    private fun getPartiesOnThisCompression(contractsToCompress : List<CDMContractState>) : List<Party> {
        val ret = contractsToCompress.flatMap { it.participants }.toSet().toList()
        if(ret.size != 2) {
            throw FlowException("Expected two distinct parties on the trades to compress. Found ${ret.size}")
        }
        return ret
    }

    @Suspendable
    private fun createCompressionCdmEvent(contractsToCompress : List<CDMContractState>) : String {
        val parties = getPartiesOnThisCompression(contractsToCompress)
        val eventDate = LocalDate.now().toString()
        val eventIdentifier = "${serviceHub.myInfo.legalIdentities.first().name.organisation}-${Date().time}"
        val party1MembershipMetadata = getMembershipMetadata(parties[0])
        val party2MembershipMetadata = getMembershipMetadata(parties[1])
        val quantityChangesBlob = createQuantityChangesBlob(contractsToCompress.map { it.contract() })
        val newTrade = createNewTrade(contractsToCompress.map { it.contract() }, "CMP-${Date().time}")

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
              "intent" : "COMPRESSION",
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
                "newTrade" : [ {"contract" : #NEW_TRADE#} ],
                "quantityChange" : [ #QUANTITY_CHANGES# ]
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
                .replace("#QUANTITY_CHANGES#", quantityChangesBlob)
                .replace("#NEW_TRADE#", newTrade)

    }


}

@InitiatedBy(CompressCDMContractsOnLedgerFlow::class)
class CompressCDMContractsOnLedgerFlowResponder(flowSession : FlowSession) : BusinessNetworkAwareInitiatedFlow<SignedTransaction>(flowSession) {

    @Suspendable
    override fun onOtherPartyMembershipVerified(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                //the contract verify should fully cover this, sign always
            }
        }

        return subFlow(signTransactionFlow)
    }

}