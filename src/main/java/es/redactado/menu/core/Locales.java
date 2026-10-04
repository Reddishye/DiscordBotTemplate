package es.redactado.menu.core;

import java.util.Locale;
import net.dv8tion.jda.api.interactions.DiscordLocale;

/**
 * Chooses which locale to answer an interaction in.
 *
 * <p>The user's own setting wins over the guild's, because a user who has chosen a
 * language is asking to be spoken to in it regardless of where they happen to be. The
 * guild is the fallback for members who have not chosen anything.
 *
 * <p>{@link DiscordLocale#UNKNOWN} is the interesting case rather than the exceptional
 * one: Discord reports it whenever a user or guild has no usable locale, which is the
 * normal state for a brand new account. Its tag is the literal string
 * {@code "unknown"}, and {@code Locale.forLanguageTag("unknown")} returns
 * {@link Locale#ROOT} with an empty language, so it is checked explicitly instead of
 * being converted and hoped about.
 *
 * <p>English is the final fallback because it is the default bundle and the only one
 * guaranteed to be complete.
 */
public final class Locales {

    private Locales() {}

    /**
     * Picks the locale to answer in.
     *
     * @param user the interacting user's Discord locale, may be null or
     *     {@link DiscordLocale#UNKNOWN}
     * @param guild the guild's Discord locale, may be null or unknown
     * @return the locale to answer in, never null
     */
    public static Locale resolve(DiscordLocale user, DiscordLocale guild) {
        if (known(user)) {
            return toLocale(user);
        }
        if (known(guild)) {
            return toLocale(guild);
        }
        return Locale.ENGLISH;
    }

    private static boolean known(DiscordLocale discordLocale) {
        return discordLocale != null && discordLocale != DiscordLocale.UNKNOWN;
    }

    private static Locale toLocale(DiscordLocale discordLocale) {
        Locale locale = Locale.forLanguageTag(discordLocale.getLocale());
        // A tag the JDK does not recognise still leaves us with a usable language, but
        // a completely unrecognised one would leave us with ROOT and no way to pick a
        // bundle, which is the English fallback by another route.
        return locale.getLanguage().isEmpty() ? Locale.ENGLISH : locale;
    }
}
