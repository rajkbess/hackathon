package net.corda.derivativestradingnetwork.integrationTests

import com.google.common.reflect.TypeToken
import net.corda.businessnetworks.membership.states.Membership
import net.corda.businessnetworks.membership.states.MembershipStatus
import net.corda.core.identity.Party
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverDSL
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BnoNode(driver : DriverDSL, testIdentity : TestIdentity, autoStart : Boolean = true) : BusinessNetworkNode(driver, testIdentity, autoStart) {

    fun approveMembershipForAllPending() {
        getMembershipStates().filter { it.status == MembershipStatus.PENDING }
                             .map { coreHandle.rpc.wellKnownPartyFromX500Name(it.member.name) }
                             .forEach { approveMembership(it!!) }
    }

    fun getMembershipStates() : List<Membership.State> {
        val response = getFromUrl("api/bnoApi/membershipStates")
        val desiredType = object : TypeToken<List<Membership.State>>() {}.type
        return getSuitableGson().fromJson<List<Membership.State>>(response.body().charStream(),desiredType)
    }


    fun approveMembership(forParty : Party) {
        val response = postPlainTextToUrl(forParty.name.toString(),"http://${webHandle.listenAddress}/api/bnoApi/activateMembership")
        assertEquals("OK", response.message())
        assertTrue(response.isSuccessful)
    }

}

