package net.corda.derivativestradingnetwork.integrationTests

import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.*
import org.junit.Test
import kotlin.test.assertEquals

class EndToEndTest {

    fun setUpEnvironmentAndRunTest(test : (DriverDSL, BnoNode,MemberNode,MemberNode, MemberNode, MemberNode, MemberNode, MemberNode, MemberNode, MemberNode, MemberNode)->Unit) {
        driver(DriverParameters(isDebug = true, startNodesInProcess = true,
                extraCordappPackagesToScan = listOf(
                        "net.corda.cdmsupport",
                        "net.corda.derivativestradingnetwork.flow",
                        "net.corda.businessnetworks.membership.member.service",
                        "net.corda.businessnetworks.membership.member",
                        "net.corda.businessnetworks.membership.bno",
                        "net.corda.businessnetworks.membership.states"),
                        networkParameters = testNetworkParameters(minimumPlatformVersion = 4))) {




            // ----- bno, client, dealer, ccp start nodes -------
            val bno = BnoNode(this, TestIdentity(CordaX500Name("BNO", "New York", "US")),false)
            val client1 = MemberNode(this, TestIdentity(CordaX500Name("CLIENT-C01", "", "US")), false)
            val client2 = MemberNode(this, TestIdentity(CordaX500Name("CLIENT-C02", "", "US")), false)
            val client3 = MemberNode(this, TestIdentity(CordaX500Name("CLIENT-C03", "", "US")), false)
            val client4 = MemberNode(this, TestIdentity(CordaX500Name("CLIENT-C04", "", "US")), false)
            val dealer1 = MemberNode(this, TestIdentity(CordaX500Name("DEALER-D01", "", "US")), false)
            val dealer2 = MemberNode(this, TestIdentity(CordaX500Name("DEALER-D02", "", "US")), false)
            val dealer3 = MemberNode(this, TestIdentity(CordaX500Name("DEALER-D03", "", "US")), false)
            val ccp = MemberNode(this, TestIdentity(CordaX500Name("CCP-P01", "", "US")), false)
            val regulator = MemberNode(this, TestIdentity(CordaX500Name("REGULATOR-R01", "", "US")), false)

            listOf(bno,client1,client2,client3,client4,dealer1,dealer2,dealer3,ccp,regulator).map { it.startCoreAsync() }.map { it.waitForCoreToStart() }.map { it.startWebAsync() }.map { it.waitForWebToStart() }

            //confirm all the nodes are on the network
            bno.confirmNodeIsOnTheNetwork()
            client1.confirmNodeIsOnTheNetwork()
            client2.confirmNodeIsOnTheNetwork()
            client3.confirmNodeIsOnTheNetwork()
            client4.confirmNodeIsOnTheNetwork()
            dealer1.confirmNodeIsOnTheNetwork()
            dealer2.confirmNodeIsOnTheNetwork()
            dealer3.confirmNodeIsOnTheNetwork()
            ccp.confirmNodeIsOnTheNetwork()
            regulator.confirmNodeIsOnTheNetwork()

            establishBusinessNetworkAndConfirmAssertions(bno, listOf(client1,client2,client3,client4,dealer1,dealer2,dealer3,ccp,regulator))

            //run the test
            test(this, bno, client1, client2, client3, client4, dealer1, dealer2, dealer3, ccp, regulator)

        }
    }


    @Test
    fun `Party A can trade with Party B`() {
        setUpEnvironmentAndRunTest { _, _, client1, _, _, _, dealer1, _, _, _, _ ->
            val dealer1Client1Trade = EndToEndTest::class.java.getResource("/testData/cdmEvents/dealer-1_client-1/newTrade_1.json").readText()
            assertEquals(0, client1.getLiveContracts().size)
            assertEquals(0, dealer1.getLiveContracts().size)
            dealer1.persistCDMEventOnLedger(dealer1Client1Trade)
            assertEquals(1, client1.getLiveContracts().size)
            assertEquals(1, dealer1.getLiveContracts().size)
        }
    }

