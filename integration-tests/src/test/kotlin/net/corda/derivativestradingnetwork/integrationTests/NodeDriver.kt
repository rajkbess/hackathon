package net.corda.derivativestradingnetwork.integrationTests

import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.core.identity.CordaX500Name
import net.corda.derivativestradingnetwork.UtilParsers
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.*
import org.isda.cdm.Event
import java.io.File
import kotlin.test.assertEquals

//You will struggle to run this from Intelli J on windows. On windows run the WrapperAroundNodeDriver as a JUnit test instead
fun main(args: Array<String>) {
    NodeDriver().runNetwork()
}

class NodeDriver {
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

            nodes.map { it.startCoreAsync() }.map { it.waitForCoreToStart() }.map { it.startWebAsync() }.forEach { it.waitForWebToStart() }
            nodes.forEach { node -> node.confirmNodeIsOnTheNetwork() }
            println("Establishing business network")
            establishBusinessNetworkAndConfirmAssertions(bno, nonBnoNodes)

            println("Placing trades on the ledger")
            feedInTradesFromDirectoryAndConfirmAssertions(nonBnoNodes.map { it.testIdentity.name.organisation to it }.toMap())

            println("")

            println("Network set up")
        }
    }

    private fun establishBusinessNetworkAndConfirmAssertions(bno: BnoNode, membersToBe: List<MemberNode>) {
        val networkDefinition = getNetworkDefinitionJson()
        //at the beginning there are no members
        assertEquals(0, bno.getMembershipStates().size)

        membersToBe.forEach {
            acquireMembershipAndConfirmAssertions(bno, it, networkDefinition)
        }

        //check members can see one another
        membersToBe.forEach { confirmVisibility(it as MemberNode, 9, 5, 3, 1) }
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

        println("Expect to have this population of trades $nameToCounter")
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
        val nameInitiator = if (nameOne.equals("CCP-P01",true)) {
            nameOne
        } else if (nameTwo.equals("CCP-P01",true)) {
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
        return NodeDriver::class.java.getResource("/testData/network-definition.json").readText()
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