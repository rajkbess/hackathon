package net.corda.derivativestradingnetwork.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.common.PartyAndMembershipMetadata
import net.corda.businessnetworks.membership.member.GetMembersFlow
import net.corda.businessnetworks.membership.member.support.BusinessNetworkAwareInitiatedFlow
import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.cdmsupport.CDMContractState
import net.corda.cdmsupport.eventparsing.createContractIdentifier
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.network.NetworkMap
import net.corda.cdmsupport.transactionbuilding.CdmTransactionBuilder
import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap
import org.isda.cdm.Contract
import org.isda.cdm.InterestRatePayout
import org.isda.cdm.PeriodExtendedEnum
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@StartableByRPC
class FixCDMContractsOnLedgerFlow(val networkMap : NetworkMap, val oracleParty: Party, val fixingDate : LocalDate) : FlowLogic<Int>() {

    @Suspendable
    override fun call() : Int {
        val cdmVaultQuery = DefaultCdmVaultQuery(serviceHub)
        val liveContracts = cdmVaultQuery.getLiveContracts()
        val toBeFixedContracts = liveContracts.filter { hasFixingOnDate(it) }
        toBeFixedContracts.forEach {
            val contractId = it.contractIdentifier.single().identifierValue.identifier
            val contractIdScheme = it.contractIdentifier.single().identifierValue.identifierScheme
            subFlow(FixCDMContractOnLedgerFlow(networkMap, oracleParty, contractId, contractIdScheme, fixingDate))
        }
        return toBeFixedContracts.size
    }

    private fun hasFixingOnDate(contract : Contract) : Boolean {
        val floatingLegs = contract.contractualProduct.economicTerms.payout.interestRatePayout.filter { isFloating(it) }
        return floatingLegs.any { hasFixingOnDate(it) }
    }

    private fun isFloating(interestRatePayout : InterestRatePayout) : Boolean {
        return interestRatePayout.interestRate?.floatingRate != null
    }

    private fun hasFixingOnDate(interestRatePayout: InterestRatePayout) : Boolean {
        return getFixingDates(interestRatePayout).contains(fixingDate)
    }

    private fun getFixingDates(interestRatePayout: InterestRatePayout) : List<LocalDate> {
        //this is simplified and actually looks at the starts of the calculation periods
        val ret = mutableListOf<LocalDate>()
        val calculationPeriodFrequency = getCalculationPeriodFrequency(interestRatePayout)

        var fixingDate = interestRatePayout.calculationPeriodDates.effectiveDate.adjustableDate.unadjustedDate
        while(fixingDate.isBefore(interestRatePayout.calculationPeriodDates.terminationDate.unadjustedDate)) {
            ret.add(fixingDate)
            fixingDate = fixingDate.plusDays(calculationPeriodFrequency)
        }
        return ret
    }

    private fun getCalculationPeriodFrequency(interestRatePayout: InterestRatePayout) : Long {
        val periodMultiplier = interestRatePayout.calculationPeriodDates.calculationPeriodFrequency.periodMultiplier
        val periodFrequency = interestRatePayout.calculationPeriodDates.calculationPeriodFrequency.period

        return when {
            periodFrequency == PeriodExtendedEnum.D -> periodMultiplier.toLong()
            periodFrequency == PeriodExtendedEnum.W -> periodMultiplier * 7L
            periodFrequency == PeriodExtendedEnum.M -> periodMultiplier * 30L
            periodFrequency == PeriodExtendedEnum.Y -> periodMultiplier * 365L
            else -> throw FlowException("Unsupported period frequency")
        }
    }
}

