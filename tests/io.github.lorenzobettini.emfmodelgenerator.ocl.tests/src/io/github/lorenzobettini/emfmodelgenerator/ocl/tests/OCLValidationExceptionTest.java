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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.junit.jupiter.api.Test;

import io.github.lorenzobettini.emfmodelgenerator.ocl.OCLValidationException;
import io.github.lorenzobettini.emfmodelgenerator.ocl.OCLValidationResult;

class OCLValidationExceptionTest {

	@Test
	void retainsResultAndNamesFirstRejectedDiagnostic() {
		var root = new BasicDiagnostic(Diagnostic.ERROR, "test", 0, "validation", null);
		root.add(new BasicDiagnostic(Diagnostic.INFO, "test", 0, "context", null));
		root.add(new BasicDiagnostic(Diagnostic.WARNING, "test", 0, "AdultStudent", null));
		var result = new OCLValidationResult(root);

		var exception = new OCLValidationException(result);

		assertSame(result, exception.getResult());
		assertEquals("OCL validation failed: AdultStudent", exception.getMessage());
	}

	@Test
	void rejectsNullOrValidResults() {
		assertEquals("result must not be null",
				assertThrows(NullPointerException.class,
						() -> new OCLValidationException(null)).getMessage());
		assertEquals("result must represent a validation failure",
				assertThrows(IllegalArgumentException.class,
						() -> new OCLValidationException(
								new OCLValidationResult(Diagnostic.OK_INSTANCE))).getMessage());
	}
}
