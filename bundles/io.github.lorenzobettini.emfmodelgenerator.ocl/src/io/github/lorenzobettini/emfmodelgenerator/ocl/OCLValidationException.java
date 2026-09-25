/*******************************************************************************
 * Copyright (c) 2026 Lorenzo Bettini and contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.github.lorenzobettini.emfmodelgenerator.ocl;

import java.util.Objects;

import org.eclipse.emf.common.util.Diagnostic;

/** Thrown when post-generation OCL validation does not succeed. */
public final class OCLValidationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final transient OCLValidationResult result;

	/**
	 * Creates an exception retaining the complete validation result.
	 *
	 * @param result a non-valid validation result
	 */
	public OCLValidationException(OCLValidationResult result) {
		super(messageFor(result));
		this.result = result;
	}

	/**
	 * Returns the complete validation result, including its diagnostic tree.
	 *
	 * @return the validation result
	 */
	public OCLValidationResult getResult() {
		return result;
	}

	private static String messageFor(OCLValidationResult result) {
		Objects.requireNonNull(result, "result must not be null");
		if (result.isValid()) {
			throw new IllegalArgumentException("result must represent a validation failure");
		}
		// The root is usually an aggregate summary; prefer the first rejected child because it
		// normally identifies the violated invariant or evaluation/setup failure. Fall back to the
		// root below when the diagnostic has no rejected child.
		Diagnostic firstRejected = result.flattenedDiagnostics().stream()
				.skip(1)
				.filter(diagnostic -> diagnostic.getSeverity() >= result.rejectedSeverity())
				.findFirst()
				.orElse(result.diagnostic());
		return "OCL validation failed: " + firstRejected.getMessage();
	}
}
