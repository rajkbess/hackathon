package net.corda.derivativestradingnetwork.integrationTests

import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.*
import org.junit.Test

class EndToEndTest {

    fun setUpEnvironmentAndRunTest(test : (DriverDSL, NetworkNode, NetworkNode, NetworkNode, NetworkNode, NetworkNode)->Unit) {
        driver(DriverParameters(isDebug = true, startNodesInProcess = true,
                extraCordappPackagesToScan = listOf(
                        "net.corda.cdmsupport",
                        "net.corda.derivativestradingnetwork.flow",
                        "net.corda.derivativestradingnetwork.states",
                        "net.corda.businessnetworks.membership.member.service",
                        "net.corda.businessnetworks.membership.member",
                        "net.corda.businessnetworks.membership.bno",
                        "net.corda.businessnetworks.membership.states"),
                        networkParameters = testNetworkParameters(minimumPlatformVersion = 4))) {




            // ----- bno, client, dealer, ccp start nodes -------
            val alice = NetworkNode(this, TestIdentity(CordaX500Name("Alice", "New York", "US")),false)
            val bank1 = NetworkNode(this, TestIdentity(CordaX500Name("Bank 1", "", "US")), false)
            val bob = NetworkNode(this, TestIdentity(CordaX500Name("Bob", "", "US")), false)
            val issuer = NetworkNode(this, TestIdentity(CordaX500Name("Issuer", "", "US")), false)
            val amlAuthority = NetworkNode(this, TestIdentity(CordaX500Name("AML Authority", "", "US")), false)


            listOf(alice,bank1,bob, issuer, amlAuthority).map { it.startCoreAsync() }.map { it.waitForCoreToStart() }
            //.map { it.startWebAsync() }.map { it.waitForWebToStart() }

            //confirm all the nodes are on the network
            alice.confirmNodeIsOnTheNetwork()
            bank1.confirmNodeIsOnTheNetwork()
            bob.confirmNodeIsOnTheNetwork()
            issuer.confirmNodeIsOnTheNetwork()
            amlAuthority.confirmNodeIsOnTheNetwork()

            //run the test
            test(this, alice,  bob, bank1, issuer, amlAuthority)

        }
    }

    @Test
    fun `Alice can ask bank 1 for token creation`() {
        setUpEnvironmentAndRunTest { driver, alice, bob, bank1, issuer, amlAuthority ->

        }
    }




}