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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ExtensibleURIConverterImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.ecore.util.EObjectValidator;
import org.eclipse.emf.ecore.util.EContentAdapter;
import org.eclipse.ocl.pivot.validation.ValidationRegistryAdapter;
import org.junit.jupiter.api.Test;

import io.github.lorenzobettini.emfmodelgenerator.EMFValidationKind;
import io.github.lorenzobettini.emfmodelgenerator.ocl.OCLModelValidator;
import io.github.lorenzobettini.emfmodelgenerator.ocl.tests.fixture.EducationFixture;

class CompleteOCLValidationTest {

	private static final String EXTERNAL_ECORE = "/model/completeocl/education.ecore";
	private static final String CONSTRAINTS = "/model/completeocl/constraints/education.ocl";

	@Test
	void validModelPassesWithRelativeMetamodelImport() {
		var fixture = fixture();
		var school = fixture.school("valid-external");
		fixture.student(school, "Alice", 20, "s1");

		try (var validator = OCLModelValidator.forCompleteOCL(
				fixture.resourceSet(), uri(CONSTRAINTS))) {
			var result = validator.validate(school);

			assertTrue(result.isValid(), () -> messages(result.diagnostic()));
			assertEquals(EMFValidationKind.VALID, result.kind());
		}
	}

	@Test
	void constraintViolationIsReportedWithoutChangingTheEcoreModel() {
		var fixture = fixture();
		var studentClass = (EClass) fixture.ePackage().getEClassifier("Student");
		var school = fixture.school("invalid-external");
		fixture.student(school, "Alice", 17, "s1");
		assertTrue(fixture.ePackage().getEAnnotations().isEmpty());
		assertTrue(studentClass.getEAnnotations().isEmpty());

		try (var validator = OCLModelValidator.forCompleteOCL(
				fixture.resourceSet(), uri(CONSTRAINTS))) {
			var result = validator.validate(school);

			assertFalse(result.isValid());
			assertEquals(EMFValidationKind.VALIDATION_FAILURE, result.kind());
			assertContains(result.diagnostic(), "Adult");
		}
		assertTrue(fixture.ePackage().getEAnnotations().isEmpty());
		assertTrue(studentClass.getEAnnotations().isEmpty());
	}

	@Test
	void syntaxErrorReturnsAUsefulSetupDiagnostic() {
		var fixture = fixture();
		var school = fixture.school("syntax-error");
		fixture.student(school, "Alice", 20, "s1");

		try (var validator = OCLModelValidator.forCompleteOCL(
				fixture.resourceSet(), uri("/model/completeocl/constraints/malformed.ocl"))) {
			var result = validator.validateAll(List.of());

			assertFalse(result.isValid());
			assertEquals(EMFValidationKind.VALIDATOR_FAILURE, result.kind());
			assertContains(result.diagnostic(), "malformed.ocl");
			assertContains(result.diagnostic(), "5:3");
		}
	}

	@Test
	void unresolvedImportedMetamodelReturnsAUsefulSetupDiagnostic() {
		var fixture = fixture();
		var school = fixture.school("unresolved-import");

		try (var validator = OCLModelValidator.forCompleteOCL(
				fixture.resourceSet(), uri("/model/completeocl/constraints/unresolved.ocl"))) {
			var result = validator.validate(school);

			assertFalse(result.isValid());
			assertEquals(EMFValidationKind.VALIDATOR_FAILURE, result.kind());
			assertContains(result.diagnostic(), "missing.ecore");
		}
	}

	@Test
	void documentWithoutAnImportedPackageIsASetupFailure() {
		var fixture = fixture();

		try (var validator = OCLModelValidator.forCompleteOCL(
				fixture.resourceSet(), uri("/model/completeocl/constraints/empty.ocl"))) {
			var result = validator.validateAll(List.of());

			assertEquals(EMFValidationKind.VALIDATOR_FAILURE, result.kind());
			assertContains(result.diagnostic(), "does not complement an imported EPackage");
		}
	}

	@Test
	void resourceLoadingExceptionIsASetupFailure() {
		var fixture = fixture();
		var brokenURI = URI.createURI("broken:/constraints.ocl");
		fixture.resourceSet().setURIConverter(new ExtensibleURIConverterImpl() {
			@Override
			public InputStream createInputStream(URI uri, Map<?, ?> options) throws IOException {
				if (brokenURI.equals(uri)) {
					throw new IllegalStateException("broken input");
				}
				return super.createInputStream(uri, options);
			}
		});

		try (var validator = OCLModelValidator.forCompleteOCL(
				fixture.resourceSet(), brokenURI)) {
			var result = validator.validateAll(List.of());

			assertEquals(EMFValidationKind.VALIDATOR_FAILURE, result.kind());
			assertContains(result.diagnostic(), "broken input");
		}
	}

	@Test
	void loaderErrorCallbackReturnsFailure() throws Exception {
		var fixture = fixture();
		try (var validator = OCLModelValidator.forCompleteOCL(
				fixture.resourceSet(), uri(CONSTRAINTS))) {
			var loaderField = validator.getClass().getDeclaredField("loader");
			loaderField.setAccessible(true);
			Object loader = loaderField.get(validator);
			var error = loader.getClass().getDeclaredMethod("error", String.class, String.class);
			error.setAccessible(true);

			assertFalse((boolean) error.invoke(loader, "primary", "detail"));
		}
	}

