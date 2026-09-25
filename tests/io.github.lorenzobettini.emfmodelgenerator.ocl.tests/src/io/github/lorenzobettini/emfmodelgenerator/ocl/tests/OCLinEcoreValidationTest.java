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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.ocl.pivot.utilities.PivotConstants;
import org.eclipse.ocl.pivot.utilities.ThreadLocalExecutor;
import org.junit.jupiter.api.Test;

import io.github.lorenzobettini.emfmodelgenerator.ocl.OCLDiagnosticKind;
import io.github.lorenzobettini.emfmodelgenerator.ocl.OCLModelValidator;
import io.github.lorenzobettini.emfmodelgenerator.ocl.tests.fixture.EducationFixture;

class OCLinEcoreValidationTest {

	@Test
	void validSchoolPassesUsingRealEmbeddedOCL() {
		var fixture = new EducationFixture();
		var school = fixture.school("valid");
		fixture.student(school, "Alice", 20, "s1");
		fixture.student(school, "Bob", 18, "s2");

		try (var validator = OCLModelValidator.forOCLinEcore(fixture.resourceSet())) {
			var result = validator.validate(school);

			assertTrue(result.isValid(), () -> messages(result.diagnostic()));
			assertEquals(OCLDiagnosticKind.VALID, result.kind());
			assertEquals(Diagnostic.OK, result.diagnostic().getSeverity());
		}
		assertNoModelSpecificGlobalRegistration(fixture);
	}

	@Test
	void underageStudentIsRejectedAtTheDefaultThreshold() {
		var fixture = new EducationFixture();
		var school = fixture.school("underage");
		fixture.student(school, "Alice", 17, "s1");

		try (var validator = OCLModelValidator.forOCLinEcore(fixture.resourceSet())) {
			var result = validator.validate(school);

			assertFalse(result.isValid());
			assertTrue(result.diagnostic().getSeverity() >= Diagnostic.WARNING);
			assertEquals(Diagnostic.WARNING, result.rejectedSeverity());
			assertContains(result.diagnostic(), "Adult");
		}
	}

	@Test
	void blankNameFails() {
		var fixture = new EducationFixture();
		var school = fixture.school("blank-name");
		fixture.student(school, "   ", 20, "s1");

		try (var validator = OCLModelValidator.forOCLinEcore(fixture.resourceSet())) {
			var result = validator.validate(school);

			assertFalse(result.isValid());
			assertContains(result.diagnostic(), "NonBlankName");
		}
	}

	@Test
	void duplicateIdentifiersFail() {
		var fixture = new EducationFixture();
		var school = fixture.school("duplicates");
		fixture.student(school, "Alice", 20, "same");
		fixture.student(school, "Bob", 21, "same");

		try (var validator = OCLModelValidator.forOCLinEcore(fixture.resourceSet())) {
			var result = validator.validate(school);

			assertFalse(result.isValid());
			assertContains(result.diagnostic(), "UniqueStudentIdentifiers");
		}
	}

	@Test
	void multipleViolationsRemainInTheDiagnosticTree() {
		var fixture = new EducationFixture();
		var school = fixture.school("multiple-violations");
		fixture.student(school, "", 17, "same");
		fixture.student(school, "Bob", 16, "same");

		try (var validator = OCLModelValidator.forOCLinEcore(fixture.resourceSet())) {
			var result = validator.validate(school);

			assertContains(result.diagnostic(), "Adult");
			assertContains(result.diagnostic(), "NonBlankName");
			assertContains(result.diagnostic(), "UniqueStudentIdentifiers");
			assertTrue(result.flattenedDiagnostics().size() >= 4,
					() -> messages(result.diagnostic()));
		}
	}

	@Test
	void everyExplicitRootIsValidatedAndAggregated() {
		var fixture = new EducationFixture();
		var valid = fixture.school("first-root");
		fixture.student(valid, "Alice", 20, "s1");
		var invalid = fixture.school("second-root");
		fixture.student(invalid, "Bob", 17, "s2");

		try (var validator = OCLModelValidator.forOCLinEcore(fixture.resourceSet())) {
			var result = validator.validateAll(List.of(valid, invalid));

			assertFalse(result.isValid());
			assertEquals(2, result.diagnostic().getChildren().size());
			assertContains(result.diagnostic(), "Adult");
		}
	}

	@Test
	void savedAndReloadedSchoolRemainsValid() throws Exception {
		var fixture = new EducationFixture();
		var school = fixture.school("round-trip");
		fixture.student(school, "Alice", 20, "s1");
		var resource = school.eResource();
		var serialized = new ByteArrayOutputStream();
		resource.save(serialized, null);
		resource.unload();
		resource.load(new ByteArrayInputStream(serialized.toByteArray()), null);
		var reloadedSchool = resource.getContents().getFirst();

		try (var validator = OCLModelValidator.forOCLinEcore(fixture.resourceSet())) {
			assertTrue(validator.validate(reloadedSchool).isValid());
		}
	}