    @Test
    fun `Party A trades with Party B and Party C`() {
        setUpEnvironmentAndRunTest { _, _, client1, _, _, client4, dealer1, _, _, _, _ ->
            val dealer1Client1Trade = EndToEndTest::class.java.getResource("/testData/cdmEvents/dealer-1_client-1/newTrade_1.json").readText()
            assertEquals(0, client1.getLiveContracts().size)
            assertEquals(0, client4.getLiveContracts().size)
            assertEquals(0, dealer1.getLiveContracts().size)
            dealer1.persistCDMEventOnLedger(dealer1Client1Trade)
            assertEquals(1, client1.getLiveContracts().size)
            assertEquals(0, client4.getLiveContracts().size)
            assertEquals(1, dealer1.getLiveContracts().size)

            val dealer1Client4Trade = EndToEndTest::class.java.getResource("/testData/cdmEvents/dealer-1_client-4/newTrade_1.json").readText()
            dealer1.persistCDMEventOnLedger(dealer1Client4Trade)
            assertEquals(1, client1.getLiveContracts().size)
            assertEquals(1, client4.getLiveContracts().size)
            assertEquals(2, dealer1.getLiveContracts().size)
        }
    }

    @Test
    fun `In-House trade`() {
        setUpEnvironmentAndRunTest { _, _, _, client1, _, _, dealer1, _, _, _, _ ->
            val inHouseTrade = EndToEndTest::class.java.getResource("/testData/cdmEvents/dealer-1_dealer-1/newTrade_1.json").readText()
            assertEquals(0, dealer1.getLiveContracts().size)
            dealer1.persistCDMEventOnLedger(inHouseTrade)
            assertEquals(1, dealer1.getLiveContracts().size)
        }
    }

    @Test
    fun `Trades can be terminated`() {
        setUpEnvironmentAndRunTest { _, _, client1, _, _, _, dealer1, _, _, _,_ ->
            assertEquals(0, client1.getLiveContracts().size)
            assertEquals(0, dealer1.getLiveContracts().size)

            //get two trades in
            val dealer1Client1Trade = EndToEndTest::class.java.getResource("/testData/cdmEvents/dealer-1_client-1/newTrade_1.json").readText()
            dealer1.persistCDMEventOnLedger(dealer1Client1Trade)
            assertEquals(1, client1.getLiveContracts().size)
            assertEquals(1, dealer1.getLiveContracts().size)

            val inHouseTrade = EndToEndTest::class.java.getResource("/testData/cdmEvents/dealer-1_dealer-1/newTrade_1.json").readText()
            dealer1.persistCDMEventOnLedger(inHouseTrade)
            assertEquals(2, dealer1.getLiveContracts().size)

            //now terminate
            assertEquals(0, client1.getTerminatedContracts().size)
            assertEquals(0, dealer1.getTerminatedContracts().size)

            val dealer1Client1Termination = EndToEndTest::class.java.getResource("/testData/cdmEvents/dealer-1_client-1/termination_1.json").readText()
            dealer1.persistCDMEventOnLedger(dealer1Client1Termination)
            assertEquals(0, client1.getLiveContracts().size)
            assertEquals(1, dealer1.getLiveContracts().size)
            assertEquals(1, client1.getTerminatedContracts().size)
            assertEquals(1, dealer1.getTerminatedContracts().size)

            val dealer1Dealer1Termination = EndToEndTest::class.java.getResource("/testData/cdmEvents/dealer-1_dealer-1/termination_1.json").readText()
            dealer1.persistCDMEventOnLedger(dealer1Dealer1Termination)
            assertEquals(0, client1.getLiveContracts().size)
            assertEquals(0, dealer1.getLiveContracts().size)
            assertEquals(1, client1.getTerminatedContracts().size)
            assertEquals(2, dealer1.getTerminatedContracts().size)
        }
    }

    @Test
    fun `Trade amendment`() {
        setUpEnvironmentAndRunTest { _, _, _, _, _, client4, dealer1, _, _, _,_ ->
            assertEquals(0, client4.getLiveContracts().size)
            assertEquals(0, dealer1.getLiveContracts().size)
            val dealer1Client4Trade = EndToEndTest::class.java.getResource("/testData/cdmEvents/dealer-1_client-4/newTrade_1.json").readText()
            dealer1.persistCDMEventOnLedger(dealer1Client4Trade)
            assertEquals(1, client4.getLiveContracts().size)
            assertEquals(1, dealer1.getLiveContracts().size)

            val dealer1Client4QuantityChange = EndToEndTest::class.java.getResource("/testData/cdmEvents/dealer-1_client-4/quantityChange_1.json").readText()
            dealer1.persistCDMEventOnLedger(dealer1Client4QuantityChange)
            assertEquals(1, client4.getLiveContracts().size)
            assertEquals(1, dealer1.getLiveContracts().size)
        }
    }