	@Test
	void separateDocumentsInSeparateResourceSetsDoNotLeakConstraints() {
		var adultFixture = fixture();
		var adultSchool = adultFixture.school("adult-document");
		adultFixture.student(adultSchool, "Alice", 20, "s1");
		var minorFixture = fixture();
		var minorSchool = minorFixture.school("minor-document");
		minorFixture.student(minorSchool, "Bob", 17, "s2");

		var adultValidator = OCLModelValidator.forCompleteOCL(
				adultFixture.resourceSet(), uri(CONSTRAINTS));
		var minorValidator = OCLModelValidator.forCompleteOCL(
				minorFixture.resourceSet(), uri("/model/completeocl/constraints/minor.ocl"));
		assertTrue(adultValidator.validate(adultSchool).isValid());
		adultValidator.close();
		assertTrue(minorValidator.validate(minorSchool).isValid());
		assertThrows(IllegalStateException.class,
				() -> adultValidator.validate(adultSchool));
		minorValidator.close();

		assertNull(EValidator.Registry.INSTANCE.get(adultFixture.ePackage()));
		assertNull(EValidator.Registry.INSTANCE.get(minorFixture.ePackage()));
	}

	@Test
	void savedAndReloadedModelRemainsValidAgainstExternalConstraints() throws Exception {
		var fixture = fixture();
		var school = fixture.school("external-round-trip");
		fixture.student(school, "Alice", 20, "s1");
		var resource = school.eResource();
		var serialized = new ByteArrayOutputStream();
		resource.save(serialized, null);
		resource.unload();
		resource.load(new ByteArrayInputStream(serialized.toByteArray()), null);

		try (var validator = OCLModelValidator.forCompleteOCL(
				fixture.resourceSet(), uri(CONSTRAINTS))) {
			assertTrue(validator.validate(resource.getContents().getFirst()).isValid());
		}
	}

	@Test
	void closeUnloadsTemporaryResourcesAndRestoresTheResourceSet() {
		var fixture = fixture();
		var school = fixture.school("cleanup");
		fixture.student(school, "Alice", 20, "s1");
		int initialResources = fixture.resourceSet().getResources().size();
		int initialAdapters = fixture.resourceSet().eAdapters().size();

		var validator = OCLModelValidator.forCompleteOCL(
				fixture.resourceSet(), uri(CONSTRAINTS));
		var callerAdapter = new EContentAdapter();
		fixture.resourceSet().eAdapters().add(callerAdapter);
		assertTrue(fixture.resourceSet().getResources().size() > initialResources);
		assertTrue(validator.validate(school).isValid());
		var callerOwnedResource = fixture.school("created-after-validator").eResource();
		validator.close();
		validator.close();

		assertEquals(initialResources + 1, fixture.resourceSet().getResources().size());
		assertTrue(fixture.resourceSet().getResources().contains(callerOwnedResource));
		assertEquals(initialAdapters + 1, fixture.resourceSet().eAdapters().size());
		assertTrue(fixture.resourceSet().eAdapters().contains(callerAdapter));
	}

	@Test
	void closeRestoresAPreexistingLocalValidatorRegistration() {
		var fixture = fixture();
		var registry = ValidationRegistryAdapter.getAdapter(fixture.resourceSet());
		registry.put(fixture.ePackage(), EObjectValidator.INSTANCE);

		var validator = OCLModelValidator.forCompleteOCL(
				fixture.resourceSet(), uri(CONSTRAINTS));
		validator.close();

		assertSame(EObjectValidator.INSTANCE, registry.get(fixture.ePackage()));
	}

	@Test
	void closeRestoresAPreexistingLocalOCLResourceFactory() {
		var fixture = fixture();
		Resource.Factory previous = new XMIResourceFactoryImpl();
		fixture.resourceSet().getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put("ocl", previous);

		var validator = OCLModelValidator.forCompleteOCL(
				fixture.resourceSet(), uri(CONSTRAINTS));
		validator.close();

		assertSame(previous, fixture.resourceSet().getResourceFactoryRegistry()
				.getExtensionToFactoryMap().get("ocl"));
	}

	@Test
	void multipleRootsAndFactoryArgumentsFollowThePublicContract() {
		assertEquals("resourceSet must not be null",
				assertThrows(NullPointerException.class,
						() -> OCLModelValidator.forCompleteOCL(null, uri(CONSTRAINTS)))
							.getMessage());
		var fixture = fixture();
		assertEquals("oclURI must not be null",
				assertThrows(NullPointerException.class,
						() -> OCLModelValidator.forCompleteOCL(fixture.resourceSet(), null))
							.getMessage());
		var valid = fixture.school("first");
		fixture.student(valid, "Alice", 20, "s1");
		var invalid = fixture.school("second");
		fixture.student(invalid, "Bob", 17, "s2");
		try (var validator = OCLModelValidator.forCompleteOCL(
				fixture.resourceSet(), uri(CONSTRAINTS))) {
			var result = validator.validateAll(List.of(valid, invalid));

			assertFalse(result.isValid());
			assertEquals(2, result.diagnostic().getChildren().size());
			assertContains(result.diagnostic(), "Adult");
			assertTrue(validator.validateAll(List.of()).isValid());
			assertEquals("roots must not contain null",
					assertThrows(NullPointerException.class,
							() -> validator.validateAll(
									java.util.Collections.singletonList(null))).getMessage());
		}
	}

	private static EducationFixture fixture() {
		return new EducationFixture(EXTERNAL_ECORE);
	}

	private static URI uri(String path) {
		URL resource = CompleteOCLValidationTest.class.getResource(path);
		if (resource == null) {
			throw new IllegalStateException("Missing test resource " + path);
		}
		return URI.createURI(resource.toExternalForm());
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