@InitiatingFlow
@StartableByRPC
class FixCDMContractOnLedgerFlow(val networkMap : NetworkMap, val oracleParty: Party, val contractId : String, val contractIdScheme : String, val fixingDate : LocalDate) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val contractIdentifier = createContractIdentifier(contractId, contractIdScheme, null, null)
        val cdmContractState = DefaultCdmVaultQuery(serviceHub).getCdmContractState(listOf(contractIdentifier)).state.data
        val fixingRate = subFlow(GetFixingForDate(oracleParty, fixingDate))
        val cashFlow = calculateCashflow(fixingRate, cdmContractState)
        val resetCdmEvent = createResetCDMEvent(cdmContractState, cashFlow)
        return persistCDMEventOnTheLedger(resetCdmEvent)
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

    private fun calculateCashflow(fixingRate : BigDecimal, cdmContractState: CDMContractState) : Map<InterestRatePayout,BigDecimal> {
        val floatingLegs = cdmContractState.contract().contractualProduct.economicTerms.payout.interestRatePayout.filter { isFloating(it) }
        return floatingLegs.map {
            val notionalAmount = it.quantity.notionalAmount.amount
            val calculationPeriodFrequency = getCalculationPeriodFrequency(it)
            val cashflow = notionalAmount.multiply(fixingRate).multiply(BigDecimal(calculationPeriodFrequency/365.0))
            (it to cashflow)
        }.toMap()
    }

    private fun isFloating(interestRatePayout : InterestRatePayout) : Boolean {
        return interestRatePayout.interestRate?.floatingRate != null
    }

    private fun getCalculationPeriodFrequency(interestRatePayout: InterestRatePayout) : Long {
        val periodMultiplier = interestRatePayout.calculationPeriodDates.calculationPeriodFrequency.periodMultiplier
        val periodFrequency = interestRatePayout.calculationPeriodDates.calculationPeriodFrequency.period

        return when {
            periodFrequency == PeriodExtendedEnum.D -> periodMultiplier.toLong()
            periodFrequency == PeriodExtendedEnum.W -> periodMultiplier * 7L
            periodFrequency == PeriodExtendedEnum.M -> periodMultiplier * 30L
            periodFrequency == PeriodExtendedEnum.Y -> periodMultiplier * 365L
            else -> throw FlowException("Unsupported period frequency")
        }
    }

    @Suspendable
    private fun getMembershipMetadata(party : Party) : MembershipMetadata {
        return getPartiesOnThisBusinessNetwork().filter { it.party == party }.single().membershipMetadata
    }

    @Suspendable
    private fun getPartiesOnThisBusinessNetwork() : List<PartyAndMembershipMetadata> {
        return subFlow(GetMembersFlow(false))
    }

    @Suspendable
    private fun createResetCDMEvent(cdmContractState : CDMContractState, cashFlowPerLeg : Map<InterestRatePayout, BigDecimal>) : String {

        val contract = cdmContractState.contract()
        val eventDate = LocalDate.now().toString()
        val eventIdentifier = "${serviceHub.myInfo.legalIdentities.first().name.organisation}-${Date().time}"
        val party1MembershipMetadata = getMembershipMetadata(cdmContractState.participants[0])
        val party2MembershipMetadata = getMembershipMetadata(cdmContractState.participants[1])
        val contractIdentifier = contract.contractIdentifier.single().identifierValue.identifier
        val contractIdentifierScheme = contract.contractIdentifier.single().identifierValue.identifierScheme
        val floatingLeg = contract.contractualProduct.economicTerms.payout.interestRatePayout.filter { isFloating(it) }.single()
        val floatingLegPayer = floatingLeg.payerReceiver.payerPartyReference
        val floatingLegReceiver = floatingLeg.payerReceiver.receiverPartyReference
        val cashFlowCurrency = floatingLeg.quantity.notionalAmount.currency

        return """
            {
              "eventDate" : "#EVENT_DATE#",
              "eventEffect" : {
                "effectedEvent" : "dummyEventEffect1"
              },
              "eventIdentifier" : {
                "identifierValue" : {
                  "identifier" : "#EVENT_IDENTIFIER#"
                }
              },
              "eventQualifier" : "Reset",
              "lineage" : {
                "contractReference" : [ {
                  "identifierValue" : {
                    "identifier" : "#CONTRACT_IDENTIFIER#",
                    "identifierScheme" : "#CONTRACT_IDENTIFIER_SCHEME#"
                  }
                } ]
              },
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
                "reset" : [ {
                  "cashflow" : {
                    "payerReceiver" : {
                      "payerPartyReference" : "#PARTY_PAYER_ID#",
                      "receiverPartyReference" : "#PARTY_RECEIVER_ID#"
                    },
                    "cashflowAmount" : {
                      "amount" : #AMOUNT#,
                      "currency" : "#CURRENCY#"
                    },
                    "cashflowCalculation" : "FloatingAmount",
                    "rosettaKeyValue" : "ba6df164"
                  },
                  "date" : "#FIXING_DATE#",
                  "resetValue" : #FIXING_RATE#,
                  "rosettaKey" : "2e884775"
                } ]
              },
              "timestamp" : {
                "creationTimestamp" : "2018-03-20T18:13:51"
              },
              "rosettaKey" : "651eab30"
            }
        """.replace("#EVENT_DATE#",eventDate)
           .replace("#EVENT_IDENTIFIER#",eventIdentifier)
           .replace("#PARTY_1_LEGAL_ENTITY_ID#",party1MembershipMetadata.legalEntityId)
           .replace("#PARTY_1_LEGAL_ENTITY_NAME#",party1MembershipMetadata.name)
           .replace("#PARTY_1_ID#",party1MembershipMetadata.partyId)
           .replace("#PARTY_2_LEGAL_ENTITY_ID#",party2MembershipMetadata.legalEntityId)
           .replace("#PARTY_2_LEGAL_ENTITY_NAME#",party2MembershipMetadata.name)
           .replace("#PARTY_2_ID#",party2MembershipMetadata.partyId)
           .replace("#CONTRACT_IDENTIFIER#",contractIdentifier)
           .replace("#CONTRACT_IDENTIFIER_SCHEME#",contractIdentifierScheme)
           .replace("#PARTY_PAYER_ID#",floatingLegPayer)
           .replace("#PARTY_RECEIVER_ID#",floatingLegReceiver)
           .replace("#AMOUNT#",cashFlow.toDouble().toString())
           .replace("#CURRENCY#",cashFlowCurrency)
    }

}

@InitiatedBy(FixCDMContractOnLedgerFlow::class)
class FixCDMContractOnLedgerFlowResponder(flowSession : FlowSession) : BusinessNetworkAwareInitiatedFlow<SignedTransaction>(flowSession) {

    @Suspendable
    override fun onOtherPartyMembershipVerified(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                //always approve
            }
        }

        return subFlow(signTransactionFlow)
    }
}

@InitiatingFlow
@StartableByRPC
class GetFixingForDate(val oracleParty : Party, val fixingDate : LocalDate) : FlowLogic<BigDecimal>() {

    @Suspendable
    override fun call(): BigDecimal {
        return initiateFlow(oracleParty).sendAndReceive<BigDecimal>(fixingDate).unwrap { it }
    }

}

@InitiatedBy(GetFixingForDate::class)
class GetFixingForDateResponder(flowSession : FlowSession) : BusinessNetworkAwareInitiatedFlow<Unit>(flowSession) {

    @Suspendable
    override fun onOtherPartyMembershipVerified() {
        val fixingDate = flowSession.receive<LocalDate>().unwrap { it }
        flowSession.send(BigDecimal("1.984"))
    }
}