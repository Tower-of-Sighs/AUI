package com.sighs.apricityui.startup;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static method that runs once, at the very start of the client's loading phase.
 *
 * <p>The hook runs before the framework's own stack exists — no document, no layout, no
 * service layer beyond the client platform — so it is meant for the one thing that works that
 * early: a page drawn by the system browser. Everything it needs is on
 * {@link StartupContext}: the page to show, live client information, and the update channel of
 * a view that is already running by the time the game renders.</p>
 *
 * <pre>{@code
 * public final class MySplash {
 *     @StartupHook(priority = 100)
 *     public static void splash(StartupContext context) {
 *         context.showPage("""
 *                 <html><body style="background:#101418;color:#eef">
 *                 <h1>Loading…</h1></body></html>
 *                 """);
 *     }
 * }
 * }</pre>
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>The method must be {@code static} and take exactly one parameter that accepts a
 *       {@link StartupContext}. Anything else is reported and skipped.</li>
 *   <li>Hooks run in {@code priority} order, highest first; ties are broken by class and
 *       method name so the order is stable across launches.</li>
 *   <li>They are invoked on the client bootstrap thread, before the game window exists, so a
 *       hook should not block. Starting the browser happens right after the last hook
 *       returns, and the page configured here is the page it opens.</li>
 *   <li>An exception from a hook is logged and swallowed: a broken hook must not keep the game
 *       from starting.</li>
 *   <li>Only clients run hooks; a dedicated server never sees a {@code StartupContext}.</li>
 * </ul>
 *
 * <p>Discovery goes through the loader's annotation scanner, so hooks may live in any package
 * of any mod, not only in AUI's own.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface StartupHook {
    /**
     * Hooks with a higher value run first. Defaults to {@code 0}.
     */
    int priority() default 0;
}
