package com.codencode.learningq.util;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ResourceLock(Resources.LOCALE)
class EmailNormalizerTests {

    @Test
    void differentlyFormattedAccountIdentifiersNormalizeToTheSameValue() {
        String expected = "learner@example.com";

        assertEquals(expected, EmailNormalizer.normalize("  Learner@Example.com  "));
        assertEquals(expected, EmailNormalizer.normalize("\tLEARNER@EXAMPLE.COM\n"));
        assertEquals(expected, EmailNormalizer.normalize(expected));
    }

    @Test
    void dotsAndPlusSuffixesRemainPartOfTheAccountIdentifier() {
        String normalized = EmailNormalizer.normalize(" First.Last+Learning@Example.com ");

        assertEquals("first.last+learning@example.com", normalized);
        assertNotEquals(EmailNormalizer.normalize("FirstLast+Learning@Example.com"), normalized);
        assertNotEquals(EmailNormalizer.normalize("First.Last@Example.com"), normalized);
        assertNotEquals(EmailNormalizer.normalize("First.Last+Other@Example.com"), normalized);
    }

    @Test
    void normalizationIsIdempotent() {
        String normalized = EmailNormalizer.normalize(" Learner+Practice@Example.com ");

        assertEquals(normalized, EmailNormalizer.normalize(normalized));
    }

    @Test
    void nullEmailIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> EmailNormalizer.normalize(null));
    }

    @Test
    void emptyAndWhitespaceOnlyEmailsAreRejected() {
        for (String email : new String[] {"", "   ", "\t\r\n"}) {
            assertThrows(IllegalArgumentException.class, () -> EmailNormalizer.normalize(email));
        }
    }

    @Test
    void accountIdentifiersDoNotDependOnTheMachinesDefaultLocale() {
        Locale original = Locale.getDefault();
        Locale originalDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        Locale originalFormat = Locale.getDefault(Locale.Category.FORMAT);

        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertEquals("i.learner@example.com",
                    EmailNormalizer.normalize("I.LEARNER@EXAMPLE.COM"));
        } finally {
            Locale.setDefault(original);
            Locale.setDefault(Locale.Category.DISPLAY, originalDisplay);
            Locale.setDefault(Locale.Category.FORMAT, originalFormat);
        }
    }
}
