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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.List;

import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcoreFactory;
import org.junit.jupiter.api.Test;

import io.github.lorenzobettini.emfmodelgenerator.ocl.OCLModelValidator;
import io.github.lorenzobettini.emfmodelgenerator.ocl.OCLValidationException;
import io.github.lorenzobettini.emfmodelgenerator.ocl.OCLValidationResult;

class OCLModelValidatorTest {

	@Test
	void convenienceMethodsAcceptValidSingleAndMultipleRoots() {
		var validator = new StubValidator(new OCLValidationResult(Diagnostic.OK_INSTANCE));
		var first = EcoreFactory.eINSTANCE.createEObject();
		var roots = List.of(first, EcoreFactory.eINSTANCE.createEObject());

		assertDoesNotThrow(() -> validator.validateOrThrow(first));
		assertDoesNotThrow(() -> validator.validateAllOrThrow(roots));
		assertSame(first, validator.lastRoot);
		assertSame(roots, validator.lastRoots);
		assertDoesNotThrow(validator::close);
	}

	@Test
	void convenienceMethodsThrowAndRetainInvalidResult() {
		var result = new OCLValidationResult(
				new BasicDiagnostic(Diagnostic.WARNING, "test", 0, "constraint", null));
		try (var validator = new StubValidator(result)) {
			var root = EcoreFactory.eINSTANCE.createEObject();

			var singleFailure = assertThrows(OCLValidationException.class,
					() -> validator.validateOrThrow(root));
			var multipleFailure = assertThrows(OCLValidationException.class,
					() -> validator.validateAllOrThrow(List.of(root)));

			assertSame(result, singleFailure.getResult());
			assertSame(result, multipleFailure.getResult());
		}
	}

	@Test
	void convenienceMethodsRejectNullInputsAndNullImplementationResults() {
		try (var validValidator = new StubValidator(new OCLValidationResult(Diagnostic.OK_INSTANCE))) {
			assertEquals("root must not be null",
					assertThrows(NullPointerException.class,
							() -> validValidator.validateOrThrow(null)).getMessage());
			assertEquals("roots must not be null",
					assertThrows(NullPointerException.class,
							() -> validValidator.validateAllOrThrow(null)).getMessage());
		}
		try (var nullValidator = new StubValidator(null)) {
			assertEquals("validate(root) must not return null",
					assertThrows(NullPointerException.class,
							() -> nullValidator.validateOrThrow(
									EcoreFactory.eINSTANCE.createEObject())).getMessage());
			assertEquals("validateAll(roots) must not return null",
					assertThrows(NullPointerException.class,
							() -> nullValidator.validateAllOrThrow(
									List.of(EcoreFactory.eINSTANCE.createEObject()))).getMessage());
		}
	}

	private static final class StubValidator implements OCLModelValidator {

		private final OCLValidationResult result;
		private EObject lastRoot;
		private Collection<? extends EObject> lastRoots;

		private StubValidator(OCLValidationResult result) {
			this.result = result;
		}

		@Override
		public OCLValidationResult validate(EObject root) {
			lastRoot = root;
			return result;
		}

		@Override
		public OCLValidationResult validateAll(Collection<? extends EObject> roots) {
			lastRoots = roots;
			return result;
		}

		@Override
		public void close() {
			// Nothing to release in the contract test.
		}
	}
}