	@Test
	void malformedEmbeddedExpressionIsAnEvaluationOrSetupFailure() {
		var fixture = new EducationFixture();
		fixture.studentConstraint("Adult", "self.age >=");
		var school = fixture.school("malformed");
		fixture.student(school, "Alice", 20, "s1");

		try (var validator = OCLModelValidator.forOCLinEcore(fixture.resourceSet())) {
			var result = validator.validate(school);

			assertFalse(result.isValid());
			assertEquals(OCLDiagnosticKind.EVALUATION_OR_SETUP_FAILURE, result.kind(),
					() -> messages(result.diagnostic()));
		}
	}

	@Test
	void separateResourceSetsAndValidatorLifecyclesRemainIsolated() {
		var firstFixture = new EducationFixture();
		var firstSchool = firstFixture.school("first");
		firstFixture.student(firstSchool, "Alice", 17, "s1");
		var secondFixture = new EducationFixture();
		var secondSchool = secondFixture.school("second");
		secondFixture.student(secondSchool, "Bob", 20, "s2");
		var firstFactories = firstFixture.resourceSet().getResourceFactoryRegistry()
				.getExtensionToFactoryMap();
		Object previousEssentialOCLFactory = firstFactories.get("xmi");
		firstFactories.put("essentialocl", previousEssentialOCLFactory);
		try (var ignored = OCLModelValidator.forOCLinEcore(firstFixture.resourceSet())) {
			// Establish the OCL framework baseline before checking session-local lifecycle effects.
		}
		Object globalDelegate = EValidator.ValidationDelegate.Registry.INSTANCE
				.get(PivotConstants.OCL_DELEGATE_URI_PIVOT);
		int firstInitialAdapters = firstFixture.resourceSet().eAdapters().size();
		int secondInitialAdapters = secondFixture.resourceSet().eAdapters().size();

		var firstValidator = OCLModelValidator.forOCLinEcore(firstFixture.resourceSet());
		var secondValidator = OCLModelValidator.forOCLinEcore(secondFixture.resourceSet());
		assertNull(ThreadLocalExecutor.basicGetEnvironmentFactory());
		assertFalse(firstValidator.validate(firstSchool).isValid());
		firstValidator.close();
		assertTrue(secondValidator.validate(secondSchool).isValid());
		assertThrows(IllegalStateException.class, () -> firstValidator.validate(firstSchool));
		secondValidator.close();
		secondValidator.close();

		assertEquals(firstInitialAdapters, firstFixture.resourceSet().eAdapters().size());
		assertEquals(secondInitialAdapters, secondFixture.resourceSet().eAdapters().size());
		assertSame(globalDelegate, EValidator.ValidationDelegate.Registry.INSTANCE
				.get(PivotConstants.OCL_DELEGATE_URI_PIVOT));
		assertSame(previousEssentialOCLFactory, firstFactories.get("essentialocl"));
		assertNoModelSpecificGlobalRegistration(firstFixture);
		assertNoModelSpecificGlobalRegistration(secondFixture);
	}

	@Test
	void factoryAndMultipleRootArgumentsFailClearly() {
		assertEquals("resourceSet must not be null",
				assertThrows(NullPointerException.class,
						() -> OCLModelValidator.forOCLinEcore(null)).getMessage());
		var fixture = new EducationFixture();
		try (var validator = OCLModelValidator.forOCLinEcore(fixture.resourceSet())) {
			assertEquals("root must not be null",
					assertThrows(NullPointerException.class,
							() -> validator.validate(null)).getMessage());
			assertEquals("roots must not be null",
					assertThrows(NullPointerException.class,
							() -> validator.validateAll(null)).getMessage());
			assertEquals("roots must not be empty",
					assertThrows(IllegalArgumentException.class,
							() -> validator.validateAll(List.of())).getMessage());
			assertEquals("roots must not contain null",
					assertThrows(NullPointerException.class,
							() -> validator.validateAll(java.util.Collections.singletonList(null)))
								.getMessage());
		}
	}

	private static void assertNoModelSpecificGlobalRegistration(EducationFixture fixture) {
		assertNull(EValidator.Registry.INSTANCE.get(fixture.ePackage()));
	}

	private static void assertContains(Diagnostic diagnostic, String text) {
		assertTrue(messages(diagnostic).contains(text),
				() -> "Expected diagnostic to contain " + text + ":\n" + messages(diagnostic));
	}

	private static String messages(Diagnostic diagnostic) {
		var result = new StringBuilder(diagnostic.getMessage());
		for (Diagnostic child : diagnostic.getChildren()) {
			result.append('\n').append(messages(child));
		}
		return result.toString();
	}

}
