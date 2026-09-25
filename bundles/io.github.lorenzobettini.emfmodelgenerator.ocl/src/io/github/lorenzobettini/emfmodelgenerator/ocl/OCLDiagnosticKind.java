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

/**
 * Classifies the outcome of post-generation OCL validation independently of
 * the configured constraint source.
 */
public enum OCLDiagnosticKind {
	/** The selected roots are OCL-valid with respect to the configured constraints. */
	VALID,

	/** One or more configured constraints were violated. */
	VALIDATION_FAILURE,

	/** Constraint setup, parsing, resolution, or evaluation failed. */
	EVALUATION_OR_SETUP_FAILURE
}
