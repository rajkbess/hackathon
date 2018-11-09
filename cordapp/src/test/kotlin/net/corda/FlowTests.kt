package net.corda

import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.derivativestradingnetwork.flow.UserIssuanceRequestFlow
import net.corda.derivativestradingnetwork.states.MoneyToken
import net.corda.finance.DOLLARS
import net.corda.finance.EUR
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNodeParameters
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class FlowTests {
    private val network = MockNetwork(listOf("net.corda"))

    private val user = network.createNode()
    private val commercialBank = network.createNode()
    private val centralBankName = CordaX500Name("issuer", "Frankfurt", "DE")
    private val centralBank = network.createNode(MockNodeParameters().withLegalName(centralBankName))

    private val userParty = user.info.singleIdentity()
    private val commercialBankParty = commercialBank.info.singleIdentity()
    private val centralBankParty = centralBank.info.singleIdentity()

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `golden path test`() {
        val flow = UserIssuanceRequestFlow(100, EUR, commercialBankParty)
        val future = user.startFlow(flow)
        network.runNetwork()
        future.getOrThrow()
        network.waitQuiescent()

        user.transaction {
            val moneyStates = user.services.vaultService.queryBy<MoneyToken.State>().states
            assertEquals(1, moneyStates.size)
            val moneyState = moneyStates.single().state.data
            assertEquals(moneyState.amount, 100)
            assertEquals(moneyState.currency, EUR)
            assertEquals(moneyState.holder, userParty)
        }
    }
}