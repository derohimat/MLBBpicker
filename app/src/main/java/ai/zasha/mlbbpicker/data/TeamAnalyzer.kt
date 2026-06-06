package ai.zasha.mlbbpicker.data

/**
 * Analyzes team composition to detect imbalances and provide warnings.
 */
object TeamAnalyzer {

    private val CORE_ROLES = listOf("Tank", "Fighter", "Assassin", "Mage", "Marksman", "Support")

    fun analyze(allies: List<Hero?>): TeamAnalysis {
        val validAllies = allies.filterNotNull()
        if (validAllies.isEmpty()) return TeamAnalysis()

        // Role detection
        val hasTank = validAllies.any { it.hasRole("Tank") }
        val hasSupport = validAllies.any { it.hasRole("Support") }
        val hasMage = validAllies.any { it.hasRole("Mage") }
        val hasMarksman = validAllies.any { it.hasRole("Marksman") }
        val hasAssassin = validAllies.any { it.hasRole("Assassin") }
        val hasFighter = validAllies.any { it.hasRole("Fighter") }

        // Damage type balance
        val magicCount = validAllies.count { it.isMagicDamage }
        val physicalCount = validAllies.size - magicCount

        // CC count (heroes with Crowd Control speciality)
        val ccCount = validAllies.count { hero ->
            hero.specialityList.any {
                it.contains("Control", ignoreCase = true) ||
                it.contains("Crowd", ignoreCase = true) ||
                it.contains("Initiator", ignoreCase = true)
            }
        }

        // Count filled core roles
        val roleFlags = listOf(hasTank, hasFighter, hasAssassin, hasMage, hasMarksman, hasSupport)
        val filledRolesCount = roleFlags.count { it }

        // Generate warnings
        val warnings = mutableListOf<TeamWarning>()

        // Critical: No tank
        if (!hasTank && validAllies.size >= 3) {
            warnings.add(TeamWarning("No Tank! Team needs a frontliner", WarningSeverity.CRITICAL))
        }

        // Warning: No support/healer
        if (!hasSupport && validAllies.size >= 3) {
            warnings.add(TeamWarning("No Support — consider adding sustain", WarningSeverity.WARNING))
        }

        // Warning: No damage dealer
        if (!hasMarksman && !hasAssassin && !hasMage && validAllies.size >= 3) {
            warnings.add(TeamWarning("No carry hero! Need damage dealer", WarningSeverity.CRITICAL))
        }

        // Warning: All physical damage
        if (magicCount == 0 && validAllies.size >= 3) {
            warnings.add(TeamWarning("All Physical — enemy can stack armor", WarningSeverity.WARNING))
        }

        // Warning: All magic damage
        if (physicalCount == 0 && validAllies.size >= 3) {
            warnings.add(TeamWarning("All Magic — enemy can stack magic def", WarningSeverity.WARNING))
        }

        // Warning: Too many of same role
        for (role in CORE_ROLES) {
            val count = validAllies.count { it.hasRole(role) }
            if (count >= 3) {
                warnings.add(TeamWarning("Too many ${role}s ($count)", WarningSeverity.WARNING))
            }
        }

        // Info: No CC
        if (ccCount == 0 && validAllies.size >= 3) {
            warnings.add(TeamWarning("Low CC — hard to initiate fights", WarningSeverity.INFO))
        }

        // Info: Good composition
        if (hasTank && (hasSupport || hasMage) && (hasMarksman || hasAssassin) && warnings.isEmpty()) {
            warnings.add(TeamWarning("Balanced composition ✓", WarningSeverity.INFO))
        }

        // Calculate overall score (0-100)
        var score = 50 // Base
        if (hasTank) score += 12
        if (hasSupport) score += 8
        if (hasMarksman || hasAssassin) score += 10
        if (hasMage) score += 5
        if (hasFighter) score += 5
        if (magicCount > 0 && physicalCount > 0) score += 5 // Mixed damage bonus
        if (ccCount >= 2) score += 5

        // Penalties
        if (!hasTank && validAllies.size >= 3) score -= 15
        if (magicCount == 0 && validAllies.size >= 3) score -= 10
        if (physicalCount == 0 && validAllies.size >= 3) score -= 10

        score = score.coerceIn(0, 100)

        return TeamAnalysis(
            hasTank = hasTank,
            hasSupport = hasSupport,
            hasMage = hasMage,
            hasMarksman = hasMarksman,
            hasAssassin = hasAssassin,
            hasFighter = hasFighter,
            physicalCount = physicalCount,
            magicCount = magicCount,
            ccCount = ccCount,
            filledRolesCount = filledRolesCount,
            warnings = warnings,
            overallScore = score
        )
    }
}
