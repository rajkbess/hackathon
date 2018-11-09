package net.corda

import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.flows.FlowException
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.derivativestradingnetwork.TokenTransferFlow
import net.corda.derivativestradingnetwork.flow.UserIssuanceRequestFlow
import net.corda.derivativestradingnetwork.states.MoneyToken
import net.corda.finance.EUR
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNodeParameters
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FlowTests {
    private val network = MockNetwork(listOf("net.corda"))

    private val userOne = network.createNode()
    private val userTwo = network.createNode()
    private val commercialBank = network.createNode()
    private val centralBankName = CordaX500Name("issuer", "Frankfurt", "DE")
    private val centralBank = network.createNode(MockNodeParameters().withLegalName(centralBankName))
    private val amlName = CordaX500Name("aml", "Frankfurt", "DE")
    private val aml = network.createNode(MockNodeParameters().withLegalName(amlName))

    private val userOneParty = userOne.info.singleIdentity()
    private val userTwoParty = userTwo.info.singleIdentity()
    private val commercialBankParty = commercialBank.info.singleIdentity()
    private val centralBankParty = centralBank.info.singleIdentity()
    private val amlParty = aml.info.singleIdentity()

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `cannot transfer over 1000`() {
        val flow = UserIssuanceRequestFlow(1100, EUR, commercialBankParty)
        val future = userOne.startFlow(flow)
        network.runNetwork()
        future.getOrThrow()

        userOne.transaction {
            val moneyStates = userOne.services.vaultService.queryBy<MoneyToken.State>().states
            assertEquals(0, moneyStates.size)
        }
    }

    @Test
    fun `issuance and transfer without AML`() {
        val flow = UserIssuanceRequestFlow(100, EUR, commercialBankParty)
        val future = userOne.startFlow(flow)
        network.runNetwork()
        future.getOrThrow()

        userOne.transaction {
            val moneyStates = userOne.services.vaultService.queryBy<MoneyToken.State>().states
            assertEquals(1, moneyStates.size)
            val moneyState = moneyStates.single().state.data
            assertEquals(moneyState.amount, 100)
            assertEquals(moneyState.currency, EUR)
            assertEquals(moneyState.holder, userOneParty)
            assertEquals(moneyState.issuer, centralBankParty)
            assertEquals(moneyState.amlAuthority, amlParty)
        }

        val flow2 = TokenTransferFlow(EUR, 100, userTwoParty)
        val future2 = userOne.startFlow(flow2)
        network.runNetwork()
        future2.getOrThrow()

        userOne.transaction {
            val moneyStates = userOne.services.vaultService.queryBy<MoneyToken.State>().states
            assertEquals(0, moneyStates.size)
        }

        userTwo.transaction {
            val moneyStates = userTwo.services.vaultService.queryBy<MoneyToken.State>().states
            assertEquals(1, moneyStates.size)
            val moneyState = moneyStates.single().state.data
            assertEquals(moneyState.amount, 100)
            assertEquals(moneyState.currency, EUR)
            assertEquals(moneyState.holder, userTwoParty)
            assertEquals(moneyState.issuer, centralBankParty)
            assertEquals(moneyState.amlAuthority, amlParty)
        }
    }

    @Test
    fun `issuance and transfer with AML`() {
        val flow = UserIssuanceRequestFlow(200, EUR, commercialBankParty)
        val future = userOne.startFlow(flow)
        network.runNetwork()
        future.getOrThrow()

        userOne.transaction {
            val moneyStates = userOne.services.vaultService.queryBy<MoneyToken.State>().states
            assertEquals(1, moneyStates.size)
            val moneyState = moneyStates.single().state.data
            assertEquals(moneyState.amount, 200)
            assertEquals(moneyState.currency, EUR)
            assertEquals(moneyState.holder, userOneParty)
            assertEquals(moneyState.issuer, centralBankParty)
            assertEquals(moneyState.amlAuthority, amlParty)
        }

        val flow2 = TokenTransferFlow(EUR, 200, userTwoParty)
        val future2 = userOne.startFlow(flow2)
        network.runNetwork()
        future2.getOrThrow()

        userOne.transaction {
            val moneyStates = userOne.services.vaultService.queryBy<MoneyToken.State>().states
            assertEquals(0, moneyStates.size)
        }

        userTwo.transaction {
            val moneyStates = userTwo.services.vaultService.queryBy<MoneyToken.State>().states
            assertEquals(1, moneyStates.size)
            val moneyState = moneyStates.single().state.data
            assertEquals(moneyState.amount, 200)
            assertEquals(moneyState.currency, EUR)
            assertEquals(moneyState.holder, userTwoParty)
            assertEquals(moneyState.issuer, centralBankParty)
            assertEquals(moneyState.amlAuthority, amlParty)
        }
    }
}