package net.corda.derivativestradingnetwork.integrationTests

import com.google.gson.*
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.core.identity.CordaX500Name
import net.corda.derivativestradingnetwork.UtilParsers
import net.corda.derivativestradingnetwork.entity.SettlementInstruction
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.*
import org.isda.cdm.Event
import java.io.File
import java.lang.reflect.Type
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals

//You will struggle to run this from Intelli J on windows. On windows run the WrapperAroundNodeDriver as a JUnit test instead
fun main(args: Array<String>) {
    NodeDriverWithUseCase5().runNetwork()
}

class NodeDriverWithUseCase5 {
    fun runNetwork() {
        driver(DriverParameters(isDebug = true, startNodesInProcess = true, waitForAllNodesToFinish = true,
                extraCordappPackagesToScan = listOf(
                        "net.corda.cdmsupport",
                        "net.corda.derivativestradingnetwork.flow",
                        "net.corda.businessnetworks.membership.member.service",
                        "net.corda.businessnetworks.membership.member",
                        "net.corda.businessnetworks.membership.bno",
                        "net.corda.businessnetworks.membership.states",
                        "net.corda.yourcode"),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4))) {

            // ----- bno, client, dealer, ccp start nodes -------
            val bno = BnoNode(this, TestIdentity(CordaX500Name("BNO", "New York", "US")), false)
            val client1 = MemberNode(this, TestIdentity(CordaX500Name("CLIENT-C01", "", "US")), false)
            val client2 = MemberNode(this, TestIdentity(CordaX500Name("CLIENT-C02", "", "US")), false)
            val client3 = MemberNode(this, TestIdentity(CordaX500Name("CLIENT-C03", "", "US")), false)
            val client4 = MemberNode(this, TestIdentity(CordaX500Name("CLIENT-C04", "", "US")), false)
            val client5 = MemberNode(this, TestIdentity(CordaX500Name("CLIENT-C05", "", "US")), false)
            val dealer1 = MemberNode(this, TestIdentity(CordaX500Name("DEALER-D01", "", "US")), false)
            val dealer2 = MemberNode(this, TestIdentity(CordaX500Name("DEALER-D02", "", "US")), false)
            val dealer3 = MemberNode(this, TestIdentity(CordaX500Name("DEALER-D03", "", "US")), false)
            val ccp = MemberNode(this, TestIdentity(CordaX500Name("CCP-P01", "", "US")), false)

            val nonBnoNodes = listOf(client1, client2, client3, client4, client5, dealer1, dealer2, dealer3, ccp)
            val nodes = listOf(bno) + nonBnoNodes

            nodes.map { it.startCoreAsync() }.map { it.waitForCoreToStart() }.map { it.startWebAsync() }.map { it.waitForWebToStart() }.forEach { it.confirmNodeIsOnTheNetwork() }
            println("Establishing business network")

            establishBusinessNetworkAndConfirmAssertions(bno, nonBnoNodes, 0, 9, 5, 3, 1)

            val nameToNodeMap = nonBnoNodes.map { it.testIdentity.name.organisation to it }.toMap()
            println("Placing trades on the ledger")

            feedInTradesFromDirectoryAndConfirmAssertions(nameToNodeMap)

            println("----- Network set up -----")
            nodes.forEach {
                println("${it.testIdentity.name.organisation} web url is ${it.webHandle.listenAddress}")
            }

            println("-------- Use use case 3 to get some payments --------")
            feedInUseCase3Events(nameToNodeMap)

            println("-------- Use case 3 completed -------")

            println("-------- Settling all payments --------")
            //settleAllPayments(nonBnoNodes)

            println("-------- Confirming no one has any payments in pending ----")
            //nonBnoNodes.forEach { assertEquals(0, it.getAllPayments("PENDING").size) }
            println("--------- ALL GOOD FOR USE CASE 5, READY TO DO SETTLEMENTS ------")
        }
    }

    private fun feedInUseCase3Events(nameToNode : Map<String,MemberNode>) {
        val directoryWithEvents = File(NodeDriver::class.java.getResource("/testData/useCase3Events").file)
        val partyIdToName = createMapFromPartyIdToName(getNetworkDefinitionJson())

        directoryWithEvents.listFiles { file, name -> name.endsWith(".json",true)}.forEach {
            println("Starting to handle event ${it.name}")
                val eventJson = it.readText()
                val event = parseEventFromJson(eventJson)

                val nodeToInitiate = decideWhoInitiatesTheEvent(event, partyIdToName, nameToNode)
                println("It will be placed on the ledger by node ${nodeToInitiate.testIdentity.name.organisation}")
                nodeToInitiate.persistCDMEventOnLedger(eventJson)
        }
    }

    private fun settleAllPayments(memberNodes : List<MemberNode>) {
        memberNodes.forEach { settlePayments(it) }
    }

    private fun settlePayments(memberNode : MemberNode) {
        println("Addressing node ${memberNode.testIdentity.name.organisation}")
        val paymentKeyToCounter = mutableMapOf<PaymentKey,Int>()
        val allPendingPaymentsOnThisNode = memberNode.getAllPayments("PENDING").map { it as Map<String,Object> }
        val beforePendingPayments = allPendingPaymentsOnThisNode.size
        println("Found this many pending payments ${beforePendingPayments}")

        //now create a list of settlement instructions
        allPendingPaymentsOnThisNode.forEach {
            val payerAndReceiverMap = it.get("payerReceiver") as Map<String,Object>
            val payerAndReceiver = setOf(payerAndReceiverMap.get("payerPartyReference").toString(), payerAndReceiverMap.get("receiverPartyReference").toString())
            val currency = (it.get("paymentAmount") as Map<String,Object>).get("currency").toString()
            val paymentDate = LocalDate.parse((it.get("paymentDate") as Map<String,Object>).get("unadjustedDate").toString())
            val paymentKey = PaymentKey(payerAndReceiver,currency,paymentDate)
            increaseSettlementPaymentsCounter(paymentKeyToCounter, paymentKey)
        }

        //now settle them one key after another
        paymentKeyToCounter.forEach {
            val nodeName = memberNode.testIdentity.name.organisation
            println("Settling for payment key ${it.key} on $nodeName")
            val paymentKey = it.key

            val settlementInstruction = SettlementInstruction(paymentKey.payerAndReceiver.toList()[0],
                    null,
                    null,
                    paymentKey.payerAndReceiver.toList()[1],
                    null,
                    null,
                    0.0,paymentKey.currency,paymentKey.paymentDate,"A Settlement Ref")

            val settlementInstructionAsJson = getLocalDateToIsoStringGsonSerializer().toJson(listOf(settlementInstruction))
            memberNode.processSettlementInstruction(settlementInstructionAsJson)
        }

        assertEquals(memberNode.getAllPayments().size, memberNode.getAllPayments("SETTLED").size)

    }

    protected fun getLocalDateToIsoStringGsonSerializer() : Gson {
        return GsonBuilder().registerTypeAdapter(LocalDate::class.java, object : JsonSerializer<LocalDate> {

            override fun serialize(src: LocalDate?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
                return JsonPrimitive(src!!.toString())
            }

        }).create()
    }

    private fun increaseSettlementPaymentsCounter(counter : MutableMap<PaymentKey,Int>, paymentKey : PaymentKey) {
        if(counter.containsKey(paymentKey)) {
            counter[paymentKey] = counter[paymentKey]!! + 1
        } else {
            counter.put(paymentKey, 1)
        }
    }

    data class PaymentKey(val payerAndReceiver : Set<String>, val currency : String, val paymentDate : LocalDate)

    private fun establishBusinessNetworkAndConfirmAssertions(bno: BnoNode, membersToBe: List<MemberNode>, existingMembers : Int, expectedMembers : Int, expectedClients : Int, expectedDealers : Int, expectedCcps : Int) {
        val networkDefinition = getNetworkDefinitionJson()
        //at the beginning there are no members
        assertEquals(existingMembers, bno.getMembershipStates().size)

        membersToBe.forEach {
            acquireMembershipAndConfirmAssertions(bno, it, networkDefinition)
        }

        //check members can see one another
        membersToBe.forEach { confirmVisibility(it, expectedMembers, expectedClients, expectedDealers, expectedCcps) }
    }

    private fun feedInTradesFromDirectoryAndConfirmAssertions(nameToNode : Map<String,MemberNode>) {
        val nameToCounter = mutableMapOf<String,Int>()
        val partyIdToName = createMapFromPartyIdToName(getNetworkDefinitionJson())

        val directoryWithTrades = File(NodeDriver::class.java.getResource("/testData/barclaysHackathonTrades").file)
        directoryWithTrades.listFiles { file, name -> name.endsWith(".json",true)}.forEach {
            println("Starting to handle trade ${it.name}")
            val eventJson = it.readText()
            val event = parseEventFromJson(eventJson)
            val nodeToInitiate = decideWhoInitiatesTheEvent(event, partyIdToName, nameToNode)
            println("It will be placed on the ledger by node ${nodeToInitiate.testIdentity.name.organisation}")
            nodeToInitiate.persistCDMEventOnLedger(eventJson)
            updateCounter(event, partyIdToName, nameToCounter)
        }

        println("Expect to have this population of trades $nameToCounter, checking...")
        assertEquals(162, nameToNode.get("CCP-P01")!!.getLiveContracts().size)

        assertEquals(8, nameToNode.get("CLIENT-C01")!!.getLiveContracts().size)
        assertEquals(7, nameToNode.get("CLIENT-C02")!!.getLiveContracts().size)
        assertEquals(10, nameToNode.get("CLIENT-C03")!!.getLiveContracts().size)
        assertEquals(15, nameToNode.get("CLIENT-C04")!!.getLiveContracts().size)
        assertEquals(8, nameToNode.get("CLIENT-C05")!!.getLiveContracts().size)

        assertEquals(55, nameToNode.get("DEALER-D01")!!.getLiveContracts().size)
        assertEquals(62, nameToNode.get("DEALER-D02")!!.getLiveContracts().size)
        assertEquals(59, nameToNode.get("DEALER-D03")!!.getLiveContracts().size)
        println("Ledger content matches")
    }

    private fun updateCounter(event : Event, partyIdToName : Map<String,String>, nameToCounter : MutableMap<String,Int>) {
        val partyOne = event.party[0].partyId.first() //only one party initiates the process of putting the trade on the ledger. the other party will sign this
        val partyTwo = event.party[1].partyId.first()

        val nameOne = partyIdToName.get(partyOne) ?: throw RuntimeException("Expected to find node name (i.e. organization) for a party id $partyOne")
        val nameTwo = partyIdToName.get(partyTwo) ?: throw RuntimeException("Expected to find node name (i.e. organization) for a party id $partyTwo")

        if(nameOne == nameTwo) {
            nameToCounter[nameOne] = (nameToCounter[nameOne] ?: 0) + 1
        } else {
            nameToCounter[nameOne] = (nameToCounter[nameOne] ?: 0) + 1
            nameToCounter[nameTwo] = (nameToCounter[nameTwo] ?: 0) + 1
        }
    }

    private fun decideWhoInitiatesTheEvent(event : Event, partyIdToName : Map<String,String>, nameToNode : Map<String,MemberNode>) : MemberNode {
        val partyOne = event.party[0].partyId.first() //only one party initiates the process of putting the trade on the ledger. the other party will sign this
        val partyTwo = event.party[1].partyId.first()

        val nameOne = partyIdToName.get(partyOne) ?: throw RuntimeException("Expected to find node name (i.e. organization) for a party id $partyOne")
        val nameTwo = partyIdToName.get(partyTwo) ?: throw RuntimeException("Expected to find node name (i.e. organization) for a party id $partyTwo")
        val nameInitiator = if (nameOne.equals("CCP-P01",true) && event.party.size == 2) {
            nameOne
        } else if (nameTwo.equals("CCP-P01",true) && event.party.size == 2) {
            nameTwo
        } else {
            nameOne
        }

        return nameToNode.get(nameInitiator) ?: throw RuntimeException("Expected to find a node for name $nameInitiator")
    }

    private fun createMapFromPartyIdToName(networkDefinition : String) : Map<String,String> {
        val allMemberAccounts = UtilParsers.parseMembershipDefinitionJson(networkDefinition)
        return allMemberAccounts.map { it.partyId to it.name }.toMap()
    }


    private fun getNetworkDefinitionJson() : String {
        return NodeDriver::class.java.getResource("/testData/network-definition-barclays-hackathon-with-additional-parties.json").readText()
    }

    private fun acquireMembershipAndConfirmAssertions(bno: BnoNode, member: MemberNode, networkDefinition: String) {
        val membershipsBefore = bno.getMembershipStates().size
        member.askForMembership(networkDefinition)
        bno.approveMembership(member.testIdentity.party)
        assertEquals(membershipsBefore + 1, bno.getMembershipStates().size)
    }

    private fun confirmVisibility(memberNode: MemberNode, allMembers: Int, clients: Int, dealers: Int, ccps: Int) {
        assertEquals(allMembers, memberNode.getMembersVisibleToNode().size)
        assertEquals(clients, memberNode.getMembersVisibleToNode("clients").size)
        assertEquals(dealers, memberNode.getMembersVisibleToNode("dealers").size)
        assertEquals(ccps, memberNode.getMembersVisibleToNode("ccps").size)
    }
}