    @Test
    fun `New trade, observation, reset, payment`() {
        setUpEnvironmentAndRunTest { _, _, client1, _, _, _, dealer1, _, _,_,_ ->
            //putting new trade in first
            val contractId = "7IE1XJPRMD"
            val contractIdScheme = "http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/"

            val dealer1Client1Trade = EndToEndTest::class.java.getResource("/testData/cdmEvents/dealer-1_client-1/newTrade_1.json").readText()
            assertEquals(0, client1.getLiveContracts().size)
            assertEquals(0, dealer1.getLiveContracts().size)
            dealer1.persistCDMEventOnLedger(dealer1Client1Trade)
            assertEquals(1, client1.getLiveContracts().size)
            assertEquals(1, dealer1.getLiveContracts().size)

            //followed by an observation
            val observation = EndToEndTest::class.java.getResource("/testData/cdmEvents/observation_1.json").readText()
            dealer1.persistCDMEventOnLedger(observation)

            //followed by a reset
            assertEquals(0, client1.getResets(contractId,contractIdScheme).size)
            assertEquals(0, dealer1.getResets(contractId,contractIdScheme).size)
            val reset = EndToEndTest::class.java.getResource("/testData/cdmEvents/dealer-1_client-1/reset_1.json").readText()
            dealer1.persistCDMEventOnLedger(reset)
            assertEquals(1, client1.getResets(contractId,contractIdScheme).size)
            assertEquals(1, dealer1.getResets(contractId,contractIdScheme).size)

            //followed by a payment
            assertEquals(0, client1.getPayments(contractId,contractIdScheme).size)
            assertEquals(0, dealer1.getPayments(contractId,contractIdScheme).size)
            val payment = EndToEndTest::class.java.getResource("/testData/cdmEvents/dealer-1_client-1/payment_1.json").readText()
            dealer1.persistCDMEventOnLedger(payment)
            val client1Payments =  client1.getPayments(contractId,contractIdScheme)
            assertEquals(1,client1Payments.size)
            val dealer1Payments = dealer1.getPayments(contractId,contractIdScheme)
            assertEquals(1, dealer1Payments.size)
            assertEquals("PENDING",(client1Payments.first() as Map<String,Object>).get("paymentStatus").toString())
            assertEquals("PENDING",(dealer1Payments.first() as Map<String,Object>).get("paymentStatus").toString())


        }
    }

    @Test
    fun `New trade, reported to regulator`() {
        setUpEnvironmentAndRunTest { _, _, client1, _, _, _, dealer1, _,_, _, regulator ->
            val dealer1Client1Trade = EndToEndTest::class.java.getResource("/testData/cdmEvents/dealer-1_client-1/newTrade_1.json").readText()
            assertEquals(0, client1.getLiveContracts().size)
            assertEquals(0, dealer1.getLiveContracts().size)
            dealer1.persistCDMEventOnLedger(dealer1Client1Trade)
            assertEquals(1, client1.getLiveContracts().size)
            assertEquals(1, dealer1.getLiveContracts().size)

            assertEquals(0, regulator.getLiveContracts().size)
            client1.shareContract("REGULATOR-R01","7IE1XJPRMD","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")
            assertEquals(1, regulator.getLiveContracts().size)
        }
    }

    @Test
    fun `Payment on a trade being quantityChanged`() {
        setUpEnvironmentAndRunTest { _, _, _, client2, _, _, _, _,dealer3, ccp, _ ->

            //no live contracts, no payments at the beginning
            assertEquals(0, client2.getLiveContracts().size)
            assertEquals(0, dealer3.getLiveContracts().size)
            assertEquals(0, ccp.getLiveContracts().size)
            assertEquals(0, client2.getPayments("BD25TK6B0W","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/").size)
            assertEquals(0, dealer3.getPayments("BD25TK6B0W","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/").size)
            assertEquals(0, ccp.getPayments("BD25TK6B0W","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/").size)

            //send the test trade in
            val dealer3CcpTrade = EndToEndTest::class.java.getResource("/testData/barclaysHackathonTrades/CDM_NEW_CREDIT_CDSWAP2018-10-01T214055.077.json").readText()
            dealer3.persistCDMEventOnLedger(dealer3CcpTrade)
            assertEquals(0, client2.getLiveContracts().size)
            assertEquals(1, dealer3.getLiveContracts().size)
            assertEquals(1, ccp.getLiveContracts().size)
            assertEquals(0, dealer3.getPayments("BD25TK6B0W","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/").size)
            assertEquals(0, ccp.getPayments("BD25TK6B0W","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/").size)

            //send the partial novation with a payment on it in
            val partialNovation = EndToEndTest::class.java.getResource("/testData/useCase3Events/BD25TK6B0W_PARTIAL_NOVATION.json").readText()
            dealer3.persistCDMEventOnLedger(partialNovation)
            assertEquals(1, client2.getLiveContracts().size)
            assertEquals(1, dealer3.getLiveContracts().size)
            assertEquals(2, ccp.getLiveContracts().size)
            assertEquals(1, client2.getPayments("BD25TK6B0W","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/").size)
            assertEquals(1, dealer3.getPayments("BD25TK6B0W","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/").size)
            assertEquals(0, ccp.getPayments("BD25TK6B0W","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/").size)
        }
    }

