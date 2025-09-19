package com.mycompany.app;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour marquer les méthodes qui doivent recevoir des messages
 * du bus distribué. Inspirée de Google Guava EventBus.
 *
 * Utilisation :
 * <pre>
 * @Subscribe
 * public void onMessage(MonMessage message) {
 *     // traitement du message
 * }
 * </pre>
 *
 * @author Middleware Team
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {
}