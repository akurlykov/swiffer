/**
 *
 */
package com.solambda.swiffer.api;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation to put on a event handler parameter to receive the input of a
 * activity , a signal or a workflow.
 * <p>
 */
@Documented
@Retention(RUNTIME)
@Target(PARAMETER)
public @interface Input {

}