    @Test
    fun `New trade, novate by a CCP`() {
        setUpEnvironmentAndRunTest { _, _, client1, _, _, _, dealer1, _,_, ccp, _ ->
            assertEquals(0, client1.getLiveContracts().size)
            assertEquals(0, dealer1.getLiveContracts().size)
            assertEquals(0, ccp.getLiveContracts().size)

            //send a new trade in between client 1 and dealer 1
            val dealer1Client1Trade = EndToEndTest::class.java.getResource("/testData/cdmEvents/dealer-1_client-1/newTrade_1.json").readText()
            dealer1.persistCDMEventOnLedger(dealer1Client1Trade)
            assertEquals(1, client1.getLiveContracts().size)
            assertEquals(1, dealer1.getLiveContracts().size)

            assertEquals(0, ccp.getLiveContracts().size)
            assertEquals(0, client1.getNovatedContracts().size)
            assertEquals(0, dealer1.getNovatedContracts().size)
            assertEquals(0, ccp.getNovatedContracts().size)

            //share it with the CCP
            client1.shareContract("CCP-P01","7IE1XJPRMD","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")
            assertEquals(1, ccp.getLiveContracts().size)
            assertEquals(1, client1.getLiveContracts().size)
            assertEquals(1, dealer1.getLiveContracts().size)
            assertEquals(0, client1.getNovatedContracts().size)
            assertEquals(0, dealer1.getNovatedContracts().size)
            assertEquals(0, ccp.getNovatedContracts().size)

            //let the CCP novate it
            val novation = EndToEndTest::class.java.getResource("/testData/cdmEvents/ccp/novations/dealer-1_client-1-novation.json").readText()
            ccp.persistCDMEventOnLedger(novation)
            assertEquals(2, ccp.getLiveContracts().size) //1 trade with client 1 and one with dealer 1
            assertEquals(1, client1.getLiveContracts().size) //1 trade with ccp
            assertEquals(1, dealer1.getLiveContracts().size) //1 trade with ccp
            assertEquals(1, client1.getNovatedContracts().size) //the old trade between client 1 and dealer 1
            assertEquals(1, dealer1.getNovatedContracts().size) //the old trade between client 1 and dealer 1
            assertEquals(0, ccp.getNovatedContracts().size) //the ccp is not a participant on the novated trade so it won't store it to its vault
        }
    }

    private fun establishBusinessNetworkAndConfirmAssertions(bno : BnoNode, membersToBe : List<MemberNode>) {
        val networkDefinition = EndToEndTest::class.java.getResource("/testData/network-definition-end-to-end-test.json ").readText()
        //at the beginning there are no members
        assertEquals(0,bno.getMembershipStates().size)

        membersToBe.forEach {
            acquireMembershipAndConfirmAssertions(bno,it,networkDefinition)
        }

        //check members can see one another
        membersToBe.forEach { confirmVisibility(it as MemberNode, 9, 4, 3, 1, 1) }
    }

    private fun acquireMembershipAndConfirmAssertions(bno : BnoNode, member : MemberNode, networkDefinition : String) {
        val membershipsBefore = bno.getMembershipStates().size
        member.askForMembership(networkDefinition)
        bno.approveMembership(member.testIdentity.party)
        assertEquals(membershipsBefore+1,bno.getMembershipStates().size)
    }

    private fun confirmVisibility(memberNode : MemberNode, allMembers : Int, clients : Int, dealers : Int, ccps : Int, regulators : Int) {
        assertEquals(allMembers,memberNode.getMembersVisibleToNode().size)
        assertEquals(clients,memberNode.getMembersVisibleToNode("clients").size)
        assertEquals(dealers,memberNode.getMembersVisibleToNode("dealers").size)
        assertEquals(ccps,memberNode.getMembersVisibleToNode("ccps").size)
        assertEquals(regulators,memberNode.getMembersVisibleToNode("regulators").size)
    }
}