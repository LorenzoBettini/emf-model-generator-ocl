/*******************************************************************************
 * Copyright (c) 2026 Lorenzo Bettini and contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.github.lorenzobettini.emfmodelgenerator.ocl.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.junit.jupiter.api.Test;

import io.github.lorenzobettini.emfmodelgenerator.ocl.OCLDiagnosticKind;
import io.github.lorenzobettini.emfmodelgenerator.ocl.OCLValidationResult;

class OCLValidationResultTest {

	@Test
	void okDiagnosticIsValidWithDefaultThreshold() {
		var result = new OCLValidationResult(Diagnostic.OK_INSTANCE);

		assertTrue(result.isValid());
		assertEquals(Diagnostic.WARNING, result.rejectedSeverity());
		assertEquals(OCLDiagnosticKind.VALID, result.kind());
		assertSame(Diagnostic.OK_INSTANCE, result.diagnostic());
		assertEquals(List.of(Diagnostic.OK_INSTANCE), result.flattenedDiagnostics());
	}

	@Test
	void warningAndHigherSeveritiesAreRejectedByDefault() {
		for (int severity : List.of(Diagnostic.WARNING, Diagnostic.ERROR, Diagnostic.CANCEL)) {
			var result = new OCLValidationResult(diagnostic(severity, "rejected"));

			assertFalse(result.isValid());
			assertEquals(OCLDiagnosticKind.VALIDATION_FAILURE, result.kind());
		}
	}

	@Test
	void explicitThresholdIsApplied() {
		var acceptedWarning = new OCLValidationResult(
				diagnostic(Diagnostic.WARNING, "warning"), Diagnostic.ERROR);
		var rejectedInfo = new OCLValidationResult(
				diagnostic(Diagnostic.INFO, "info"), Diagnostic.INFO);
		var acceptedError = new OCLValidationResult(
				diagnostic(Diagnostic.ERROR, "error"), Diagnostic.CANCEL);

		assertTrue(acceptedWarning.isValid());
		assertFalse(rejectedInfo.isValid());
		assertTrue(acceptedError.isValid());
	}

	@Test
	void flatteningIsImmutableAndDepthFirst() {
		var root = diagnostic(Diagnostic.ERROR, "root");
		var first = diagnostic(Diagnostic.WARNING, "first");
		var grandchild = diagnostic(Diagnostic.ERROR, "grandchild");
		var second = diagnostic(Diagnostic.INFO, "second");
		first.add(grandchild);
		root.add(first);
		root.add(second);

		List<Diagnostic> flattened = new OCLValidationResult(root).flattenedDiagnostics();

		assertEquals(List.of(root, first, grandchild, second), flattened);
		assertThrows(UnsupportedOperationException.class,
				() -> flattened.add(Diagnostic.OK_INSTANCE));
	}

	@Test
	void explicitFailureKindDistinguishesEvaluationAndSetupFailures() {
		var diagnostic = diagnostic(Diagnostic.ERROR, "parser failed");
		var result = new OCLValidationResult(diagnostic, Diagnostic.WARNING,
				OCLDiagnosticKind.EVALUATION_OR_SETUP_FAILURE);

		assertFalse(result.isValid());
		assertEquals(OCLDiagnosticKind.EVALUATION_OR_SETUP_FAILURE, result.kind());
	}

	@Test
	void nullAndInconsistentArgumentsFailClearly() {
		assertEquals("diagnostic must not be null",
				assertThrows(NullPointerException.class,
						() -> new OCLValidationResult(null)).getMessage());
		assertEquals("kind must not be null",
				assertThrows(NullPointerException.class,
						() -> new OCLValidationResult(Diagnostic.OK_INSTANCE,
								Diagnostic.WARNING, null)).getMessage());
		assertThrows(IllegalArgumentException.class,
				() -> new OCLValidationResult(Diagnostic.OK_INSTANCE, Diagnostic.OK));
		assertThrows(IllegalArgumentException.class,
				() -> new OCLValidationResult(Diagnostic.OK_INSTANCE, Diagnostic.WARNING,
						OCLDiagnosticKind.VALIDATION_FAILURE));
		assertThrows(IllegalArgumentException.class,
				() -> new OCLValidationResult(diagnostic(Diagnostic.ERROR, "error"),
						Diagnostic.WARNING, OCLDiagnosticKind.VALID));
	}

	private static BasicDiagnostic diagnostic(int severity, String message) {
		return new BasicDiagnostic(severity, "test", 0, message, null);
	}
}
