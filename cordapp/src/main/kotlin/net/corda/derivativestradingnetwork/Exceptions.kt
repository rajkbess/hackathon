package net.corda.derivativestradingnetwork

import net.corda.core.identity.CordaX500Name

class InvalidMembershipMetadata(message : String) : RuntimeException(message)
class PartyForThisNameNotFound(name : String) : RuntimeException("Party of name $name not found on this business network")
class PartyForThisCordaX500NameNotFound(name : CordaX500Name) : RuntimeException("Party of name $name not found on this business network")
class AmbiguousPartyName(name : String) : RuntimeException("Found more than one party for name $name")