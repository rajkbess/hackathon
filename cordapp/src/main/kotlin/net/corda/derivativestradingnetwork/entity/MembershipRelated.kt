package net.corda.derivativestradingnetwork.entity

import net.corda.businessnetworks.membership.states.MembershipMetadata

data class MemberAccountDefinition(val partyId : String, val legalEntityId : String, val type : String, val account : String, val name : String)
data class PartyNameAndMembershipMetadata(val party : String, val membershipMetadata : MembershipMetadata)