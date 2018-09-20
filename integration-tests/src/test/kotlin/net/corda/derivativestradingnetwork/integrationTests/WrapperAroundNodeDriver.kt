package net.corda.derivativestradingnetwork.integrationTests

import org.junit.Test

class WrapperAroundNodeDriver {


    @Test
    fun `Run Network`() {
        NodeDriver().runNetwork()
    }

    @Test
    fun `Run Network with use case 3`() {
        NodeDriverWithUseCase3().runNetwork()
    }

}

