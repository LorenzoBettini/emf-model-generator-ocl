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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.emf.common.util.Diagnostic;

/**
 * Immutable result of validating complete generated roots against configured OCL constraints.
 *
 * @param diagnostic the original root EMF diagnostic, including its complete child tree
 * @param rejectedSeverity the lowest diagnostic severity that makes the result invalid
 * @param kind classification that distinguishes violations from evaluation/setup failures
 */
public record OCLValidationResult(
		Diagnostic diagnostic,
		int rejectedSeverity,
		OCLDiagnosticKind kind) {

	/** The default threshold, which rejects warnings and all higher severities. */
	public static final int DEFAULT_REJECTED_SEVERITY = Diagnostic.WARNING;

	/**
	 * Creates a result using severity to distinguish a valid result from a validation failure.
	 *
	 * @param diagnostic the original root EMF diagnostic
	 * @param rejectedSeverity the lowest rejected severity
	 */
	public OCLValidationResult(Diagnostic diagnostic, int rejectedSeverity) {
		this(diagnostic, rejectedSeverity,
				diagnostic != null && diagnostic.getSeverity() < rejectedSeverity
						? OCLDiagnosticKind.VALID
						: OCLDiagnosticKind.VALIDATION_FAILURE);
	}

	/**
	 * Creates a severity-classified result using {@link #DEFAULT_REJECTED_SEVERITY}.
	 *
	 * @param diagnostic the original root EMF diagnostic
	 */
	public OCLValidationResult(Diagnostic diagnostic) {
		this(diagnostic, DEFAULT_REJECTED_SEVERITY);
	}

	/** Validates the immutable result components. */
	public OCLValidationResult {
		Objects.requireNonNull(diagnostic, "diagnostic must not be null");
		Objects.requireNonNull(kind, "kind must not be null");
		if (!isSupportedThreshold(rejectedSeverity)) {
			throw new IllegalArgumentException("rejectedSeverity must be Diagnostic.INFO, WARNING, "
					+ "ERROR, or CANCEL");
		}
		boolean validSeverity = diagnostic.getSeverity() < rejectedSeverity;
		if ((kind == OCLDiagnosticKind.VALID) != validSeverity) {
			throw new IllegalArgumentException("kind must agree with the diagnostic severity");
		}
	}

	/**
	 * Returns whether validation succeeded at the configured severity threshold.
	 *
	 * @return {@code true} only when the diagnostic severity is below the rejection threshold
	 */
	public boolean isValid() {
		return kind == OCLDiagnosticKind.VALID;
	}

	/**
	 * Returns an immutable depth-first view containing the root diagnostic and all descendants.
	 *
	 * @return immutable diagnostics in deterministic traversal order
	 */
	public List<Diagnostic> flattenedDiagnostics() {
		var flattened = new ArrayList<Diagnostic>();
		flatten(diagnostic, flattened);
		return List.copyOf(flattened);
	}

	private static void flatten(Diagnostic current, List<Diagnostic> target) {
		target.add(current);
		for (Diagnostic child : current.getChildren()) {
			flatten(child, target);
		}
	}

	private static boolean isSupportedThreshold(int severity) {
		return severity == Diagnostic.INFO || severity == Diagnostic.WARNING
				|| severity == Diagnostic.ERROR || severity == Diagnostic.CANCEL;
	}
}
