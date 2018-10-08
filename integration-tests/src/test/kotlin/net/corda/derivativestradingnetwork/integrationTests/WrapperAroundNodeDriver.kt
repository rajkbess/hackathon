package net.corda.derivativestradingnetwork.integrationTests

import org.junit.Test

class WrapperAroundNodeDriver {


    @Test
    fun `Run Network`() {
        NodeDriver().runNetwork()
    }


}

