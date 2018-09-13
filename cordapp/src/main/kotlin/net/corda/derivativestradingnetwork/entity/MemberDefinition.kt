package net.corda.derivativestradingnetwork.entity

data class MemberAccountDefinition(val partyId : String, val legalEntityId : String, val type : String, val account : String, val name : String)