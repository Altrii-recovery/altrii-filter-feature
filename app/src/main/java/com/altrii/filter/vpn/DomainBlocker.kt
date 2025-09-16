package com.altrii.filter.vpn

import com.altrii.filter.data.FilterPreferences
import java.util.Locale

class DomainBlocker(private val preferences: FilterPreferences) {

    fun evaluate(domain: String?): BlockCategory? {
        if (domain.isNullOrBlank()) return null
        val normalized = domain.trimEnd('.').lowercase(Locale.ROOT)
        if (preferences.blockSocialMedia && normalized.matchesAny(socialMediaDomains)) {
            return BlockCategory.SOCIAL
        }
        if (preferences.blockYoutube && normalized.matchesAny(youtubeDomains)) {
            return BlockCategory.YOUTUBE
        }
        if (preferences.blockGambling && normalized.matchesAny(gamblingDomains)) {
            return BlockCategory.GAMBLING
        }
        return null
    }

    private fun String.matchesAny(domains: Set<String>): Boolean {
        var current = this
        while (current.isNotEmpty()) {
            if (domains.contains(current)) return true
            val index = current.indexOf('.')
            if (index == -1) break
            current = current.substring(index + 1)
        }
        return false
    }

    private val socialMediaDomains = setOf(
        "facebook.com",
        "fb.com",
        "instagram.com",
        "twitter.com",
        "tiktok.com",
        "snapchat.com",
        "pinterest.com",
        "reddit.com",
        "linkedin.com"
    )

    private val youtubeDomains = setOf(
        "youtube.com",
        "ytimg.com",
        "googlevideo.com",
        "youtube-nocookie.com",
        "yt3.ggpht.com"
    )

    private val gamblingDomains = setOf(
        "bet365.com",
        "pokerstars.com",
        "fanduel.com",
        "draftkings.com",
        "williamhill.com",
        "betmgm.com",
        "888casino.com",
        "partycasino.com"
    )
}
